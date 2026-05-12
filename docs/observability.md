# Observability

## Metrics

Spring Boot Actuator + Micrometer exposes Prometheus metrics at `/actuator/prometheus`.

Key signals:

| Metric | Source | Why |
| --- | --- | --- |
| `http_server_requests_seconds_count` | Spring auto | Request rate per endpoint, status |
| `http_server_requests_seconds_bucket` | Spring auto | Latency histograms (p50/p95/p99) |
| `polymarket_upstream_errors_total` | `GammaClient`, `ClobClient`, `PositionsClient` | Polymarket API failures |
| `jvm_memory_used_bytes` | JVM | Heap pressure |
| `hikaricp_connections_active` | Hikari | DB pool exhaustion |
| `lettuce_command_completion_seconds` | Lettuce | Redis call latency |

## Scrape config (Grafana Cloud / self-hosted Prometheus)

```yaml
scrape_configs:
  - job_name: polymarket-tma-backend
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['polymarket-tma-backend.fly.dev:443']
    scheme: https
```

## Alerts (Prometheus rules)

```yaml
groups:
  - name: polymarket-tma
    rules:
      - alert: HighErrorRate
        expr: sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) > 1
        for: 10m
        annotations:
          summary: ">1 5xx/s sustained for 10m"
      - alert: HighLatencyP95
        expr: histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket[5m]))) > 1
        for: 10m
      - alert: UpstreamErrorsHigh
        expr: rate(polymarket_upstream_errors_total[5m]) > 0.5
        for: 5m
        annotations:
          summary: "Polymarket upstream errors above threshold"
```

## Logs

Use a structured JSON pattern in `logback-spring.xml` so Loki can index fields. Recommended fields:
`timestamp`, `level`, `traceId`, `userId`, `path`, `status`, `latency_ms`.

Push logs via Grafana Agent (`loki.write` config). On Fly.io the simplest setup is:

```bash
fly logs -a polymarket-tma-backend | promtail --client.url=https://loki.grafana.net/loki/api/v1/push
```

## Errors

- Backend: add Sentry Spring Boot SDK (`io.sentry:sentry-spring-boot-starter-jakarta`) and set `SENTRY_DSN`.
- Frontend: `@sentry/react` initialised in `main.tsx` with `VITE_SENTRY_DSN`.

## Dashboards

Use the Spring Boot dashboard (Grafana ID `12900`) and the JVM dashboard (`4701`). Add a custom panel
for `rate(polymarket_upstream_errors_total[5m])` by `upstream` label.
