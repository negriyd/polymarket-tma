# Phase 2 — Статус торговли

> **Стратегия (24 мая 2026):** основной путь для новых пользователей — **внутренние ставки (parimutuel)**, MVP (Фазы 0–3) **готов**.  
> Пошаговое включение: **[bets-setup_RU.md](./bets-setup_RU.md)**. План: [internal-bets-plan_RU.md](./internal-bets-plan_RU.md).  
> **Обновление (11 октября 2026): CLOB-торговля снова работает.** Блокер «maker address not allowed, please use the deposit wallet flow» снят: торговля идёт через **deposit wallet** пользователя и подписи `POLY_1271`. Проверено реальным ордером (`status: live`, затем отмена) — см. раздел «CLOB v2 через deposit wallet» ниже и `tools/clob-v2-probe/`.
> Режим выбирается флагом: `BETS_ENABLED=true` — внутренние ставки (`/api/bets/*`), `BETS_ENABLED=false` — торговля на Polymarket CLOB (`/api/orders/*`).

## Внутренние ставки (`BETS_ENABLED=true`, Фазы 1–3 ✅)

| Компонент | Статус | Где |
|-----------|--------|-----|
| Backend `/api/bets/*` | ✅ | `backend/.../bets/` |
| DB V2 + V3 | ✅ | `V2__internal_bets.sql`, `V3__withdrawals.sql` |
| Auto-resolve + payouts | ✅ | `MarketResolutionJob`, `PayoutCalculator` |
| Withdraw + admin | ✅ | `WithdrawService`, `BetAdminController` |
| Frontend bet flow | ✅ | `MarketTradeSheet.tsx`, `MarketDetailPage.tsx` |
| Wallet: баланс, My bets, withdraw | ✅ | `_WalletPanel.tsx` |

**Сквозной поток (пользователь):** Markets → Bet YES/NO → USDC на treasury → confirm → (после resolve) balance → Withdraw.  
**Оператор:** env в `deploy/.env` → см. [bets-setup_RU.md](./bets-setup_RU.md).

---

## CLOB v2 через deposit wallet — ✅ работает

С апреля–мая 2026 Polymarket не принимает ордера, где `maker` — обычный EOA. Каждый пользователь торгует через **deposit wallet** — смарт-кошелёк, владелец которого его Privy EOA. Адрес детерминирован (CREATE2), кошелёк разворачивается и обслуживается без газа через **Polymarket Relayer**, пополняется через **Polymarket Bridge**.

**Проверка 11.10.2026** (тестовый кошелёк `0x415a…` → deposit wallet `0xa1D1…bE57`): развёртывание через relayer, пополнение USDT (Solana) → 4.59 pUSD, разрешения, API-ключ, ордер `POLY_1271` принят CLOB (`status: live`) и отменён. Эталон этого ордера зашит в `OrderBuilderTest`.

### Рабочая схема

| Что | Значение |
|---|---|
| API-ключ (L1) | подпись EOA, `POLY_ADDRESS` = EOA (без изменений) |
| Ордер | `signatureType=3` (`POLY_1271`), `maker` = `signer` = deposit wallet |
| Подпись | EOA подписывает ERC-7739 `TypedDataSign` (ордер внутри как `contents`); бэкенд собирает итоговую подпись 317 байт |
| Залог | **pUSD** `0xC011a7E1…` (обёрнутый USDC), не USDC.e |
| Разрешения | 10 штук одним пакетом: pUSD → 2 exchange v2, NegRiskAdapter, exchange v3; CTF → 2 exchange v2, NegRiskAdapter, 2 CTF collateral adapter; PositionManager → exchange v3 |
| Развёртывание / разрешения / комиссия / Claim / вывод | пакеты (`Batch`, EIP-712), которые подписывает EOA и исполняет relayer — газ пользователю не нужен |
| Пополнение | `POST bridge.polymarket.com/deposit` → адреса в EVM / Solana / Tron; любой поддерживаемый стейблкоин приходит как pUSD |

### Компоненты

