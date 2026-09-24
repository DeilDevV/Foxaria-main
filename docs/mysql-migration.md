# Foxaria MySQL migration

1. Настрой одну общую MySQL базу и укажи одинаковые параметры в:
   - `foxaria-bootstrap/src/main/resources/config.yml`
   - `foxaria-proxy-bungee/src/main/resources/config.yml`
   - `foxaria-hub-guard/src/main/resources/config.yml`

2. Собери проект:

```powershell
.\gradlew.bat build
```

3. Сгенерируй SQL-дамп из текущих SQLite баз:

```powershell
python .\scripts\sqlite_to_mysql_export.py `
  --main-sqlite .\test-server\plugins\Foxaria\foxaria.db `
  --proxy-sqlite .\proxy-bungeecord\plugins\FoxariaProxy\foxaria-proxy.db `
  --out .\scripts\generated\foxaria_mysql_dump.sql `
  --database foxaria
```

4. Импортируй дамп в MySQL:

```powershell
mysql -u foxaria -p foxaria < .\scripts\generated\foxaria_mysql_dump.sql
```

Или через готовый PowerShell-скрипт:

```powershell
.\scripts\import_mysql_dump.ps1 `
  -Host localhost `
  -Port 3306 `
  -Database foxaria `
  -User foxaria `
  -Password 'your-password'
```

5. После импорта запусти Paper и Bungee уже с `database.type: mysql`.

Примечания:

- `sqlite-file` поля в конфиге оставлены только как fallback для старых локальных сценариев миграции.
- Основной Foxaria на Paper теперь читает `proxy_privileges` из общей БД, а не из отдельного `foxaria-proxy.db`.
- `FoxariaHubGuard` тоже умеет подключаться к MySQL напрямую через `rank-bridge`.
