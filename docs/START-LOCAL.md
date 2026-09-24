# Запуск Foxaria на локальном ПК

## Требования

- **JDK 21** — или положи portable JDK в папку `.jdks/jdk-21*` рядом с репозиторием: скрипты подхватят сами.
- **MySQL для локалки не обязателен** — в `servers/game/plugins/Foxaria/config.yml` поставь `database.type: sqlite` (место помечено `# [VM]`).

## Первый запуск

1. `up.cmd` — соберёт все плагины, разложит jar по `servers/*/plugins`, скопирует конфиги и запустит 4 сервера.
2. Клиентом заходи на `localhost` (прокси, порт 25565).
   Порты: 25565 proxy · 25566 auth · 25567 lobby · 25568 game.

## Каждый день

| Скрипт | Что делает |
| --- | --- |
| `start.cmd` | запуск всех серверов без пересборки |
| `stop.cmd` | остановка всех серверов |
| `up.cmd` | полная пересборка плагинов + запуск |

Поменял код плагина → `up.cmd`. Поменял только конфиг → `stop.cmd` + `start.cmd`.

## Где конфиги

- Игровой плагин: `servers/game/plugins/Foxaria/` (`config.yml`, `messages.yml`, `modules/*.yml`)
- Прокси: `servers/proxy/plugins/FoxariaProxy/config.yml`
- Аутентификация/лобби: `servers/auth|lobby/plugins/FoxariaHubGuard/config.yml`
- Сайт (опционально): `web/site/config.js`
