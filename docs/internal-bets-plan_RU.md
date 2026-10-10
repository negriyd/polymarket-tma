# Собственный механизм ставок (вместо Polymarket CLOB) — план

Статус: **Фаза 3 завершена** → следующий шаг: **Фаза 4 (ops)**  
Дата плана: 24 мая 2026  
Фаза 0 закрыта: 24 мая 2026  
Фаза 1 закрыта: 24 мая 2026  
Фаза 2 закрыта: 24 мая 2026  
Фаза 3 закрыта: 24 мая 2026

Polymarket остаётся **источником рынков и oracle резолва**. Приём ставок, учёт, комиссия и выплаты — на нашей стороне. Это **внутренний parimutuel (пул ставок)**, а не торговля outcome-токенами на CLOB.

См. также:
- [bets-setup_RU.md](./bets-setup_RU.md) — **пошаговое включение end-to-end и admin resolve**
- [trading_RU.md](./trading_RU.md) — legacy CLOB path (заморожен)
- [bets-decisions_RU.md](./bets-decisions_RU.md) — полный текст решений Фазы 0

---

## Резюме Фазы 0 (принятые решения)

| # | Вопрос | Решение |
|---|--------|---------|
| **0.1** | Модель ставок | **Parimutuel (пул)** — победители делят пул пропорционально своим ставкам; fixed odds отложены на Фазу 5+ |
| **0.2** | Резолв рынка | **Gamma — основной источник**; **grace period 24 ч** после `endDate`; **ручной admin** для спорных/отменённых рынков |
| **0.3** | Выплаты | **Internal balance** при win + **on-chain withdraw** по кнопке Claim (гибрид) |
| **0.4** | Юридика | Зафиксированы **риски и обязательства оператора** (см. ниже); полноценная юридическая экспертиза — **TODO до prod** |
| **0.5** | Документация | CLOB path = **deprecated / frozen**; internal bets = **primary** — обновлены `trading_RU.md`, `trading.md`, `ToDo.txt` |

### 0.1 — Parimutuel (пул)

**Почему:** не нужно задавать коэффициенты, нет counterparty risk платформы на неверных odds, проще MVP.

**Формула:**
```
net_pool = yes_total + no_total
fee = net_pool × platform_fee_bps / 10_000
payout_pool = net_pool − fee
user_payout = user_stake / winning_side_total × payout_pool
```

**Scope MVP:** только бинарные рынки (YES/NO, `outcome_index` 0 или 1). Multi-outcome / negRisk — Фаза 5.

**UI:** показываем implied odds из пула (`no_total / yes_total` для YES) и «ваша доля в пуле X%».

### 0.2 — Резолв

| Источник | Когда | Действие |
|----------|-------|----------|
| **Gamma API** | `closed=true` + winning outcome (price ≈ 1.0) | Авто-резолв после grace period |
| **Grace period** | 24 ч после `MarketDto.endDate` | Новые ставки блокируются за 15 мин до `endDate`; авто-резолв не раньше чем через 24 ч после `endDate` |
| **Admin manual** | Gamma не дал исход / отмена / спор | `POST /admin/markets/{conditionId}/resolve` или `.../cancel` |
| **Refund** | Рынок отменён (CANCELLED) | Полный возврат ставок на internal balance |

**Job:** `@Scheduled` каждые 10 мин — рынки с активными ставками и `endDate + 24h < now`.

### 0.3 — Выплаты (гибрид)

1. После резолва `PayoutCalculator` зачисляет **net** на `user_balance` (PostgreSQL, микро-USDC).
2. Пользователь жмёт **Withdraw / Claim** → backend шлёт USDC с treasury hot wallet на Privy-адрес.
3. **Min withdraw:** 1 USDC (конфиг). **Rate limit:** 1 withdraw / user / 5 min.

**Почему не сразу on-chain payout:** меньше gas, batch-friendly, проще retry при сбое RPC.

**Приём ставок:** on-chain USDC `transfer(treasury, amount)` — verify по `txHash` (как fee flow).

### 0.4 — Юридические заметки (не юридическая консультация)

> ⚠️ Это инженерная фиксация рисков, **не** юридическое заключение. Перед публичным запуском нужен counsel.