| Компонент | Где |
|---|---|
| Подпись POLY_1271 (обёртка ERC-7739, `finalizeSignature`) | `trading/OrderBuilder.java` |
| Relayer-клиент: адрес кошелька, `WALLET-CREATE`, `WALLET`-пакеты, builder HMAC | `trading/relayer/RelayerClient.java`, `DepositWalletAddress`, `DepositWalletBatch`, `BuilderAuthSigner` |
| Онбординг, разрешения, общий механизм пакетов | `trading/relayer/DepositWalletService.java`, `DepositWalletApprovals`, `PendingBatchCache` |
| Пополнение и вывод через мост | `trading/bridge/BridgeClient.java`, `FundingService`, `BridgeWithdrawService` |
| Claim (pUSD через CTF collateral adapter) | `redeem/RedeemService.prepareBatch` |
| Фронтенд: кошелёк, Top up, Withdraw | `features/wallet/DepositWalletSection.tsx`, `FundingPanel.tsx`, `WithdrawPanel.tsx`, `relayerTx.ts` |
| Фронтенд: сделка, Claim | `features/trading/ClobTradeSheet.tsx`, `PositionRow.tsx` |
| Проверочный скрипт на официальных TS SDK | `tools/clob-v2-probe/` |

### Эндпоинты (все требуют авторизации)

| Метод | Путь | Назначение |
|---|---|---|
| GET | `/api/wallet/deposit-wallet` | адрес, развёрнут ли, баланс pUSD, 10 разрешений, `readyToTrade` |
| POST | `/api/wallet/deposit-wallet/deploy` | `WALLET-CREATE` через relayer |
| POST | `/api/wallet/deposit-wallet/approvals/prepare` | пакет с недостающими разрешениями |
| POST | `/api/wallet/deposit-wallet/batches/submit` | `{batchHash, signature}` → relayer (разрешения, комиссия, Claim, вывод) |
| GET | `/api/wallet/deposit-wallet/transactions/{id}` | статус транзакции relayer |
| GET | `/api/wallet/deposit-wallet/funding` | адреса моста и варианты пополнения |
| GET | `/api/wallet/deposit-wallet/funding/deposits` | последние пополнения через мост |
| GET | `/api/wallet/deposit-wallet/withdraw/options` | направления вывода и баланс |
| POST | `/api/wallet/deposit-wallet/withdraw/prepare` | пакет вывода (pUSD напрямую или через мост) + расчёт моста |
| POST | `/api/positions/redeem/prepare-batch` | пакет Claim |

Вызовы в пакетах собирает **только сервер** (хранятся в Redis 10 минут); клиент присылает лишь хеш и подпись, а сервер сверяет подписанта с кошельком пользователя. Так builder-ключи нельзя использовать для произвольных транзакций.

### Настройка (оператор)

| Env | Назначение |
|---|---|
| `POLY_BUILDER_API_KEY`, `POLY_BUILDER_SECRET`, `POLY_BUILDER_PASSPHRASE` | builder-ключ: polymarket.com → Settings → Builders. Без него не работают развёртывание, разрешения, комиссия, Claim и вывод |
| `POLY_BUILDER_CODE` | необязательный bytes32 builder code для атрибуции переводов моста |
| `RELAYER_URL`, `BRIDGE_URL` | по умолчанию `relayer-v2.polymarket.com`, `bridge.polymarket.com` |
| `BETS_ENABLED=false` | показывать CLOB-торговлю вместо внутренних ставок |

Адреса контрактов — `app.relayer.*` в `application.yml`.

### Путь пользователя

1. **Wallet → Connect Polymarket trading** — подпись L1, API-ключ.
2. **Trading wallet → Activate** — развёртывание deposit wallet.
3. **Approve** — одна подпись на все недостающие разрешения.
4. **Top up** — выбрать сеть и токен, отправить на адрес моста; через несколько минут приходит pUSD.
5. **Сделка** на странице рынка: подпись ордера; при включённой комиссии — вторая подпись (перевод pUSD).
6. **Claim** по решённой позиции — подпись пакета, выплата в pUSD.
7. **Withdraw** — pUSD в Polygon или стейблкоин в другую сеть через мост (перед подписью показывается расчёт).

### Ограничения

- Поддержаны **CTF-рынки** (`clobTokenIds`, ордера версии 2). Позиции Polymarket V2 (Router `0x1212…`, ордера версии 3 с `positionID`) не обрабатываются.
- Мост на маленьких суммах дорогой: вывод $3 → USDT (Tron) по расчёту даёт ≈ 1.60 USDT. Интерфейс показывает расчёт и предупреждение перед подписью.
- Пакеты используют nonce relayer на момент подготовки: из двух одновременно подготовленных пакетов исполнится один.
- Статус вывода через мост не отслеживается — показывается только отправка pUSD и ориентировочное время.

---

## CLOB: детали реализации

## Phase 1 vs Phase 2 (что в репозитории)

