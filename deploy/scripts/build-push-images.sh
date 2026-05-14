#!/usr/bin/env bash
# Build app images locally and push to a container registry.
# Usage (from repo root):
#   ./deploy/scripts/build-push-images.sh YOUR_DOCKERHUB_USER 2025-05-14
#
# Optional: export VITE_* / NODE_MEMORY_MB before running (or source deploy/.env).
set -euo pipefail

PREFIX="${1:?Docker Hub user or registry prefix, e.g. janedoe or ghcr.io/janedoe}"
TAG="${2:-latest}"

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

# Allow deploy/.env to supply build args (optional)
if [[ -f deploy/.env ]]; then
  set -a
  # shellcheck disable=SC1091
  source deploy/.env
  set +a
fi

BACKEND_TAG="${PREFIX}/polymarket-tma-backend:${TAG}"
FRONTEND_TAG="${PREFIX}/polymarket-tma-frontend:${TAG}"

echo "=== Building backend -> ${BACKEND_TAG}"
docker build -f deploy/Dockerfile.backend -t "${BACKEND_TAG}" ./backend

echo "=== Building frontend -> ${FRONTEND_TAG}"
docker build -f deploy/Dockerfile.frontend \
  --build-arg "VITE_API_BASE_URL=${VITE_API_BASE_URL:-}" \
  --build-arg "VITE_PRIVY_APP_ID=${VITE_PRIVY_APP_ID:-}" \
  --build-arg "VITE_POLYGON_RPC_URL=${VITE_POLYGON_RPC_URL:-https://polygon-rpc.com}" \
  --build-arg "NODE_MEMORY_MB=${NODE_MEMORY_MB:-4096}" \
  -t "${FRONTEND_TAG}" \
  ./frontend

echo "=== Pushing"
docker push "${BACKEND_TAG}"
docker push "${FRONTEND_TAG}"

echo "Done. On the server, set in deploy/.env:"
echo "  BACKEND_IMAGE=${BACKEND_TAG}"
echo "  FRONTEND_IMAGE=${FRONTEND_TAG}"
echo "Then: docker compose -f docker-compose.prebuilt.yml --profile full pull && docker compose -f docker-compose.prebuilt.yml --profile full up -d"
