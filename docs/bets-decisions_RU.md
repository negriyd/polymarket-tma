# Решения Фазы 0 — внутренние ставки

Дата: 24 мая 2026  
Статус: **утверждено для реализации MVP**

Документ фиксирует продуктовые и технические решения перед разработкой Фазы 1.  
Полный roadmap: [internal-bets-plan_RU.md](./internal-bets-plan_RU.md).

---

## Контекст

Интеграция с Polymarket CLOB v2 заблокирована upstream:
- `order_version_mismatch` (исправлено переходом на v2 schema)
- `maker address not allowed, please use the deposit wallet flow` (требует deposit wallet + POLY_1271)
- Открытые issues в [py-clob-client-v2](https://github.com/Polymarket/py-clob-client-v2) (#51, #53, #61, #63, #64)

**Стратегический pivot:** рынки и резолв — из Polymarket (Gamma); ставки и выплаты — на платформе.

---

## 0.1 Модель ставок: Parimutuel

**Решение:** пул (parimutuel), не fixed odds.

| Критерий | Parimutuel | Fixed odds |
|----------|------------|------------|
| Риск платформы | Низкий | Высокий |
| Сложность MVP | Низкая | Средняя |
| UX | «Доля в пуле» | «Коэффициент 1.8» |
| **Выбор** | ✅ MVP | Отложено |

**Ограничение MVP:** только YES/NO (`outcome_index` 0 или 1).

---

## 0.2 Резолв рынка

**Решение:** Gamma primary + 24h grace + manual admin.

```
endDate − 15min  →  LOCK (новые ставки закрыты)
endDate + 24h    →  earliest auto-resolve via Gamma
```

**Определение победителя (Gamma):**
- `closed == true`
- `outcomePrices[i] >= 0.99` (или официальный winner flag, если появится в API)

**Admin fallback:** отмена рынка → full refund на internal balance.

---

## 0.3 Выплаты

**Решение:** internal balance + on-chain withdraw.

| Этап | Механизм |
|------|----------|
| Приём ставки | On-chain USDC → treasury, verify `Transfer` event |
| После win | Credit `user_balance.available_micro_usdc` |
| Вывод | User-initiated withdraw → treasury sends USDC |

---

## 0.4 Юридика (инженерная фиксация)

**Не является юридической консультацией.**

Обязательства оператора MVP:
1. Прозрачные правила parimutuel и fee в UI
2. Disclaimer: средства на treasury, выплата по Claim
3. ToS / Privacy Policy — **до public prod**
4. Не использовать брендинг «Polymarket» для описания нашего betting product

---

## 0.5 Документация и статус CLOB

| Компонент | Статус |
|-----------|--------|
| `/api/bets/*` | **Primary** — активная разработка |
| `/api/orders/*`, CLOB auth | **Deprecated / frozen** — код в репо, UI скрыть |
| Gamma markets | **Active** — без изменений |
| CLOB positions/redeem | **Legacy** — для существующих on-chain позиций |

Обновлённые файлы: `trading_RU.md`, `trading.md`, `ToDo.txt`, `internal-bets-plan_RU.md`.

---

## Следующий шаг

**Фаза 1.1** — Flyway migration `V2__internal_bets.sql`.