| Область | Phase 1 (только чтение) | Phase 2 (торговый каркас) |
|---|---|---|
| Рынки, поиск | Gamma + публичный поиск → list/detail | То же |
| Стакан / цены | Gamma + публичный CLOB REST | То же |
| Поток цен | CLOB WS → STOMP `/topic/market/{…}` (опциональный тумблер) | То же |
| Идентификация | Telegram-авторизация, JWT | То же |
| Кошелёк | Опциональный Privy при `VITE_PRIVY_APP_ID` | Privy на Polygon + USDC `balanceOf` через viem |
| Ордера | — | `POST /api/orders/prepare`, `POST /api/orders/submit`, Redis pending-кэш, таблица `order_audit` |
| Submit в CLOB | — | `POST /order` с **L2 HMAC заголовками**, когда у пользователя есть выведенные API-ключи; иначе — без авторизации. Поток L1 (вывод ключей) на `/api/clob/auth/*` |
| Позиции | — | `GET /api/positions` → Data API `GET /positions?user=<wallet>` (Redis-кэш ~10s); ответ содержит title/icon/outcome/PnL/redeemable и флаг `favorite`, склеенный из локальной `favorite_market` |
| Cancel | — | `DELETE /api/orders/{orderId}` → CLOB `DELETE /order` с L2-заголовками через `ClobL2Signer`. Возвращает `CLOB_AUTH_REQUIRED`, если креденшелы не выведены. |
| Approvals USDC / CTF | — | `wallet/ApprovalCalldataBuilder`, `ApprovalStatusReader`, `ApprovalService`, `GET /api/wallet/approvals`; на фронте `ApprovalsSection.tsx` |
| Канонический **orderHash** | — | EIP-712 digest через web3j `StructuredDataEncoder`; поля `tokenId`/`uint256` идут как **decimal strings**, чтобы Privy/viem не теряли точность |
| Redeem / получение выигрыша | — | `POST /api/positions/redeem/prepare` → unsigned calldata `redeemPositions(...)`. Фронт `PositionRow` показывает кнопку Claim для каждой redeemable-позиции. |
| Платформенная комиссия (spread) | — | `app.fees.spread-bps` + `app.fees.recipient-address`. Если ставка ненулевая, `prepare` возвращает дополнительный USDC `transfer(recipient, fee)`, который кошелёк отправляет сразу после подписания ордера. Публичное превью на `GET /api/fees`. |

Ключевые файлы:

- [OrderBuilder.java](../backend/src/main/java/com/polymarket/tma/trading/OrderBuilder.java)
- [PendingOrderCache.java](../backend/src/main/java/com/polymarket/tma/trading/PendingOrderCache.java)
- [TradingService.java](../backend/src/main/java/com/polymarket/tma/trading/TradingService.java)
- [ClobOrderClient.java](../backend/src/main/java/com/polymarket/tma/trading/ClobOrderClient.java)
- [PositionsClient.java](../backend/src/main/java/com/polymarket/tma/trading/PositionsClient.java)
- [RedeemCalldataBuilder.java](../backend/src/main/java/com/polymarket/tma/redeem/RedeemCalldataBuilder.java) · [RedeemService.java](../backend/src/main/java/com/polymarket/tma/redeem/RedeemService.java) · [RedeemController.java](../backend/src/main/java/com/polymarket/tma/redeem/RedeemController.java)
- [FeeCalldataBuilder.java](../backend/src/main/java/com/polymarket/tma/fees/FeeCalldataBuilder.java) · [FeeService.java](../backend/src/main/java/com/polymarket/tma/fees/FeeService.java) · [FeeController.java](../backend/src/main/java/com/polymarket/tma/fees/FeeController.java)
- [MarketTradeSheet.tsx](../frontend/src/features/trading/MarketTradeSheet.tsx) — **internal bets:** prepare → USDC transfer → confirm (при `BETS_ENABLED=true`)
- [PositionRow.tsx](../frontend/src/features/trading/PositionRow.tsx) — встроенная кнопка Claim на каждой redeemable-позиции
- [useWallet.ts](../frontend/src/features/wallet/useWallet.ts) — кошельки Privy + USDC на Polygon

## Сквозной поток ордера (актуальный)

```mermaid
sequenceDiagram
    autonumber
    participant U as TMA UI
    participant P as Privy кошелёк
    participant B as Бэкенд
    participant POL as Polygon
    participant C as Polymarket CLOB

    participant R as Polymarket Relayer

    U->>B: POST /api/orders/prepare {conditionId, side, price, size, signatureType: POLY_1271}
    B->>B: maker = signer = deposit wallet (CREATE2 из EOA)
    B-->>U: typedData (ERC-7739 TypedDataSign) + orderHash + (опционально) feeBatch
    U->>P: signTypedData(typedData)
    P-->>U: 65-байтная подпись
    U->>B: POST /api/orders/submit {orderHash, signature, idempotencyKey}
    B->>B: достать ордер из Redis, finalizeSignature → 317 байт
    B->>C: POST /order с L2 + подписанный ордер
    C-->>B: {orderID, status}
    B-->>U: SubmittedOrderResponse
    opt feeBatch есть (после успешного ордера)
        U->>P: signTypedData(feeBatch.typedData)
        U->>B: POST /api/wallet/deposit-wallet/batches/submit
        B->>R: WALLET-пакет: pUSD.transfer(recipient, fee)
    end
```

