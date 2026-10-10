# Внутренние ставки — включение end-to-end и admin resolve

**Статус на 24 мая 2026:** Фазы **0–3 завершены** (MVP готов к пробному запуску).  
**Следующий шаг:** Фаза 4 (prod RPC, мониторинг treasury, уведомления, метрики).

| Фаза | Что сделано |
|------|-------------|
| 0 | Решения: parimutuel, Gamma + grace 24ч, internal balance + withdraw |
| 1 | Backend: `/api/bets/*`, миграция `V2__internal_bets.sql` |
| 2 | Резолв job, payouts, withdraw, admin resolve/cancel |
| 3 | Frontend: bet flow, пул на карточке рынка, «Мои ставки» + withdraw в Wallet |

См. также:
- [internal-bets-plan_RU.md](./internal-bets-plan_RU.md) — полный план и архитектура
- [trading_RU.md](./trading_RU.md) — legacy CLOB (заморожен)
- [deploy/.env.example](../deploy/.env.example) — шаблон переменных окружения

---

## Что именно включается

При `BETS_ENABLED=true` платформа переключается на **основной путь — внутренний parimutuel**:

| Где | Что меняется |
|-----|--------------|
| **Backend** (`deploy/.env` → Spring Boot) | Принимает ставки, пишет в PostgreSQL, верифицирует USDC transfer, резолвит рынки, зачисляет выигрыш на internal balance, шлёт withdraw с treasury |
| **Frontend** (Telegram Mini App) | На карточке рынка — «Bet YES/NO», пул ставок; в Wallet — баланс выигрышей, «Мои ставки», withdraw. **CLOB auth и CTF approvals скрыты** |
| **Polygon** | Пользователь шлёт USDC на `BETS_TREASURY_ADDRESS`; treasury (hot wallet) шлёт USDC обратно при withdraw |
| **PostgreSQL** | Таблицы `platform_market`, `bet`, `payout`, `user_balance`, `withdrawal` (Flyway V2 + V3) |

CLOB (`/api/orders/*`) **не используется** для новых пользователей, но код остаётся в репозитории.

---

## Что нужно заранее

1. **Telegram Bot** — `BOT_TOKEN` (как и раньше).
2. **JWT** — `JWT_SECRET` (32+ символов).
3. **Privy** — `PRIVY_APP_ID`, `PRIVY_APP_SECRET`; на фронте при сборке образа — `VITE_PRIVY_APP_ID`.
4. **Polygon hot wallet (treasury)** — отдельный EOA с USDC для приёма ставок и выплат withdraw.
   - Адрес → `BETS_TREASURY_ADDRESS`
   - Приватный ключ → `BETS_TREASURY_PRIVATE_KEY` (нужен для on-chain withdraw)
5. **Polygon RPC** — рабочий URL (Alchemy/Infura). **Обязателен** для:
   - подтверждения ставок (`confirm` — проверка `Transfer` в receipt);
   - withdraw (отправка USDC с treasury).
6. **Admin secret** — длинная случайная строка для ручного resolve/cancel → `BETS_ADMIN_SECRET`.

> ⚠️ `BETS_TREASURY_PRIVATE_KEY` храните **только** в `deploy/.env` на сервере (или локальном `.env`), **никогда** в git.

---

## Часть A. Локальная разработка (пошагово)

### Шаг A1. Инфраструктура

**Где:** терминал, корень репозитория.

```bash
cd deploy
docker compose --profile full up -d postgres redis
```

**Что делает:** поднимает PostgreSQL (`localhost:5433`) и Redis (`localhost:6379`).  
Flyway при старте backend применит миграции `V1`, `V2`, `V3`.

### Шаг A2. Переменные backend (локально)

**Где:** экспорт в shell **или** файл `backend/.env` / IDE Run Configuration.

Минимальный набор для ставок:

```bash
export BOT_TOKEN="ваш_telegram_bot_token"
export JWT_SECRET="локальный-секрет-минимум-32-символа-длинный"
export PRIVY_APP_ID="cmp..."
export PRIVY_APP_SECRET="..."

export POSTGRES_URL="jdbc:postgresql://localhost:5433/polymarket_tma"
export POSTGRES_USER="polymarket"
export POSTGRES_PASSWORD="polymarket"
export REDIS_URL="redis://localhost:6379"

# Polygon — замените на свой Alchemy/Infura ключ
export POLYGON_RPC_URL="https://polygon-mainnet.g.alchemy.com/v2/YOUR_KEY"

# Внутренние ставки
export BETS_ENABLED=true
export BETS_TREASURY_ADDRESS="0xВашTreasuryАдресНаPolygon"
export BETS_TREASURY_PRIVATE_KEY="приватный_ключ_treasury_без_0x_или_с_0x"
export BETS_ADMIN_SECRET="длинный-случайный-секрет-для-admin-api"
export BETS_PLATFORM_FEE_BPS=500
export BETS_MIN_USDC=1
export BETS_MIN_WITHDRAW_USDC=1
```

**Что делает:** Spring Boot читает эти переменные из `backend/src/main/resources/application.yml` (маппинг `BETS_*` → `app.bets.*`).

### Шаг A3. Запуск backend

**Где:** каталог `backend/`.

```bash
cd backend
./gradlew bootRun
```

**Проверка:**

```bash
curl -s http://localhost:8080/api/bets/config | python3 -m json.tool
```

Ожидаемый ответ:

```json
{
  "enabled": true,
  "platformFeeBps": 500,
  "minBetUsdc": "1",
  "minWithdrawUsdc": "1",
  "lockBeforeEndMinutes": 15
}
```

Если `"enabled": false` — проверьте, что **оба** заданы: `BETS_ENABLED=true` и непустой `BETS_TREASURY_ADDRESS`.

### Шаг A4. Запуск frontend

**Где:** каталог `frontend/`.

```bash
cd frontend
export VITE_PRIVY_APP_ID="cmp..."   # тот же, что PRIVY_APP_ID
export VITE_POLYGON_RPC_URL="https://polygon-mainnet.g.alchemy.com/v2/YOUR_KEY"
export VITE_API_BASE_URL="http://localhost:8080"
pnpm dev
```

**Что делает:** SPA на `http://localhost:5173`, запросы к backend на `:8080`.

### Шаг A5. Проверка в UI (сквозной сценарий)

**Где:** браузер / Telegram Mini App.

1. Откройте приложение → вкладка **Wallet** → **Connect wallet** (Privy).
2. Убедитесь, что на Polygon-кошельке есть **USDC** (минимум `BETS_MIN_USDC`, по умолчанию 1 USDC).
3. Вкладка **Markets** → откройте бинарный рынок (YES/NO, не negRisk).
4. На карточке рынка должны быть кнопки **Bet YES / Bet NO** и блок **Betting pool**.
5. Нажмите **Bet YES** (или NO) → введите сумму → **Place bet**.
6. Privy отправит USDC на `BETS_TREASURY_ADDRESS` → backend подтвердит ставку.
7. Вкладка **Wallet** → секция **My bets** — статус `CONFIRMED`.

**Где применяется on-chain:** только transfer USDC пользователя → treasury. Ордеров CLOB нет.

---

## Часть B. Production / сервер (пошагово)

### Шаг B1. Подготовить treasury-кошелёк

**Где:** MetaMask / Privy / любой EOA на **Polygon mainnet (chain 137)**.

1. Создайте или выберите отдельный hot wallet (не пользовательский).
2. Пополните его **MATIC** (gas для withdraw) и держите запас **USDC** для выплат победителям.
3. Запишите **address** → пойдёт в `BETS_TREASURY_ADDRESS`.
4. Экспортируйте **private key** → пойдёт в `BETS_TREASURY_PRIVATE_KEY`.

**Где применяется:** этот адрес — получатель всех ставок; с этого же кошелька backend шлёт withdraw.

### Шаг B2. Настроить `deploy/.env` на сервере

