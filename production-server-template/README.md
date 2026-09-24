# Foxaria Production Server Template

Р­С‚Р° РїР°РїРєР° РїРѕРґРіРѕС‚РѕРІР»РµРЅР° РєР°Рє production-С€Р°Р±Р»РѕРЅ РїРѕРґ Foxaria РЅР° `Paper 1.21.11`.

## Р§С‚Рѕ СѓР¶Рµ РЅР°СЃС‚СЂРѕРµРЅРѕ

- `online-mode=true`
- `environment: prod`
- Foxaria РєРѕРїРёСЂСѓРµС‚СЃСЏ РІ `plugins/Foxaria.jar`
- СЃРѕР·РґР°С‘С‚СЃСЏ production `plugins/Foxaria/config.yml`
- РїРѕРґС…РІР°С‚С‹РІР°СЋС‚СЃСЏ СЂСѓСЃСЃРєРёРµ `messages.yml` Рё `modules/*.yml`

## Р§С‚Рѕ РѕР±СЏР·Р°С‚РµР»СЊРЅРѕ РїРѕРјРµРЅСЏС‚СЊ РїРµСЂРµРґ СЂРµР»РёР·РѕРј

1. Р’ `plugins/Foxaria/config.yml` СѓРєР°Р¶Рё СЂРµР°Р»СЊРЅС‹Рµ РґР°РЅРЅС‹Рµ MySQL:
   - `database.host`
   - `database.port`
   - `database.name`
   - `database.username`
   - `database.password`
2. РџРѕРґРїРёС€Рё EULA РІ `eula.txt`
3. РќР°СЃС‚СЂРѕР№ `server.properties` РїРѕРґ Р»РёРјРёС‚С‹ С…РѕСЃС‚Р°
4. Р’С‹РґР°Р№ СЃРµР±Рµ Р°РґРјРёРЅ-РїСЂР°РІР° РїРѕСЃР»Рµ РїРµСЂРІРѕРіРѕ РІС…РѕРґР°:
   - `op <РЅРёРє>`
   - `rank set <РЅРёРє> admin`

## Р’Р°Р¶РЅРѕ

- Р”Р»СЏ production РЅРµ РѕСЃС‚Р°РІР»СЏР№ SQLite
- Р”Р»СЏ РїСѓР±Р»РёС‡РЅРѕРіРѕ СЃРµСЂРІРµСЂР° РЅРµ РІС‹РєР»СЋС‡Р°Р№ `online-mode`
- Р•СЃР»Рё РёСЃРїРѕР»СЊР·СѓРµС€СЊ reverse proxy, РЅР°СЃС‚СЂР°РёРІР°Р№ Paper/Velocity РѕС‚РґРµР»СЊРЅРѕ