| Тема | Статус / действие |
|------|-------------------|
| Мы — **оператор пула**, не посредник Polymarket | UI: «Ставки принимает [название платформы]»; не брендировать как Polymarket trading |
| Gambling / betting regulation | Зависит от юрисdiction пользователей (EU, US, etc.) — **TODO: юрист до prod** |
| KYC/AML | MVP без KYC; при масштабе — оценить пороги |
| Terms of Service | **TODO:** ToS + Privacy Policy + disclaimer «можете потерять ставку» |
| Geo-restrictions | Рассмотреть блокировку US/EU или disclaimer «используйте на свой риск» |
| Хранение средств | Treasury wallet — hot wallet; документировать лимиты и мониторинг |
| Oracle trust | Исход берём с Polymarket Gamma; при расхождении — admin override |

### 0.5 — CLOB deprecated, internal bets primary

- **Primary path (новая разработка):** `POST /api/bets/*` — см. Фазы 1–3.
- **Legacy CLOB (`/api/orders/*`, `/api/clob/auth/*`):** код **остаётся в репо**, но **не развивается** до стабилизации Polymarket deposit wallet (Фаза 5.5). UI скрыть в Фазе 3.4.
- **Markets:** Gamma API без изменений.
- **Positions/redeem CLOB:** остаются для пользователей с историей on-chain позиций; новые пользователи идут через internal bets.

---

## Краткая архитектура (целевая)

```
Gamma API → MarketService → UI
UI → POST /api/bets/prepare|confirm → BetService → PostgreSQL
UI → USDC transfer → Platform treasury wallet
BetService → verify tx → Polygon RPC
@Scheduled → MarketResolutionJob → Gamma → PayoutCalculator → user_balance
UI → POST /api/bets/withdraw → USDC → User Privy wallet
```

---

## TODO — пошаговый план

### Фаза 0 — решения ✅ (24 мая 2026)

- [x] **0.1** Модель: **parimutuel**
- [x] **0.2** Резолв: **Gamma + grace 24h + manual admin**
- [x] **0.3** Выплаты: **internal balance + on-chain withdraw**
- [x] **0.4** Юридические риски зафиксированы; counsel до prod — TODO
- [x] **0.5** Docs обновлены: CLOB deprecated, internal bets primary

### Фаза 1 — Backend core (3–5 дней) ✅ (24 мая 2026)

- [x] **1.1** Flyway `V2__internal_bets.sql` — таблицы `platform_market`, `bet`, `payout`, `user_balance`
- [x] **1.2** `AppProperties.Bets` — `platform-fee-bps`, `treasury-address`, `min-bet-usdc`, `lock-before-end-minutes`, `resolution-grace-hours`
- [x] **1.3** `BetService.prepare()` — calldata USDC transfer (pattern `FeeCalldataBuilder`)
- [x] **1.4** `BetService.confirm()` — verify tx via RPC, insert `bet`
- [x] **1.5** `BetController` — `POST /api/bets/prepare`, `POST /api/bets/confirm`, `GET /api/bets/mine`, `GET /api/bets/config`, `GET /api/bets/pool/{conditionId}`
- [x] **1.6** `MarketPoolService` — агрегаты YES/NO по `conditionId`, implied odds для UI

### Фаза 2 — Резолв и выплаты (2–4 дня) ✅ (24 мая 2026)

- [x] **2.1** `MarketResolutionJob` — poll Gamma для `closed` + winning outcome
- [x] **2.2** `PayoutCalculator` — parimutuel + fee из конфига
- [x] **2.3** Обновление `bet.status`, создание `payout` rows, credit `user_balance`
- [x] **2.4** `WithdrawService` — on-chain USDC с treasury на Privy wallet
- [x] **2.5** Admin endpoint: manual resolve / cancel market / refund

### Фаза 3 — Frontend (2–3 дня) ✅ (24 мая 2026)

- [x] **3.1** Заменить `MarketTradeSheet` CLOB flow на Bet flow (prepare → sign transfer → confirm)
- [x] **3.2** Показать пул: «YES $X / NO $Y», implied odds, ваша ставка
- [x] **3.3** Экран «Мои ставки» + баланс + Withdraw (в Wallet tab)
- [x] **3.4** Скрыть CLOB auth и CTF approvals в UI когда `BETS_ENABLED=true`