**Где:** файл `deploy/.env` рядом с `docker-compose.prebuilt.yml` (см. [deploy/.env.example](../deploy/.env.example)).

Добавьте или раскомментируйте блок **Internal parimutuel betting**:

```bash
# --- уже должны быть ---
BOT_TOKEN=...
JWT_SECRET=...
PRIVY_APP_ID=...
PRIVY_APP_SECRET=...
CORS_ALLOWED_ORIGINS=https://web.telegram.org,https://ваш-домен...

# Polygon RPC — ОБЯЗАТЕЛЬНО рабочий (не публичный polygon-rpc.com в prod)
POLYGON_RPC_URL=https://polygon-mainnet.g.alchemy.com/v2/YOUR_KEY

# --- внутренние ставки (PRIMARY) ---
BETS_ENABLED=true
BETS_TREASURY_ADDRESS=0xВашTreasuryАдрес
BETS_TREASURY_PRIVATE_KEY=ваш_приватный_ключ
BETS_ADMIN_SECRET=длинный-случайный-секрет
BETS_PLATFORM_FEE_BPS=500
BETS_MIN_USDC=1
BETS_MIN_WITHDRAW_USDC=1
BETS_LOCK_MINUTES=15
BETS_GRACE_HOURS=24
BETS_WITHDRAW_COOLDOWN_MINUTES=5
```

**Что делает docker-compose:** `env_file: .env` передаёт переменные **в контейнер backend** при старте. Frontend-образ **не** читает `BETS_*` — он только показывает UI, если `GET /api/bets/config` вернёт `enabled: true`.

### Шаг B3. Пересобрать и задеployить backend

**Где:** CI / локальная машина с docker buildx.

Backend — runtime env (достаточно нового образа + перезапуск с обновлённым `.env`):

```bash
docker buildx build --platform linux/amd64 -f deploy/Dockerfile.backend \
  -t YOUR_REGISTRY/polymarket-tma-backend:TAG --push ./backend
```

**Где на сервере:**

```bash
cd deploy
docker compose -f docker-compose.prebuilt.yml --env-file .env --profile full pull
docker compose -f docker-compose.prebuilt.yml --env-file .env --profile full up -d --force-recreate backend
```

Flyway применит миграции автоматически при первом старте новой версии.

### Шаг B4. Frontend (если ещё не собран с Privy)

**Где:** build-arg при сборке образа (см. [deploy/local/start.txt](../deploy/local/start.txt)).

```bash
docker buildx build --platform linux/amd64 -f deploy/Dockerfile.frontend \
  --build-arg VITE_API_BASE_URL="" \
  --build-arg VITE_PRIVY_APP_ID="cmp..." \
  --build-arg VITE_POLYGON_RPC_URL="https://polygon-mainnet.g.alchemy.com/v2/YOUR_KEY" \
  -t YOUR_REGISTRY/polymarket-tma-frontend:TAG --push ./frontend
```

`VITE_API_BASE_URL=""` — SPA ходит на тот же origin (nginx проксирует `/api` на backend).

### Шаг B5. Проверка после деплоя

**Где:** SSH на сервер или с вашей машины к публичному URL.

```bash
# 1) Backend жив
curl -s https://ВАШ_ДОМЕН/actuator/health

# 2) Ставки включены
curl -s https://ВАШ_ДОМЕН/api/bets/config

# 3) Пул по conditionId (публичный endpoint)
curl -s "https://ВАШ_ДОМЕН/api/bets/pool/0xCONDITION_ID"
```

В Telegram Mini App: Markets → Bet → Wallet → My bets / Withdraw.

---

## Часть C. Жизненный цикл ставки (что происходит автоматически)

