# Архитектура платформы Foxaria

## Общий принцип

Foxaria собирается как **один runtime-плагин для Paper 1.21.11**, но кодовая база остаётся **многомодульной**.

Это даёт правильный production-компромисс:

- деплой простой: на сервер кладётся один `Foxaria.jar`
- код не скатывается в giant plugin class
- модули остаются изолированными по зонам ответственности
- storage, commands, listeners, GUI и services не перемешиваются

Итоговая модель: **modular monolith на уровне кода, single-plugin runtime на уровне сервера**.

## Слои runtime

### 1. Bootstrap
- точка входа Paper-плагина
- инициализация контейнера сервисов
- порядок запуска модулей
- fail-safe проверки окружения
- безопасное отключение модулей при ошибке

### 2. Core Platform
- загрузка `config.yml`, `messages.yml`, `modules/*.yml`
- async scheduler
- database gateway и пул соединений
- миграции схемы
- audit logging
- permissions wrapper
- GUI framework
- сериализация предметов
- gameplay utilities: spawn, RTP, homes, TPA, combat tag
- anarchy policy hooks: spawn-protection, lag cleanup, nether roof, anti-abuse

### 3. Feature Modules
- economy
- kits
- shop
- player shops
- auction
- ranks
- moderation
- admin
- security
- custom items
- retention
- store lifecycle

### 4. Optional Compatibility Boundary

Foxaria должен уметь жить сам по себе, без обязательных внешних плагинов.

При этом boundary для совместимости можно оставить:

- Vault bridge
- Tebex queue/webhook worker
- внешний anti-cheat bridge

Но это именно optional boundary, а не runtime dependency.

## Модули

### `foxaria-api`
- общие интерфейсы сервисов
- модели данных
- контракты модулей
- storage abstractions

### `foxaria-core`
- service registry
- config/messages
- database pool
- migration engine
- audit service
- permissions service
- teleport/combat services
- GUI base
- core gameplay listeners

### `foxaria-economy`
- балансы
- переводы
- ledger/transaction log
- административные операции по балансу
- налог на переводы и money sinks

### `foxaria-kits`
- стартовые и ежедневные наборы
- donor kits
- cooldown
- GUI preview
- playtime unlock rules

### `foxaria-shop`
- серверный GUI-магазин
- категории
- buy/sell offers
- конфигурируемые цены
- логирование покупок и продаж

### `foxaria-player-shops`
- личные магазины игроков
- владение витриной
- stock management
- inspect mode для стаффа
- налоги и sink для экономики

### `foxaria-auction`
- выставление предметов на аукцион
- комиссия
- expiry
- offline delivery через mailbox
- safe-purchase flow без двойной продажи

### `foxaria-ranks`
- встроенные группы и права
- temporary rank grants
- runtime permission attachments
- префиксы в display/player list

### `foxaria-store`
- очередь выдачи донат-пакетов
- idempotent fulfillment
- revoke flow
- subscription tracking
- console command dispatch

### `foxaria-moderation`
- punishments
- reports
- notes
- freeze/mute logic
- vanish/socialspy/commandspy
- anti-spam и chat filter
- anti-bot join throttling

### `foxaria-admin`
- admin panel
- maintenance mode
- restart countdown
- audit lookup

### `foxaria-security`
- command rate limit
- suspicious event logging
- exploit/container protections
- anti-cheat bridge hooks

### `foxaria-custom-items`
- identity-bound custom items
- crate keys
- reward claim items
- one-time rewards
- anti-duplication metadata

### `foxaria-retention`
- login streaks
- playtime rewards
- vote rewards
- referrals
- season info
- announcements

## Правила архитектуры

### Асинхронность
- все JDBC-операции идут вне main thread
- Bukkit/Paper API вызывается только на main thread
- async -> sync границы явные

### Storage
- интерфейс сервиса отделён от repository/storage реализации
- миграции versioned
- dev = SQLite
- prod = MySQL/PostgreSQL-ready

### Безопасность
- денежные операции логируются
- punishments логируются
- донат-выдачи idempotent
- reward claims защищены от повторной выдачи
- конкурентные покупки и списания завязаны на SQL-state transition

### Локализация
- все пользовательские сообщения выносятся в `messages.yml`
- дефолтный язык проекта: русский
- launch scripts могут оставаться ASCII-safe из-за ограничений Windows console encoding, но игровой/runtime UX должен быть русским

## Итоговая структура проекта

- `foxaria-api`
- `foxaria-core`
- `foxaria-economy`
- `foxaria-kits`
- `foxaria-shop`
- `foxaria-player-shops`
- `foxaria-auction`
- `foxaria-ranks`
- `foxaria-store`
- `foxaria-moderation`
- `foxaria-admin`
- `foxaria-security`
- `foxaria-custom-items`
- `foxaria-retention`
- `foxaria-bootstrap`
- `test-server`
- `docs`

## Что обязательно должно быть готово перед продом

- зелёная сборка `.\gradlew.bat build`
- один актуальный `Foxaria.jar`
- рабочий `Paper 1.21.11`
- `online-mode=true`
- production database
- прогнанные миграции
- русские `messages.yml` и `modules/*.yml`
- проверенные staff flows
- проверенные economy/store flows
- проверенные anti-abuse лимиты