## Сквозной поток Claim (deposit wallet)

```mermaid
sequenceDiagram
    autonumber
    participant U as TMA UI
    participant P as Privy кошелёк
    participant B as Бэкенд
    participant R as Polymarket Relayer

    U->>B: POST /api/positions/redeem/prepare-batch {conditionId, negRisk}
    B-->>U: Batch: CtfCollateralAdapter.redeemPositions(pUSD, 0x0, conditionId, [1, 2])
    U->>P: signTypedData(batch.typedData)
    U->>B: POST /api/wallet/deposit-wallet/batches/submit {batchHash, signature}
    B->>R: WALLET-пакет от deposit wallet
    R-->>U: транзакция в блоке → pUSD на deposit wallet
    U->>B: refetch /api/positions и /api/wallet/deposit-wallet
```

---

## Сбои Privy «Sign and continue» — вероятные причины применительно к коду

Privy показывает обобщённое **«An error has occurred, please try again.»** При возможности логируйте оригинальную ошибку через DevTools / удалённый дебаг.

### 1. **Числовое кодирование `typedData` (закрыто в коде)**

[`OrderBuilder.build`](../backend/src/main/java/com/polymarket/tma/trading/OrderBuilder.java) выдаёт **все** `uint256` / `uint8` поля в `domain` и `message` как **decimal strings**, включая **`chainId`** в домене, поэтому крупные `tokenId` не округляются в JSON, и viem/Privy видят единое соглашение.

Если подпись всё равно падает — обычно дело в **не той сети**, **maker ≠ активный кошелёк**, либо в несовпадении **signatureType**.

### 2. **`wallets[0]` vs подписант, выбранный для EIP-712**

[`useWallet`](../frontend/src/features/wallet/useWallet.ts) берёт `address` из `wallets[0]`. Порядок embedded vs external может выдать «не тот» кошелёк. Подпись делается с `{ address: w.address }`, а `maker`/`signer` в typed data приходят из профиля БД (**после `api.setWalletAddress`**). Любое расхождение даёт ошибку подписи или верификации.

Лучше выбирать **именно embedded Polygon-кошелёк**, совпадающий с сохранённым профилем / `typedData.message.maker`.

### 3. **`signatureType` vs модель кошелька**

Для новых кошельков CLOB принимает только **`POLY_1271`** (deposit wallet); `ClobTradeSheet` всегда шлёт его. EOA (`0`) отклоняется с `CLOB_DEPOSIT_WALLET_REQUIRED`.

### 4. **`orderHash` vs учёт в CLOB**

`orderHash` API — это **EIP-712 struct hash** из web3j `StructuredDataEncoder` (тот же digest, что подписывает кошелёк). CLOB может использовать дополнительные id, как только L1/L2 submit полностью провязан; рассматривайте серверный `orderHash` как канонический digest подготовленных typed data.

---

## L1 / L2 авторизация CLOB (бэкенд)

В `backend/src/main/java/com/polymarket/tma/trading/clob`:

- `ClobAuthBuilder` — собирает EIP-712 `ClobAuth` typed data (домен `ClobAuthDomain`, chainId 137). Все uint — decimal strings.
- `ClobApiKeyClient` — `POST /auth/api-key` с заголовками `POLY_ADDRESS / POLY_SIGNATURE / POLY_TIMESTAMP / POLY_NONCE`. Возвращает `(apiKey, secret, passphrase)`.
- `ClobCredentialsStore` — креденшелы в Redis на пользователя, TTL 30 дней.
- `ClobL2Signer` — HMAC-SHA256 над `timestamp + METHOD + path + body`; URL-safe base64 на входе и выходе (как в py-clob-client).
- `ClobAuthController` — `/api/clob/auth/prepare | submit | status` (требует auth), `DELETE /api/clob/auth` для сброса.
- `ClobOrderClient.submit(...)` — добавляет заголовки `POLY_*`, если есть `creds`.
- `ClobOrderClient.cancel(orderId, wallet, creds)` — `DELETE /order` с L2-заголовками и телом `{"orderID": ...}`.

