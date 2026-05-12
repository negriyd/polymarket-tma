#!/usr/bin/env bash
# Reset Fly resources (optional), create Postgres + Upstash Redis, wire secrets, deploy API.
#
# Usage:
#   export BOT_TOKEN='...'
#   ./deploy/fly/bootstrap-backend.sh --destroy   # wipe backend, postgres app, web app, Fly Redis name
#   ./deploy/fly/bootstrap-backend.sh            # provision if missing, deploy
#
# If REDIS_URL is already set in the environment, Fly Redis is NOT created (external Redis / Upstash URL).
#
# Docs: deploy/fly/README.md

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
BACKEND_DIR="${REPO_ROOT}/backend"

: "${FLY_REGION:=fra}"
: "${BACKEND_APP:=polymarket-tma-backend}"
: "${POSTGRES_APP:=polymarket-tma-db}"
: "${WEB_APP:=polymarket-tma-web}"
: "${REDIS_NAME:=polymarket-tma-redis}"

# Populated before fly secrets set: either $REDIS_URL from env or Private URL from `fly redis status`.
RESOLVED_REDIS_URL="${REDIS_URL:-}"

DESTROY=false
for arg in "$@"; do
  case "$arg" in
    --destroy) DESTROY=true ;;
    -h|--help)
      echo "Usage: $0 [--destroy]"
      echo "Env:"
      echo "  BOT_TOKEN (required)"
      echo "  REDIS_URL — optional; if unset, creates Fly Upstash Redis (REDIS_NAME) and uses Private URL"
      echo "  JWT_SECRET, CORS_ALLOWED_ORIGINS, FLY_REGION, BACKEND_APP, POSTGRES_APP, WEB_APP, REDIS_NAME,"
      echo "  FLY_ORG (optional for fly apps/redis/postgres create --org)"
      exit 0
      ;;
  esac
done

log() { printf '%s\n' "[fly-bootstrap] $*"; }
die() { printf '%s\n' "[fly-bootstrap] ERROR: $*" >&2; exit 1; }

need_cmd() { command -v "$1" >/dev/null 2>&1 || die "missing command: $1"; }
need_cmd fly
need_cmd openssl

if [[ -z "${BOT_TOKEN:-}" ]]; then
  die "Set BOT_TOKEN (Telegram bot token from BotFather)."
fi

JWT_SECRET="${JWT_SECRET:-$(openssl rand -base64 48)}"
DEFAULT_CORS="https://${WEB_APP}.fly.dev,https://web.telegram.org,https://t.me"
CORS_ALLOWED_ORIGINS="${CORS_ALLOWED_ORIGINS:-$DEFAULT_CORS}"

app_exists() {
  fly apps show -a "$1" >/dev/null 2>&1
}

fly_redis_exists() {
  fly redis status "$1" >/dev/null 2>&1
}

# Parse "Private URL = redis://..." from `fly redis status <name>`
redis_private_url() {
  local name="$1"
  local line url
  line="$(fly redis status "$name" 2>/dev/null | grep -i 'Private URL' | head -1 || true)"
  [[ -n "$line" ]] || return 1
  url="${line#*=}"
  url="${url#"${url%%[![:space:]]*}"}" # trim leading spaces
  url="${url%"${url##*[![:space:]]}"}" # trim trailing spaces
  [[ -n "$url" ]] || return 1
  printf '%s' "$url"
}

destroy_stack() {
  log "Tearing down Fly resources (best effort)..."
  if app_exists "$BACKEND_APP"; then
    fly postgres detach "$POSTGRES_APP" -a "$BACKEND_APP" 2>/dev/null || true
  fi
  fly redis destroy "$REDIS_NAME" -y 2>/dev/null || true
  fly apps destroy "$BACKEND_APP" --yes 2>/dev/null || true
  fly apps destroy "$POSTGRES_APP" --yes 2>/dev/null || true
  fly apps destroy "$WEB_APP" --yes 2>/dev/null || true
  log "Destroy complete."
}

ensure_app() {
  local name="$1"
  if app_exists "$name"; then
    log "App already exists: $name"
    return 0
  fi
  log "Creating app: $name"
  # shellcheck disable=SC2086
  fly apps create "$name" ${FLY_ORG:+--org "$FLY_ORG"}
}

create_postgres_if_needed() {
  if app_exists "$POSTGRES_APP"; then
    log "Postgres cluster app already exists: $POSTGRES_APP (skip create)"
    return 0
  fi
  log "Creating Postgres cluster: $POSTGRES_APP (region=$FLY_REGION)"
  fly postgres create \
    --name "$POSTGRES_APP" \
    --region "$FLY_REGION" \
    --initial-cluster-size 1 \
    --vm-size shared-cpu-1x \
    --volume-size 10
}

attach_postgres() {
  log "Attaching $POSTGRES_APP → $BACKEND_APP (sets DATABASE_URL)"
  fly postgres attach "$POSTGRES_APP" -a "$BACKEND_APP" || log "attach failed or already attached — check: fly secrets list -a $BACKEND_APP"
}

ensure_fly_redis_and_url() {
  if [[ -n "${RESOLVED_REDIS_URL}" ]]; then
    log "Using REDIS_URL from environment (skip fly redis create)"
    return 0
  fi

  if fly_redis_exists "$REDIS_NAME"; then
    log "Fly Redis already exists: $REDIS_NAME — reading Private URL"
    RESOLVED_REDIS_URL="$(redis_private_url "$REDIS_NAME")" || die "Could not parse Private URL from: fly redis status $REDIS_NAME"
    return 0
  fi

  log "Creating Fly Upstash Redis: $REDIS_NAME (region=$FLY_REGION, eviction on, no replicas prompt)"
  # shellcheck disable=SC2086
  fly redis create \
    --name "$REDIS_NAME" \
    --region "$FLY_REGION" \
    --no-replicas \
    --enable-eviction \
    ${FLY_ORG:+--org "$FLY_ORG"}

  # Allow Upstash to register
  sleep 3
  RESOLVED_REDIS_URL="$(redis_private_url "$REDIS_NAME")" || die "Redis created but Private URL missing. Run: fly redis status $REDIS_NAME"
}

set_app_secrets() {
  [[ -n "${RESOLVED_REDIS_URL}" ]] || die "REDIS_URL empty — set REDIS_URL or ensure Fly Redis was created"

  local -a keys=(
    "BOT_TOKEN=$BOT_TOKEN"
    "JWT_SECRET=$JWT_SECRET"
    "CORS_ALLOWED_ORIGINS=$CORS_ALLOWED_ORIGINS"
    "APP_PROFILE=prod"
    "REDIS_URL=$RESOLVED_REDIS_URL"
  )
  log "Setting secrets on $BACKEND_APP"
  fly secrets set "${keys[@]}" -a "$BACKEND_APP"
}

deploy_backend() {
  log "Deploying backend from $BACKEND_DIR"
  (cd "$BACKEND_DIR" && fly deploy -a "$BACKEND_APP" --remote-only)
}

if [[ "$DESTROY" == true ]]; then
  destroy_stack
fi

ensure_app "$BACKEND_APP"
create_postgres_if_needed
attach_postgres
ensure_fly_redis_and_url
set_app_secrets
deploy_backend

log "Done. Smoke test:"
log "  curl -sS https://${BACKEND_APP}.fly.dev/actuator/health"
log "  curl -sS 'https://${BACKEND_APP}.fly.dev/api/markets?size=1'"