| Когда | Где (код / БД) | Действие |
|-------|----------------|----------|
| Пользователь ставит | `BetService.prepare/confirm` | `bet.status = CONFIRMED`, USDC на treasury |
| За 15 мин до `endDate` | `MarketResolutionJob` → `lockDueMarkets()` | `platform_market.status = LOCKED`, новые ставки отклоняются |
| Через 24 ч после `endDate` | `MarketResolutionJob` → Gamma | Если `closed=true` и цена победителя ≥ 0.99 → auto-resolve |
| После resolve | `PayoutCalculator` | Победители: `bet.status = WON`, `user_balance` += net; проигравшие: `LOST` |
| Withdraw | `WithdrawService` | Списание balance → USDC transfer treasury → Privy wallet |

Job запускается **каждые 10 минут** (`MarketResolutionJob`).

---

## Часть D. Admin resolve (ручной резолв)

Используйте, когда:
- Gamma ещё не закрыл рынок, а вы хотите зафиксировать исход вручную;
- auto-resolve не сработал (нет цены ≥ 0.99);
- нужно форсировать исход после спора.

### Шаг D1. Узнать `conditionId`

**Где взять:**
- URL рынка в UI / API: `GET /api/markets/{slugOrId}` → поле `conditionId`
- База: `SELECT condition_id, question, status FROM platform_market;`
- Список ставок: `SELECT DISTINCT condition_id FROM bet WHERE status = 'CONFIRMED';`

Пример: `0xabc123...` (66 символов с `0x`).

### Шаг D2. Убедиться, что admin secret задан

**Где:** `deploy/.env` на сервере (или локальный export).

```bash
BETS_ADMIN_SECRET=ваш-секрет
```

Backend без этого секрета вернёт `403 ADMIN_DISABLED`.

### Шаг D3. Вызвать resolve

**Где:** терминал с доступом к backend (localhost или prod URL).

**Победитель — YES (outcome index 0):**

```bash
curl -X POST "http://localhost:8080/api/admin/bets/markets/0xВАШ_CONDITION_ID/resolve" \
  -H "Content-Type: application/json" \
  -H "X-Admin-Secret: ваш-секрет-из-BETS_ADMIN_SECRET" \
  -d '{"winningOutcomeIndex": 0}'
```

**Победитель — NO (outcome index 1):**

```bash
curl -X POST "http://localhost:8080/api/admin/bets/markets/0xВАШ_CONDITION_ID/resolve" \
  -H "Content-Type: application/json" \
  -H "X-Admin-Secret: ваш-секрет-из-BETS_ADMIN_SECRET" \
  -d '{"winningOutcomeIndex": 1}'
```

**На production** замените `http://localhost:8080` на `https://ВАШ_ДОМЕН`.

**Ожидаемый ответ:**

```json
{
  "conditionId": "0x...",
  "status": "RESOLVED",
  "winningOutcomeIndex": 0
}
```

**Что происходит внутри:**
1. `platform_market.status` → `RESOLVED`, `resolution_source` → `MANUAL`
2. `PayoutCalculator.settleMarket()` — parimutuel payout на `user_balance`
3. Статусы ставок: `WON` / `LOST`

**Проверка:**

```bash
# SQL (postgres)
SELECT status, winning_outcome_index, resolution_source FROM platform_market WHERE condition_id = '0x...';
SELECT id, user_id, outcome_index, amount_micro_usdc, status FROM bet WHERE condition_id = '0x...';
SELECT user_id, available_micro_usdc FROM user_balance;
```

Пользователь увидит обновление во вкладке **Wallet → Winnings balance** и **My bets** (статус `WON`).

---

## Часть E. Admin cancel (отмена рынка и refund)

Используйте, когда рынок отменён / исход не определён и нужно **вернуть все ставки** на internal balance.

**Где:** тот же admin API, тот же header.

```bash
curl -X POST "http://localhost:8080/api/admin/bets/markets/0xВАШ_CONDITION_ID/cancel" \
  -H "X-Admin-Secret: ваш-секрет-из-BETS_ADMIN_SECRET"
```

**Что происходит:**
- `platform_market.status` → `CANCELLED`
- Все `CONFIRMED` ставки → `REFUNDED`, полная сумма на `user_balance`

On-chain USDC **не** возвращается автоматически — refund идёт на **internal balance**. Пользователь забирает через **Withdraw** (или вы вручную отправляете USDC с treasury).