Фронтенд:

1. На странице Wallet кнопка «Connect to Polymarket trading»:
   - `POST /api/clob/auth/prepare`
   - Privy `signTypedData(typedData)`
   - `POST /api/clob/auth/submit { signature, timestamp, nonce }`
2. При отказе CLOB с 401/403 — фронт сбрасывает creds (`DELETE /api/clob/auth`) и предлагает заново вывести ключ.

## Approvals (USDC + Conditional Tokens) — legacy EOA

> Этот раздел описывает старый EOA-путь (USDC.e, транзакции с газом от пользователя). Для deposit wallet используются `DepositWalletApprovals` (10 разрешений, pUSD) и `/api/wallet/deposit-wallet/approvals/prepare`; `ApprovalsSection.tsx` больше не показывается.

В `backend/src/main/java/com/polymarket/tma/wallet`:

- `ApprovalCalldataBuilder` — чистая ABI-сборка `approve(address,uint256)` (selector `0x095ea7b3`) и `setApprovalForAll(address,bool)` (selector `0xa22cb465`).
- `ApprovalStatusReader` — опциональный Polygon JSON-RPC ридер для `allowance` (USDC) и `isApprovedForAll` (CTF). Возвращает `null`, если RPC недоступен — вызов деградирует мягко.
- `ApprovalService` — собирает список `UnsignedTx` (`to`, `data`, `value=0x0`, `chainId=137`) для всего, чего не хватает. Порог USDC ≥ `1_000_000` (1 USDC) считается approved.
- `ApprovalController` — `GET /api/wallet/approvals` (требует auth).

Polygon-адреса (в `application.yml`):

| Ключ | Значение |
|------|----------|
| `app.polygon.usdc-address` | `0x2791Bca1f2de4661ED88A30C99A7a9449Aa84174` |
| `app.polygon.ctf-address` | `0x4D97DCd97eC945f40cF65F87097ACe5EA0476045` |
| `app.polygon.ctf-exchange-address` (CTF Exchange v2) | `0xE111180000d2663C0091e4f400237545B87B996B` |
| `app.polygon.neg-risk-ctf-exchange-address` (NegRisk CTF Exchange v2) | `0xe2222d279d744050d28e00520010520000310F59` |

