# Fly.io — full stack (backend + frontend + Postgres + Redis)

Fly runs **two apps** from this repo (API and SPA). **Postgres** and **Redis** are **separate** Fly-managed resources (not lines inside `fly.toml`).

## Prerequisites

- [flyctl](https://fly.io/docs/hands-on/install-flyctl/) and `fly auth login`
- Pick a region (examples use `fra`; keep all resources in one region if possible).

## 1. Postgres (Fly Managed Postgres)

```bash
fly postgres create --name polymarket-tma-db --region fra --initial-cluster-size 1 --vm-size shared-cpu-1x --volume-size 3
```

Attach to the **backend** app (sets `DATABASE_URL` on that app):

```bash
fly postgres attach polymarket-tma-db -a polymarket-tma-backend
```

If the backend app does not exist yet:

```bash
cd backend && fly apps create polymarket-tma-backend
fly postgres attach polymarket-tma-db -a polymarket-tma-backend
```

The backend maps `DATABASE_URL` (`postgres://…`) to JDBC automatically (see `DatabaseUrlEnvironmentPostProcessor`).

## 2. Redis (Fly / Upstash)

```bash
fly redis create --name polymarket-tma-redis --region fra
```

Follow the CLI prompt to attach to `polymarket-tma-backend`, or set manually:

```bash
fly secrets set REDIS_URL='rediss://default:PASSWORD@HOST:6379' -a polymarket-tma-backend
```

(Use the URL shown after `fly redis create`.)

## 3. Backend app

From repo:

```bash
cd backend
fly launch --no-deploy --copy-config   # first time only, if fly.toml not applied
fly secrets set -a polymarket-tma-backend \
  BOT_TOKEN='<BotFather>' \
  JWT_SECRET="$(openssl rand -base64 48)" \
  CORS_ALLOWED_ORIGINS='https://polymarket-tma-web.fly.dev,https://web.telegram.org,https://t.me'
# DATABASE_URL is set by `fly postgres attach`; add REDIS_URL if not attached via redis create.
fly deploy -a polymarket-tma-backend --remote-only
```

Update **`CORS_ALLOWED_ORIGINS`** to your real frontend URL (custom domain or `*.fly.dev`).

## 4. Frontend app (static nginx)

`VITE_API_BASE_URL` is **baked at build time** — pass it on every deploy that should target a new API URL:

```bash
cd frontend
fly apps create polymarket-tma-web   # first time only
API="https://polymarket-tma-backend.fly.dev"
fly deploy -a polymarket-tma-web --remote-only \
  --build-arg VITE_API_BASE_URL="$API"
```

If you use a **custom API hostname**, substitute `$API`.

## 5. Telegram

In BotFather, set the Web App URL to your **frontend** URL, e.g. `https://polymarket-tma-web.fly.dev`.

## One-shot bootstrap (`bootstrap-backend.sh`)

From repo root: provisions **Postgres** (if missing), **Fly Upstash Redis** (if `REDIS_URL` is unset), sets secrets, deploys the API.

- **`BOT_TOKEN`** — required.
- **`REDIS_URL`** — optional. If set, no `fly redis create`; that URL is written to app secrets. If unset, the script creates Redis named **`REDIS_NAME`** (default `polymarket-tma-redis`) and uses the **Private URL** from `fly redis status`.
- **`FLY_ORG`** — optional; passed to `fly apps create` / `fly redis create` when set.
- **`--destroy`** — tears down backend app, Postgres app, web app, and the named Fly Redis (best effort).

```bash
chmod +x deploy/fly/bootstrap-backend.sh
export BOT_TOKEN='…'
# Omit REDIS_URL to let the script create Fly Redis; or set your own:
# export REDIS_URL='rediss://…'
./deploy/fly/bootstrap-backend.sh --destroy   # optional
./deploy/fly/bootstrap-backend.sh
```

The backend maps Fly’s `DATABASE_URL` to JDBC when `POSTGRES_URL` is unset.

## 6. Smoke tests

```bash
curl -sS "https://polymarket-tma-backend.fly.dev/actuator/health"
curl -sS "https://polymarket-tma-backend.fly.dev/api/markets?size=1"
curl -sS -I "https://polymarket-tma-web.fly.dev/"
```

## Costs / stop

- Scale API or web to zero: `fly scale count 0 -a <app>`
- Destroy DB (data loss): `fly apps destroy <postgres-app-name> --yes` — name from `fly postgres list`

## Files

| Path | Role |
|------|------|
| `backend/fly.toml` | Spring Boot API (`Dockerfile.backend`) |
| `frontend/fly.toml` | Vite `dist` + nginx (`Dockerfile.frontend`) |

There is **no** single `fly.toml` that launches Postgres and Redis: Fly provisions those with `fly postgres create` / `fly redis create`, then attaches secrets to your app.
