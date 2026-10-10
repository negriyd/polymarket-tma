# CLOB v2 deposit-wallet probe

Проверка официальными TS SDK (`@polymarket/clob-client-v2`, `@polymarket/builder-relayer-client`),
что EOA может торговать через Deposit Wallet + `signatureType=3` (POLY_1271).
Подписанный ордер сохраняется в `out/` — эталон для сверки Java `OrderBuilder`.

## Подготовка

1. Отдельный **тестовый** EOA (не treasury, не прод). Приватный ключ → `.env`.
2. Builder-ключи: polymarket.com → Settings → Builders → `BUILDER_*` в `.env`.
3. ~2 USDC.e на Polygon (на адрес deposit wallet из `info` — тогда газ не нужен,
   либо на EOA + немного POL для `fund`).

```bash
cd tools/clob-v2-probe
cp .env.example .env   # заполнить
npm install
```

## Шаги

```bash
npm run probe -- info              # адреса, деплой, балансы, approvals (read-only)
npm run probe -- deploy            # WALLET-CREATE через relayer (gasless)
npm run probe -- wrap 2            # USDC.e на deposit wallet -> pUSD (gasless)
#   или: npm run probe -- fund 2   # USDC.e с EOA -> pUSD на deposit wallet (EOA платит газ)
npm run probe -- approve           # 8 approvals: exchange v2/negRisk/v3 + NegRiskAdapter (gasless)
npm run probe -- auth              # API key, POLY_ADDRESS = EOA -> .creds.json
npm run probe -- balance           # pUSD глазами CLOB (signature_type=3)
npm run probe -- verify <tokenId>   # подписать ордер локально (НЕ отправляется) и проверить через ERC-1271
npm run probe -- order <tokenId> [price] [size] [--keep]
```

`order` по умолчанию ставит post-only GTC BUY на минимальном тике (5 шейров, копейки),
чтобы не исполниться, и сразу отменяет его. `--keep` — оставить ордер в книге.

## Уже проверено (одноразовый ключ, без денег)

- `info`: адрес deposit wallet выводится, версия ордеров CLOB = 2.
- `auth`: L1-авторизация EOA → API key выдаётся.
- `balance` без деплоя: `404 no deposit wallet found for owner`.
- `order` без деплоя: `400 the order signer address has to be the address of the API KEY`
  (CLOB ещё не знает связь EOA → deposit wallet).

## Результат на реальном кошельке (11.10.2026)

- `deploy`: deposit wallet `0xa1D17A5546aD9166Cb14071c36a5741d812CbE57` развёрнут, адрес совпал с вычисленным.
- `approve`: 8 approvals выставлены; CLOB дополнительно проверяет NegRiskAdapter `0xd91E80cF…`.
- Пополнение через Bridge API (`POST bridge.polymarket.com/deposit`): USDT (Solana) → pUSD, `wrap` не нужен.
- `balance`: CLOB видит pUSD и allowances для signature_type=3.
- `verify`: подпись POLY_1271 (ERC-7739, 317 байт) → `isValidSignature` = `0x1626ba7e` (валидна on-chain).
- `order`: **успех** — `POST /order` → `status: live, success: true` (orderID `0x207fe39b…855b`), затем `canceled`. API key = EOA, maker = signer = deposit wallet, signatureType=3.