---

## Часть F. Withdraw (вывод выигрыша пользователем)

**Где в UI:** Wallet → **Winnings balance** → **Withdraw to wallet**.

**Условия:**
- `BETS_TREASURY_PRIVATE_KEY` задан и совпадает с адресом treasury
- На treasury достаточно USDC + MATIC
- `GET /api/bets/balance` → `withdrawEnabled: true`
- Сумма ≥ `BETS_MIN_WITHDRAW_USDC`
- Не чаще чем раз в `BETS_WITHDRAW_COOLDOWN_MINUTES` (по умолчанию 5 мин)

**Проверка API (с JWT пользователя):**

```bash
curl -s -H "Authorization: Bearer ВАШ_JWT" \
  http://localhost:8080/api/bets/balance
```

---

## Часть G. Типичные проблемы

| Симптом | Где смотреть | Решение |
|---------|--------------|---------|
| `"enabled": false` в `/api/bets/config` | `deploy/.env` | `BETS_ENABLED=true` **и** непустой `BETS_TREASURY_ADDRESS` |
| `PAYMENT_NOT_VERIFIED` при confirm | `POLYGON_RPC_URL`, logs backend | RPC должен отдавать receipt; tx должна быть `Transfer` USDC user→treasury на точную сумму |
| Withdraw disabled в UI | `/api/bets/balance` | Задать `BETS_TREASURY_PRIVATE_KEY`; ключ должен соответствовать `BETS_TREASURY_ADDRESS` |
| `403 ADMIN_FORBIDDEN` | Header запроса | Header `X-Admin-Secret` должен **точно** совпадать с `BETS_ADMIN_SECRET` |
| Auto-resolve не срабатывает | Gamma, `endDate` | Нужно `endDate + 24h < now` **и** Gamma `closed=true` с price ≥ 0.99; иначе — admin resolve |
| CLOB UI всё ещё виден | `/api/bets/config` | Frontend скрывает CLOB только при `enabled: true`; перезапустите backend с `BETS_ENABLED=true` |

---

## Справочник переменных

| Переменная | Где задаётся | Назначение |
|------------|--------------|------------|
| `BETS_ENABLED` | `deploy/.env` → backend | `true` включает internal bets |
| `BETS_TREASURY_ADDRESS` | `deploy/.env` → backend | Polygon-адрес приёма ставок |
| `BETS_TREASURY_PRIVATE_KEY` | `deploy/.env` → backend | Подпись withdraw tx (hot wallet) |
| `BETS_ADMIN_SECRET` | `deploy/.env` → backend | Header `X-Admin-Secret` для admin API |
| `BETS_PLATFORM_FEE_BPS` | `deploy/.env` → backend | Комиссия платформы при payout (500 = 5%) |
| `POLYGON_RPC_URL` | `deploy/.env` → backend | Verify ставок + broadcast withdraw |
| `VITE_PRIVY_APP_ID` | build frontend | Кошелёк в Mini App |
| `VITE_POLYGON_RPC_URL` | build frontend | Баланс USDC в UI |

---

## Справочник API

| Метод | Путь | Auth | Назначение |
|-------|------|------|------------|
| GET | `/api/bets/config` | — | Включены ли ставки (UI читает это) |
| GET | `/api/bets/pool/{conditionId}` | — | Пул YES/NO |
| POST | `/api/bets/prepare` | JWT | Calldata USDC transfer |
| POST | `/api/bets/confirm` | JWT | Verify tx → CONFIRMED |
| GET | `/api/bets/mine` | JWT | Ставки пользователя |
| GET | `/api/bets/balance` | JWT | Internal balance |
| POST | `/api/bets/withdraw` | JWT | Вывод USDC |
| POST | `/api/admin/bets/markets/{conditionId}/resolve` | `X-Admin-Secret` | Ручной резолв |
| POST | `/api/admin/bets/markets/{conditionId}/cancel` | `X-Admin-Secret` | Отмена + refund |