> **Важно (миграция CLOB v2, 2026-04-22).** Polymarket мигрировали свою CTF Exchange на v2 22 апреля 2026. Старый v1 (`0x4bFb41d5B3570DeFd03C39a9A4D8dE6Bd8B8982E`) с 30 апреля 2026 отдаёт `400 "order_version_mismatch"` на любые новые ордера. Старые approve USDC / CTF на v1 теперь бесполезны — пользователь должен заново выдать аппрув на v2-контракт при первой сделке. Эталонный код — [py-clob-client-v2](https://github.com/Polymarket/py-clob-client-v2); в наших исходниках актуальная схема живёт в `OrderBuilder`.

### Deposit-wallet flow — решено (октябрь 2026)

С мая 2026 CLOB отвечает на ордер с EOA-`maker` `400 "maker address not allowed, please use the deposit wallet flow"` (бэкенд маппит это в `CLOB_DEPOSIT_WALLET_REQUIRED`). Issues в py-clob-client-v2 (#51, #53, #61, #63, #64, #111, #116) описывали в основном недоработки Python-SDK. Рабочая конфигурация подтверждена сообществом в [#70](https://github.com/Polymarket/py-clob-client-v2/issues/70) и нашей проверкой 11.10.2026: API-ключ на EOA, ордер `signatureType=3` с `maker = signer = deposit wallet`. Онбординг через polymarket.com не нужен — кошелёк разворачивается через relayer. Подробности — в разделе «CLOB v2 через deposit wallet» в начале документа.

Фронтенд (`ApprovalsSection.tsx`): дёргает `GET /api/wallet/approvals`, шлёт каждый `missing[i]` обычной Polygon-транзой через Privy (`{to, data, value:'0x0', chainId:137}`), затем перезапрашивает статус.

## Redeem (получение выигрыша)

В `backend/src/main/java/com/polymarket/tma/redeem`:

- `RedeemCalldataBuilder` — чистое ABI-кодирование `redeemPositions(IERC20,bytes32,bytes32,uint256[])` для CTF. По умолчанию `parentCollectionId = 0x0` (top-level рынки Polymarket); `indexSet = 1 << outcomeIndex` для позиции, которую пользователь сжигает.
- `RedeemService` — по `(conditionId, outcomeIndex)` + кошельку вызывающего возвращает `UnsignedTx` на `app.polygon.ctf-address`.
- `RedeemController` — `POST /api/positions/redeem/prepare` (требует auth).

Фронтенд (`PositionRow.tsx`): кнопка Claim показывается, когда `position.redeemable === true`. Для позиций на deposit wallet используется `RedeemService.prepareBatch` (`POST /api/positions/redeem/prepare-batch`): пакет вызывает `redeemPositions(pUSD, 0x0, conditionId, [1, 2])` на **CtfCollateralAdapter** `0xAdA1…cE1f` или **NegRiskCtfCollateralAdapter** `0xadA2…eAab` (по `negativeRisk` позиции), выплата сразу в pUSD. Старый `POST /api/positions/redeem/prepare` (транзакция с EOA на CTF, выплата в USDC.e) остался для legacy и фронтендом больше не вызывается.

## maker / signer для каждого `signatureType`

В `PrepareOrderRequest` появилось опциональное поле **`makerAddress`**. Резолвинг в `TradingService.prepare`:

| signatureType | maker | signer |
|---|---|---|
| `EOA` (по умолчанию) | wallet | wallet |
| `POLY_PROXY` | обязательный `makerAddress` (proxy) | wallet |
| `POLY_GNOSIS_SAFE` | обязательный `makerAddress` (Safe) | wallet |
| `POLY_1271` (deposit wallet) | deposit wallet, вычисляется из wallet; `makerAddress` необязателен, но должен совпадать (`MAKER_MISMATCH`) | deposit wallet (EOA подписывает обёртку ERC-7739) |

Для `POLY_PROXY` / `POLY_GNOSIS_SAFE` запрос падает с `MAKER_REQUIRED`, если `makerAddress` пуст, или `MAKER_EQUALS_SIGNER`, если совпадает с кошельком. `OrderBuilder.build(maker, signer, req)` пишет два разных поля в EIP-712 message.

---

## Схема ордера CLOB v2

В апреле 2026 Polymarket заменили CTF Exchange — `OrderBuilder` и `ClobOrderClient` теперь собирают именно v2-схему. Поля и их представление на проводе:

| Поле | EIP-712 тип | JSON на проводе | Заметка |
|------|-------------|------------------|---------|
| `salt` | `uint256` | **JSON number** | Генерируется < 2^53, чтобы не терять точность при float64-парсинге на сервере |
| `maker` | `address` | строка | Кто держит средства: deposit wallet (POLY_1271), EOA, proxy или Safe |
| `signer` | `address` | строка | Кто подписывает; для POLY_1271 — сам deposit wallet (проверка через ERC-1271) |
| `tokenId` | `uint256` | строка | Decimal big-int — 256 бит, JSON number терял бы точность |
| `makerAmount` / `takerAmount` | `uint256` | строка | 6-знаковая шкала USDC / share-юнитов |
| `side` | `uint8` (0/1) | строка `"BUY"`/`"SELL"` | На проводе строка, в digest — uint8; CLOB восстанавливает значение на своей стороне |
| `signatureType` | `uint8` | **JSON number** | 0=EOA, 1=POLY_PROXY, 2=POLY_GNOSIS_SAFE, 3=POLY_1271 |
| `timestamp` | `uint256` | строка ms | Новое в v2; заменяет v1-ный `nonce` для уникальности |
| `metadata` | `bytes32` | hex-строка | Новое в v2; по умолчанию `0x00…00` |
| `builder` | `bytes32` | hex-строка | Новое в v2; по умолчанию `0x00…00` (атрибуция билдера) |
| `expiration` | — *(только API)* | строка секунд | В v2 живёт только на проводе, в digest НЕ участвует. По умолчанию `"0"` (без срока) |
| `signature` | — | строка | Лежит **внутри** объекта `order`. Для POLY_1271 — 317 байт: `подпись EOA ‖ app domain separator ‖ hash(order) ‖ строка типа Order ‖ uint16 длины` |

Верхний уровень:

```json
{
  "order": { ... },
  "owner": "<API key UUID>",
  "orderType": "GTC",
  "deferExec": false,
  "postOnly": false
}
```

В v2 `owner` — это UUID L2-API-ключа (в v1 туда писали maker-кошелёк). EIP-712 домен — `name="Polymarket CTF Exchange"`, **`version="2"`**, `chainId=137`, `verifyingContract` переключается между стандартным контрактом и NegRisk в зависимости от `MarketDto.negRisk`.

Регрессионные тесты: [`OrderBuilderTest`](../backend/src/test/java/com/polymarket/tma/trading/OrderBuilderTest.java) (структура struct, диапазон salt, маршрутизация NegRisk) и [`ClobOrderClientPayloadTest`](../backend/src/test/java/com/polymarket/tma/trading/ClobOrderClientPayloadTest.java) (форма JSON на проводе, `owner`, `deferExec`/`postOnly`, отсутствие v1-полей).

---

## Монетизация — конфигурируемый spread / комиссия

На каждый подготавливаемый ордер можно начислять платформенную комиссию. Она **не зависит** от `feeRateBps` на стороне CLOB (тот зарезервирован за оператором биржи) и приходит обычным ERC-20 `transfer` на Polygon на сконфигурированный кошелёк.

### Конфигурация (`application.yml`)

| Ключ | Env | По умолчанию | Описание |
|---|---|---|---|
| `app.fees.spread-bps` | `TRADING_FEE_BPS` | `0` | Комиссия в базисных пунктах (100 = 1%, 50 = 0.5%). `0` отключает. |
| `app.fees.recipient-address` | `TRADING_FEE_RECIPIENT` | `""` | Polygon-адрес, получающий USDC. Пустой отключает. |

### Поток на проводе

1. `POST /api/orders/prepare` собирает ордер; [`FeeService.quote`](../backend/src/main/java/com/polymarket/tma/fees/FeeService.java) считает `fee = price × size × spreadBps / 10_000` (USDC, 6 знаков) и формирует `UnsignedTx` для `USDC.transfer(recipient, fee)`.
2. Ответ бэкенда несёт оригинальный `typedData` + `orderHash` и добавляет:

   ```json
   {
     "feeTx": { "kind": "TRADING_FEE_TRANSFER", "to": "<USDC>", "data": "0xa9059cbb…", "value": "0x0", "chainId": 137 },
     "feeAmountUsdc": "0.025",
     "feeBps": 50
   }
   ```
3. Фронт `MarketTradeSheet`:
   - подписывает ордер через Privy `signTypedData`,
   - если есть `feeTx`, шлёт его через Privy `useSendTransaction`,
   - сабмитит `{orderHash, signature}` в `POST /api/orders/submit`.
4. `GET /api/fees` отдаёт публичный конфиг комиссии, чтобы карточка ставки показывала превью (`Platform fee 0.50% · ≈ $0.025 USDC`) до подписания.

### Комиссия для deposit wallet (POLY_1271)

Для ордеров `POLY_1271` ответ `prepare` вместо `feeTx` содержит **`feeBatch`** — пакет `pUSD.transfer(recipient, fee)` с deposit wallet. `ClobTradeSheet` подписывает его **после** успешной отправки ордера и отдаёт в `batches/submit`; газ платит relayer. Итог для пользователя — две подписи на сделку. Если комиссия не прошла, ордер остаётся, пользователь видит предупреждение.

### Почему отдельный transfer (а не `feeRateBps` в CLOB)

Поле `feeRateBps` в EIP-712 ордере Polymarket уходит **оператору CLOB**, а не сторонней платформе, поэтому через него нельзя пустить долю проекта. Самый чистый MVP — отдельный USDC-перевод сразу после подписи: одна дополнительная Polygon-транза, газ платит пользователь, кошелёк-получатель видит чистый `transfer(...)` на каждую ставку.

### Ограничения / следующие шаги

- Комиссия снимается на **submit**, а не на fill. Если ордер не сматчится — комиссия всё равно собрана; такая модель «плата за размещение ставки» сознательно. Для модели «только при fill» нужен кастомный смарт-контракт-эскроу.
- Для EOA-ордеров газ комиссии оплачивается в POL; для deposit wallet газ не нужен (relayer).
- В `order_audit` пока нет колонок `fee_amount` / `fee_tx_hash`. Их добавление — в production-чеклисте ниже.

## Геоблок — региональные ограничения Polymarket CLOB

CLOB сидит за Cloudflare и отклоняет `POST /order` (и ряд других эндпоинтов) с HTTP **403 «Trading restricted in your region»**, если outbound-IP сервера попадает в ограниченную юрисдикцию. Бэкенд распознаёт эту ошибку и поднимает специальный код `CLOB_GEOBLOCKED`, чтобы UI и логи давали внятное сообщение.

### Стратегия хостинга

Геоблок Polymarket шире чем просто «EU». Реализован через Cloudflare WAF на `POST /order` по комбинации страна + ASN. Эмпирически проверено на этом проекте плюс независимое подтверждение в [polymarket/clob-client#231](https://github.com/Polymarket/clob-client/issues/231) и [Polymarket Help Center — Geographic Restrictions](https://help.polymarket.com/en/articles/13364163-geographic-restrictions):

| Регион | International CLOB | Заметки |
|---|---|---|
| Hetzner **HEL1** (Хельсинки, Финляндия) | **работает** | Самый дешёвый стабильный вариант (~€5/мес). Рекомендуем для MVP. |
| AWS **eu-west-1** (Дублин, Ирландия) | **работает** | Polymarket origin сидит в AWS eu-west-2 London → ~0.5 мс латентность. Идеально для HFT. |
| Vultr **Стокгольм / Мадрид** | обычно работает | Зависит от /24 — проверять `clob-post-probe`. |
| DigitalOcean **ams3** (Амстердам) | как повезёт | UI Polymarket заблокирован для NL, но часть интеграторов пишет, что CLOB API оттуда работает. Можно дёшево попробовать перед сменой провайдера. |
| DigitalOcean **fra1 / lon1**, Hetzner **NBG/FSN/FRA**, Scaleway Paris | блок (Germany / UK / FR заблокированы) | |
| DigitalOcean / AWS **US-регионы** (nyc1, sfo3, us-east-1) | блок | International CLOB отшивает US IP; для США есть отдельный CFTC-регулируемый US API с KYC, это другой эндпоинт. |
| DigitalOcean **tor1 / sgp1 / blr1** | блок | CA / SG / IN. |

**Никаких residential / VPN-прокси** (Bright Data Residential, Oxylabs Residential, mobile-carrier IPv6 пулы и т.п.): даже при `loc=US` от Cloudflare, WAF Polymarket режет такие подсети по `proxy/threat_score>5`. Полимаркет в TOS §2.1.4 явно запрещает использование VPN. Datacenter IPv4 в разрешённой стране — единственный устойчивый путь.

### Operator-диагностика (`/api/diagnostics/*`)

Бэкенд публикует три публичных пробника, ходящих через тот же `WebClient`, что и реальный submit, чтобы можно было дебажить связь без редеплоя:

| Эндпоинт | Что показывает |
|---|---|
| `GET /api/diagnostics/clob-egress-ip` | IP, который видит upstream (`ifconfig.me/ip`). |
| `GET /api/diagnostics/cf-trace` | Cloudflare-trace. Самое важное — `loc=`. После деплоя должно быть `US`. |
| `GET /api/diagnostics/clob-post-probe[?ua=...]` | Шлёт пустой `POST /order`. В разрешённом регионе вернёт 400/401/422 (значит подпись/payload — единственное что осталось). 403 + «Trading restricted» = регион в блок-листе. Параметр `ua` подменяет User-Agent. |

Эндпоинты сознательно без авторизации — они не светят кредами, только публичный egress-IP и Cloudflare-классификацию.

### Порядок транзакций при ставке

На фронте: `prepare → sign → submit → fee tx`. Если CLOB отвергает ордер (геоблок или другая причина), ончейн-комиссия НЕ списывается. Раньше fee шёл до submit, и при геоблоке пользователь платил впустую.

## Production TODO (что осталось)

1. **Учёт fee tx hash** в строке `order_audit` (`fee_amount`, `fee_recipient`, `fee_tx_hash`) — миграция Flyway `V2__order_audit_fees.sql` + плюс провязка в сервисе.
2. **Уведомления** «рынок зарезолвился / можно redeem» (`docs/ToDo.txt` пункт C):
   - polling `/api/positions` на каждого активного пользователя раз в N минут,
   - кросс-чек `redeemable=true` → отправка сообщения через уже сконфигурированный `app.telegram.bot-token`,
   - дедуп по `(user, conditionId)`, чтобы одну выплату не объявлять дважды.
3. **Живая проверка всей цепочки в TMA** на deposit wallet (активация → разрешения → пополнение → ордер → комиссия → Claim → вывод) с боевыми builder-ключами.
4. **Отслеживание вывода через мост** по `/status/{адрес}`; удалить legacy (`ApprovalsSection.tsx`, `/api/wallet/approvals`, `/redeem/prepare`).
5. **UI «My orders»** — открытых CLOB-ордеров пока нет в интерфейсе, поэтому уже подключённый `DELETE /api/orders/{id}` ждёт экрана списка.

---

## Контроль рисков

- Token bucket в Redis на пользователя по эндпоинтам ордеров (включается опцией).
- Серверный кап на `makerAmount` / номинал перед submit.
- Geo/KYC-гейты до разблокировки торговли.
- Killswitch уровня `app.trading.enabled=false` (когда будет провязан).
- Killswitch комиссии: `app.fees.spread-bps=0` отключает монетизацию без редеплоя остального.
