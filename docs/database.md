# Схема БД Foxaria

## Общая стратегия

- development: SQLite
- production: MySQL или PostgreSQL
- все записи идут асинхронно
- на main thread нет blocking JDBC

## Правила конкурентности

- переводы экономики должны завершаться как единая SQL-операция
- покупка аукционного лота должна менять состояние только один раз
- stock в магазинах игроков уменьшается атомарно
- Tebex/store fulfillment должен быть идемпотентным по `external_txn_id`
- identity custom item нельзя “поглотить” дважды
- one-time rewards и daily vote claims должны быть защищены уникальными ключами

## Базовые таблицы

### Core
- `fx_schema_history`
- `fx_spawn_points`
- `fx_player_homes`
- `fx_tpa_requests`
- `fx_audit_log`

### Economy
- `fx_economy_accounts`
- `fx_economy_transactions`

### Commerce
- `fx_shop_categories`
- `fx_shop_offers`
- `fx_player_shops`
- `fx_player_shop_offers`
- `fx_auction_listings`
- `fx_mailbox_deliveries`
- `fx_tebex_fulfillments`
- `fx_store_subscriptions`

### Ranks / Access
- `fx_player_ranks`
- `fx_rank_grants`
- `fx_permission_grants`

### Moderation / Security
- `fx_punishments`
- `fx_reports`
- `fx_staff_notes`
- `fx_security_events`

### Rewards / Retention
- `fx_custom_item_instances`
- `fx_reward_claims`
- `fx_crate_open_logs`
- `fx_login_streaks`
- `fx_playtime_rewards`
- `fx_vote_claims`
- `fx_referrals`

## Важные детали по таблицам

### `fx_tebex_fulfillments`
- хранит grant/revoke queue
- `external_txn_id` должен быть уникальным
- lifecycle: `PENDING -> PROCESSING -> FAILED | FULFILLED`

### `fx_store_subscriptions`
- видимость по активным подписочным пакетам
- нужна для revoke-aware donor lifecycle

### `fx_player_ranks`
- primary group игрока
- должна существовать максимум одна запись на игрока

### `fx_rank_grants`
- временные группы
- `active=false` после истечения или revoke

### `fx_permission_grants`
- временные прямые permission grants
- применяются runtime attachment-слоем

### `fx_custom_item_instances`
- уникальные идентификаторы reward/key items
- `consumed_at = 0` означает, что предмет ещё не погашен

### `fx_reward_claims`
- защита one-time reward items от повторного получения

### `fx_vote_claims`
- одна выдача на игрока, на источник, на дату

### `fx_referrals`
- один реферер на одного приглашённого игрока

## Production рекомендации

- для боевого сервера использовать MySQL/PostgreSQL
- до первого релиза настроить автоматические бэкапы
- прогонять миграции сначала на staging
- следить за размерами `fx_audit_log`, `fx_economy_transactions`, `fx_security_events`
- добавить ротацию/архивацию для heavy-write таблиц при росте онлайна
