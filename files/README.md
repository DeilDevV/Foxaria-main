# Foxaria

Мульти-модульный Minecraft-проект: Paper-плагин **Foxaria**, прокси-плагин (BungeeCord) и сайт.

## Структура репозитория

```
Foxaria-main/
├─ modules/               ← весь исходный код (Gradle multi-module)
│  ├─ foxaria-api/          общие API и сервисы
│  ├─ foxaria-core/         ядро: БД (Hikari, MySQL/Postgres/SQLite)
│  ├─ foxaria-bootstrap/    сборка всего в единый Foxaria.jar (shadowJar)
│  ├─ foxaria-proxy-bungee/ плагин прокси
│  ├─ foxaria-hub-guard/    защита auth/lobby
│  └─ ...                   economy, kits, shop, guilds, regions и т.д.
├─ servers/               ← готовые серверные инстансы (рантайм)
│  ├─ auth/                 auth backend (Paper, 25566)
│  ├─ lobby/                лобби (Paper, 25567)
│  ├─ game/                 игровой сервер, бывший test-server (25568)
│  ├─ proxy/                BungeeCord-прокси (25565)
│  └─ production-template/  шаблон продакшен-сервера
├─ web/site/              ← сайт (backend + frontend)
├─ config/gameplay/       ← игровые конфиги вне модулей (quests.yml ступеней)
├─ deploy/                ← pm2 (ecosystem.config.js), deploy.sh/ps1, nginx
├─ scripts/               ← утилиты: генератор квестов, миграции БД
├─ docs/                  ← документация
└─ up.cmd                 ← сборка плагинов + перезапуск всех серверов (Windows)
```

## Сборка (нужна JDK 21+)

```bat
gradlew.bat :foxaria-bootstrap:shadowJar
```

Имена Gradle-проектов не изменились (`:foxaria-api`, `:foxaria-bootstrap`, …) —
переезд в `modules/` сделан через `projectDir` в `settings.gradle`, поэтому
все зависимости `project(':...')` и задачи работают как раньше.

После сборки shadow-задачи сами раскладывают jar'ники по серверам:

| Модуль | Артефакт | Куда попадает |
| --- | --- | --- |
| `foxaria-bootstrap` | `Foxaria.jar` | `servers/game/plugins`, `servers/production-template/plugins` |
| `foxaria-proxy-bungee` | `FoxariaProxy.jar` | `servers/proxy/plugins` |
| `foxaria-hub-guard` | `FoxariaHubGuard.jar` | `servers/lobby/plugins`, `servers/auth/plugins` |
| `foxaria-cases-bukkit` | `FoxariaCases.jar` | game / lobby / production-template |
| `foxaria-cases-proxy` | `FoxariaCasesProxy.jar` | `servers/proxy/plugins` |

## Запуск (Windows, локально)

- `up.cmd` — собрать всё и перезапустить auth + lobby + game + proxy.
- `up.cmd --no-build` — только перезапуск.
- Только игровой сервер: `servers/game/start-test-server.bat`.

## Обновление Foxaria на игровом сервере

`servers/game/update-foxaria.cmd`

## Конфигурация Foxaria на сервере

Всё в `servers/game/plugins/Foxaria/` (`config.yml`, `messages.yml`, `modules/*.yml`).

## Продакшен (Linux, `/opt/foxaria`)

`deploy/deploy.sh` + pm2-конфиг `deploy/ecosystem.config.js` — см. `deploy/README.md`.
