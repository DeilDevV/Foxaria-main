# Production Rollout Checklist

## Платформа
- Java 21
- Paper `1.21.11`
- один плагин: `Foxaria.jar`
- `online-mode=true` для боевого сервера

## База данных
- переключить `config.yml -> environment` на `prod`
- в production использовать MySQL или PostgreSQL
- настроить автоматические бэкапы БД до первого публичного запуска
- сначала прогнать миграции на staging-базе

## Перед открытием
- проверить спавн, RTP, дома и TPA
- проверить магазин, аукцион и магазины игроков
- проверить выдачу кейсов и одноразовых наград
- проверить `/report`, `/freeze`, `/ban`, `/mute`, `/modpanel`, `/adminpanel`
- проверить донат-пакеты из `modules/store.yml`
- убедиться, что конфиги `messages.yml` и `modules/*.yml` соответствуют боевым правилам проекта

## Безопасность
- настроить `modules/security.yml` под реальную нагрузку сервера
- настроить `modules/moderation.yml` anti-bot лимиты под вашу сеть/хостинг
- проверить ограничения спавна и правило по крыше Незера
- прогнать staging-сценарии на дюпы, спам контейнеров и rate limit команд

## Операционка
- держать `.\gradlew.bat build` зелёным в CI
- деплоить артефакт `foxaria-bootstrap/build/libs/foxaria-bootstrap-0.1.0-SNAPSHOT.jar`
- мониторить аудит-таблицы и `fx_tebex_fulfillments`
- ротировать логи и бэкапы БД по расписанию
- публиковать по умолчанию только EULA-safe пакеты
