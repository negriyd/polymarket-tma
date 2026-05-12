# MVP deploy checklist

This guide assumes a fresh GitHub repo connected to Fly.io and Cloudflare. Run through it once per environment.

## 0. Provision managed services

| Service  | Provider     | Notes |
| -------- | ------------ | ----- |
| Postgres | [Neon](https://neon.tech)        | free tier, US/EU region; copy `postgresql://...` connection string |
| Redis    | [Upstash](https://upstash.com)   | choose `rediss://` TLS URL; eviction `allkeys-lru` |
| Errors   | [Sentry](https://sentry.io)      | one project for backend, one for frontend |
| Logs     | Grafana Cloud (Loki)             | free tier covers MVP |

## 1. Backend on Fly.io

```bash
cd backend
fly launch --no-deploy --copy-config --name polymarket-tma-backend
fly secrets set \
  BOT_TOKEN=<from BotFather> \
  JWT_SECRET=$(openssl rand -base64 48) \
  POSTGRES_URL='jdbc:postgresql://<neon-host>/<db>?sslmode=require' \
  POSTGRES_USER=<neon-user> \
  POSTGRES_PASSWORD=<neon-password> \
  REDIS_URL='rediss://default:<password>@<upstash-host>:6379' \
  CORS_ALLOWED_ORIGINS='https://app.example.com,https://web.telegram.org' \
  APP_PROFILE=prod
fly deploy --remote-only
fly status   # verify health checks pass
```

## 2. Frontend on Cloudflare Pages

In the Cloudflare dashboard:

1. **Pages → Create project → Connect to Git**
2. Build command: `cd frontend && pnpm install && pnpm build`
3. Output directory: `frontend/dist`
4. Environment variables:
   - `VITE_API_BASE_URL=https://polymarket-tma-backend.fly.dev` (or your custom API domain)
5. After first deploy, attach a custom domain (e.g. `app.example.com`) and let Cloudflare provision TLS.

## 3. Wire up Telegram

In `@BotFather`:

```
/myapps   → pick app → Edit Web App URL → https://app.example.com
/setdomain → https://app.example.com
```

## 4. Smoke tests

```bash
curl https://polymarket-tma-backend.fly.dev/actuator/health
curl https://polymarket-tma-backend.fly.dev/api/markets?size=2
```

Then open the Mini App from the bot chat (or its `t.me/<bot>` deep link) and verify:

- the `/api/auth/telegram` request returns 200 with a JWT
- the home screen lists markets
- tapping a market opens the detail screen and the live stream box receives at least one message

## 5. Monitoring

- Fly: `fly logs -a polymarket-tma-backend`
- Prometheus scrape: `https://polymarket-tma-backend.fly.dev/actuator/prometheus`
- Add Grafana Cloud agent (see `docs/observability.md`) and create alerts:
  - `rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 1`
  - `histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m])) > 1` (s)
  - `polymarket_upstream_errors_total > 0` (custom metric, see `MetricsConfig`)

## 6. Rollback

```bash
fly releases -a polymarket-tma-backend
fly deploy --image registry.fly.io/polymarket-tma-backend:<previous>
```

For the frontend, redeploy a previous Cloudflare Pages deployment from the dashboard.
