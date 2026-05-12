# Polymarket Telegram Mini App

Telegram Mini App for browsing (Phase 1) and trading (Phase 2) Polymarket prediction markets.

- **Frontend**: React 18 + Vite + TypeScript + `@twa-dev/sdk` + Tailwind + TanStack Query
- **Backend**: Java 21 + Spring Boot 3.3 (Web + WebFlux + Security + STOMP)
- **Storage**: PostgreSQL 16, Redis 7
- **Stream**: Polymarket CLOB WebSocket → STOMP fanout
- **Wallet (Phase 2)**: Privy embedded wallet on Polygon

## Layout

```
backend/   Spring Boot service (auth, market data, ws, trading)
frontend/  Vite + React SPA
deploy/    docker-compose, Dockerfiles
docs/      manual setup notes
```

## Quick start (dev)

Prerequisites: Docker, Docker Compose, Java 21 (Temurin), Node 20+, pnpm.

```bash
cp deploy/.env.example deploy/.env       # fill BOT_TOKEN and JWT_SECRET
docker compose -f deploy/docker-compose.yml up -d postgres redis

# backend
cd backend
./gradlew bootRun

# frontend (separate terminal)
cd frontend
pnpm install
pnpm dev

# expose to Telegram: TWO tunnels — SPA (5173) + API (8080). See docs/setup.md §2.
cloudflared tunnel --url http://localhost:5173   # Web App URL in BotFather
cloudflared tunnel --url http://localhost:8080   # set VITE_API_BASE_URL in deploy/.env to this https URL
```

`localhost:8080` as `VITE_API_BASE_URL` only works in a desktop browser on the same machine; **the real Mini App on a phone needs the API tunnel**. Full checklist: [docs/setup.md](docs/setup.md) (section 2).

## Configuration

See [docs/setup.md](docs/setup.md) for full setup instructions including BotFather, Cloudflare tunnel, and deploy targets.

## License

Proprietary. All rights reserved.
