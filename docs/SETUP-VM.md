# Переезд на виртуалку (VPS)

Все точки, которые надо поменять, помечены в файлах маркерами `# [VM]` и `[!ХОСТИНГ]`.

## 1. База данных MySQL

| Файл | Что вписать |
| --- | --- |
| `servers/game/plugins/Foxaria/config.yml` → `database` | host / port / name / username / password |
| `servers/proxy/plugins/FoxariaProxy/config.yml` → `moderation.backend-database` | те же креды (таблица `fx_punishments`) |
| `deploy/ecosystem.config.js` → `env` | `FOXARIA_DB_*`, `FOXARIA_MODERATION_JDBC_URL` |
| `web/site/config.js` → `database`, `jwt`, `sessionSecret` | креды БД + домен `[!ХОСТИНГ]` |

## 2. Пути и домены

| Файл | Что |
| --- | --- |
| `servers/*/start.sh` | `cd /opt/foxaria/...` под свой каталог |
| `deploy/ecosystem.config.js` | `cwd` / `script` пути |
| `web/site/config.js` | `frontendUrl`, `productionUrl`, секреты `[!ХОСТИНГ]` |

## 3. Шаги на сервере

1. Залить репозиторий в `/opt/foxaria` (git clone или rsync).
2. `chmod +x servers/*/start.sh deploy/deploy.sh`
3. Сборка на сервере: `./gradlew :foxaria-bootstrap:shadowJar` — jar-ы сами разложатся по `servers/*/plugins`.
   Или собрать локально и залить содержимое `servers/`.
4. `pm2 start deploy/ecosystem.config.js && pm2 save`
5. nginx для сайта: `deploy/nginx-foxaria-site.conf`.
6. Файрвол: наружу открыт только **25565** (прокси). auth/lobby/game слушают `127.0.0.1`.
7. В `servers/proxy/config.yml` (BungeeCord) серверы указывают на `127.0.0.1:25566/25567/25568`.

## Важно: секреты

Пароль MySQL и SSH-пароль лежали в публичном git — считай их скомпрометированными:
смени оба, дальше держи только в env/переменных окружения на сервере.
