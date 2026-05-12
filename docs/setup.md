# Setup guide

End-to-end setup of the Polymarket Telegram Mini App for development and production.

## 1. Register a Telegram bot and Mini App

1. Open [`@BotFather`](https://t.me/BotFather) in Telegram.
2. `/newbot` → choose a display name and a unique username ending with `bot`.
3. BotFather replies with an HTTP API token of the form `123456789:ABC...`. Save it as `BOT_TOKEN`. Treat it as a secret.
4. `/newapp` → pick your bot → fill in:
   - Title and short description
   - 640x360 icon and a 360x640 photo
   - **Web App URL**: the HTTPS URL the Mini App will be served from
       - dev: your tunnel URL (see step 2)
       - prod: `https://app.yourdomain.com`
5. Optional but recommended: `/setdomain` → set the bot domain to your prod hostname so deep links work; `/setmenubutton` → attach the Mini App to the chat menu.

Inside the bot chat the user opens the Mini App; on launch Telegram passes `initData` to the WebView containing the user object plus a signed `hash`. The backend validates `hash` with HMAC-SHA256 keyed on the bot token.

## 2. Local HTTPS tunnels for development

Telegram only opens **HTTPS** URLs. Quick tunnels forward `localhost` to the public internet so the Mini App and API are reachable from your phone.

### 2.1 Why you need **two** tunnels for the real Mini App

The WebView loads your SPA from tunnel **A** (e.g. `https://front-….trycloudflare.com`). The SPA must call your Spring Boot API over **HTTPS** at a **public** host — not `http://localhost:8080`.

| If you set | What breaks |
| --- | --- |
| `VITE_API_BASE_URL=http://localhost:8080` | On a **phone**, `localhost` is the phone itself — the API is never reached. |
| Same, from an **HTTPS** Mini App page | Browsers/WebViews often **block mixed content** (HTTPS page → `http://localhost`). |

Symptoms: **blank/empty app**, endless spinner, or **Authentication failed** / network errors — because `POST /api/auth/telegram` never succeeds.

**Fix:** run a **second** tunnel to port `8080` and set `VITE_API_BASE_URL` to that HTTPS origin.

### 2.2 Cloudflare quick tunnels (recommended)

Install once:

```bash
brew install cloudflared
```

Run **three** things in separate terminals (order does not matter, but all must stay running):

| Step | Command | Purpose |
| --- | --- | --- |
| 1 | `cd frontend && pnpm dev` | Vite on `http://localhost:5173` |
| 2 | `cloudflared tunnel --url http://localhost:5173` | Public **frontend** URL → paste into BotFather as **Web App URL** |
| 3 | `cloudflared tunnel --url http://localhost:8080` | Public **API** URL → use as `VITE_API_BASE_URL` |

Each `cloudflared` run prints a unique `https://….trycloudflare.com` URL. **They are different** — do not reuse the frontend URL for API calls.

### 2.3 Configure `deploy/.env`

After you have both tunnel URLs:

```bash
# Public SPA origin (from tunnel → :5173) — used by Telegram only; must appear in CORS.
# Public API origin (from tunnel → :8080) — the browser on the phone calls this.
VITE_API_BASE_URL=https://YOUR-API-TUNNEL.trycloudflare.com

CORS_ALLOWED_ORIGINS=http://localhost:5173,https://YOUR-FRONTEND-TUNNEL.trycloudflare.com,https://web.telegram.org
```

- Restart **Vite** after changing `VITE_API_BASE_URL` (it is inlined at dev/build time).
- Restart the **backend** after changing `CORS_ALLOWED_ORIGINS`.

`vite.config.ts` already allows `.trycloudflare.com` as `server.allowedHosts` so Vite accepts the `Host` header from the tunnel.

### 2.4 BotFather

- **Web App URL** = the **frontend** tunnel only (`:5173`).
- Do **not** set it to the API tunnel.

### 2.5 When quick tunnel URLs change

Quick tunnels get a **new hostname** every time you restart `cloudflared`. Then you must:

1. Update **Web App URL** in BotFather (`/myapps`).
2. Update `VITE_API_BASE_URL` and `CORS_ALLOWED_ORIGINS` in `deploy/.env`.
3. Restart Vite and Spring Boot.

For a **stable** hostname, use a [named Cloudflare Tunnel](https://developers.cloudflare.com/cloudflare-one/connections/connect-apps/) (requires a Cloudflare account) or deploy frontend + API to real domains.

### 2.6 ngrok (alternative)

Run two agents (or one config with two endpoints), e.g.:

```bash
ngrok http 5173   # terminal A — frontend
ngrok http 8080   # terminal B — API
```

Use the `https` URL from **B** as `VITE_API_BASE_URL`, from **A** as BotFather Web App URL, and add **A**’s origin to `CORS_ALLOWED_ORIGINS`.

### 2.7 Troubleshooting: empty Mini App

1. Confirm in mobile Safari/Chrome: open the **API** tunnel URL + `/actuator/health` — expect `{"status":"UP",…}`.
2. Confirm `VITE_API_BASE_URL` is the **API** tunnel (`:8080`), not `localhost`, not the frontend URL.
3. Confirm `CORS_ALLOWED_ORIGINS` includes the **frontend** tunnel origin exactly (scheme + host, no trailing slash).
4. Keep `cloudflared`, Vite, and Spring Boot running on the machine that hosts the tunnels.

### 2.8 White screen on the frontend tunnel (HTML loads, page blank)

The Vite dev server uses a **WebSocket** for HMR. Through `trycloudflare.com`, the client used to connect to the wrong host/port, so the runtime never finishes loading → **white screen** (check the console: WebSocket / `@vite/client` errors).

**Fix:** in `frontend/.env.local` set the public hostname of the **frontend** tunnel (no `https://`):

```bash
DEV_TUNNEL_HOST=bridges-whatever.trycloudflare.com
```

Restart `pnpm dev`. When the frontend tunnel hostname changes, update `DEV_TUNNEL_HOST` too.

Remove or comment out `DEV_TUNNEL_HOST` when you only open `http://localhost:5173` locally (so default HMR works again).

## 3. Backend dev run

```bash
cp deploy/.env.example deploy/.env
# fill BOT_TOKEN and JWT_SECRET (>=32 chars)

docker compose -f deploy/docker-compose.yml up -d postgres redis

cd backend
./gradlew bootRun
```

Endpoints:
- `GET  /actuator/health`
- `POST /api/auth/telegram`  body: `{"initData": "<raw initData string>"}`
- `GET  /api/markets?page=0&size=20`
- `GET  /api/markets/{conditionId}`
- `GET  /api/markets/{conditionId}/orderbook`
- `GET  /api/markets/{conditionId}/history?interval=1d`
- `WS   /ws`  STOMP, subscribe `/topic/market/{conditionId}`
- OpenAPI UI: `http://localhost:8080/swagger-ui.html`

## 4. Frontend dev run

```bash
cd frontend
pnpm install
pnpm dev
```

Open the tunnel HTTPS URL from inside the Telegram bot chat. The `initData` injected by Telegram is sent to `/api/auth/telegram` on first render; the returned JWT is held in memory only.

## 5. Production deploy

| Component | Recommended | Alternative |
| --- | --- | --- |
| Frontend  | Cloudflare Pages | Vercel, Netlify |
| Backend   | Fly.io           | Railway, Hetzner VPS + Caddy |
| Postgres  | Neon             | Supabase, Railway, RDS |
| Redis     | Upstash          | self-hosted on the same VPS |
| Logs      | Loki / Grafana Cloud | CloudWatch, Datadog |
| Errors    | Sentry           | Bugsnag |

### Backend (Fly.io)

```bash
cd backend
fly launch --no-deploy
fly secrets set \
  BOT_TOKEN=... \
  JWT_SECRET=... \
  POSTGRES_URL=jdbc:postgresql://... \
  POSTGRES_USER=... \
  POSTGRES_PASSWORD=... \
  REDIS_URL=redis://... \
  CORS_ALLOWED_ORIGINS=https://app.yourdomain.com,https://web.telegram.org
fly deploy
```

### Frontend (Cloudflare Pages)

- Build command: `pnpm install && pnpm build`
- Build output: `dist`
- Environment: `VITE_API_BASE_URL=https://api.yourdomain.com`

Then in BotFather `/myapps` → edit Web App URL to `https://app.yourdomain.com`.

## 6. Secrets checklist

| Variable | Where | Notes |
| --- | --- | --- |
| `BOT_TOKEN` | backend | from BotFather; rotate via `/revoke` |
| `JWT_SECRET` | backend | >= 32 random bytes |
| `POSTGRES_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | backend | managed Postgres |
| `REDIS_URL` | backend | `rediss://` for TLS |
| `CORS_ALLOWED_ORIGINS` | backend | include `https://web.telegram.org` |
| `POLYGON_RPC_URL` | backend | Phase 2 (Alchemy/Infura) |
| `PRIVY_APP_ID`, `PRIVY_APP_SECRET` | backend & frontend | Phase 2 |
| `VITE_API_BASE_URL` | frontend build | your API origin |

## 7. Validate Mini App locally

Without Telegram, mock `WebApp.initData` by running the frontend in `dev` mode — `src/lib/telegram/webApp.ts` falls back to a development stub that calls `/api/auth/telegram` with a `mock-` prefixed payload that the backend accepts only when `BOT_TOKEN` is empty.
