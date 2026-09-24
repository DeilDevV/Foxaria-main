# deploy/ — файлы развёртывания

- **`deploy.sh`** — сборка shadow JAR и выкладка на Linux-проде (`/opt/foxaria`), перезапуск через pm2.
- **`deploy.ps1`** + **`server-config.json`** — заливка сайта (`web/site`) на удалённый Windows-хост.
- **`ecosystem.config.js`** — pm2: `foxaria-proxy / foxaria-auth / foxaria-lobby / foxaria-test` + `foxaria-site-backend`. Пути ссылаются на `servers/*` и `web/site`.
- **`nginx-foxaria-site.conf`** — конфиг nginx для сайта.