### Фаза 4 — Ops и качество (2–3 дня)

- [ ] **4.1** Рабочий Polygon RPC в prod (Alchemy/Infura)
- [ ] **4.2** Treasury hot wallet + мониторинг USDC
- [ ] **4.3** Telegram-уведомления: резолв, выигрыш, withdraw
- [ ] **4.4** Метрики: `bets_total_usdc`, `payouts_total_usdc`, `platform_fee_collected`
- [ ] **4.5** Audit log + admin dashboard (опционально)

### Фаза 5 — Улучшения (позже)

- [ ] **5.1** Whitelist рынков (`platform_market.enabled`)
- [ ] **5.2** Max stake per user / per market
- [ ] **5.3** Partial refunds при отмене
- [ ] **5.4** Multi-outcome markets (negRisk)
- [ ] **5.5** CLOB «pro mode» после стабилизации Polymarket deposit wallet

---

## Схема БД (уточнено в Фазе 0)

```
platform_market
  condition_id PK, question, end_date, status (OPEN|LOCKED|RESOLVED|CANCELLED)
  winning_outcome_index, resolved_at, resolution_source (GAMMA|MANUAL)
  enabled BOOLEAN DEFAULT true

bet
  id, user_id, condition_id, outcome_index (0|1)
  amount_micro_usdc, payment_tx_hash UNIQUE, status
  idempotency_key UNIQUE, created_at

payout
  id, bet_id UNIQUE, user_id, gross_micro, fee_micro, net_micro
  credited_at  -- зачисление на user_balance

user_balance
  user_id PK, available_micro_usdc, updated_at

withdrawal
  id, user_id, amount_micro, tx_hash, status (PENDING|SENT|FAILED), created_at
```

---

## Поток пользователя (MVP)

1. Выбор рынка из Gamma
2. Сумма + YES/NO
3. `POST /api/bets/prepare` → USDC transfer calldata → treasury
4. Privy подписывает и шлёт tx
5. `POST /api/bets/confirm` → verify → `bet` = CONFIRMED
6. Lock новых ставок за 15 мин до `endDate`
7. Через 24 ч после `endDate` — job резолвит → WON/LOST → credit balance
8. `POST /api/bets/withdraw` → USDC на кошелёк

---

## Parimutuel — пример

- YES: $100 (Alice $60, Bob $40), NO: $50
- Пул $150, fee 5% → $7.5 платформе, $142.5 победителям YES
- Alice: 60/100 × 142.5 = **$85.5**

---

## Конфигурация (актуальная)

Полная пошаговая инструкция: **[bets-setup_RU.md](./bets-setup_RU.md)**.

```yaml
app:
  bets:
    feature-enabled: ${BETS_ENABLED:false}
    treasury-address: ${BETS_TREASURY_ADDRESS:${TRADING_FEE_RECIPIENT:}}
    treasury-private-key: ${BETS_TREASURY_PRIVATE_KEY:}
    admin-secret: ${BETS_ADMIN_SECRET:}
    platform-fee-bps: ${BETS_PLATFORM_FEE_BPS:500}
    min-bet-usdc: ${BETS_MIN_USDC:1}
    min-withdraw-usdc: ${BETS_MIN_WITHDRAW_USDC:1}
    lock-before-end-minutes: ${BETS_LOCK_MINUTES:15}
    resolution-grace-hours: ${BETS_GRACE_HOURS:24}
    withdraw-cooldown-minutes: ${BETS_WITHDRAW_COOLDOWN_MINUTES:5}
```

---

## Что переиспользовать

- `MarketService` / Gamma
- Telegram auth, JWT, Privy
- `FeeCalldataBuilder` — USDC transfer pattern
- `ApprovalStatusReader` / RPC — verify incoming transfers

## Legacy (заморожено)

- CLOB auth, `OrderBuilder`, `ClobOrderClient`, deposit wallet
- On-chain CTF redeem для **новых** пользователей (старые позиции — по необходимости)

---

## Оценка сроков

| Объём | Срок | Статус |
|-------|------|--------|
| MVP (Фазы 1–3) | ~2–3 недели | ✅ готово (24 мая 2026) |
| Production-ready (Фаза 4) | +1–2 недели | в работе |
