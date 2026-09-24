#!/bin/bash
# Foxaria — реструктуризация репозитория. Запускать ИЗ КОРНЯ клона Foxaria-main: bash apply-restructure.sh
set -euo pipefail
KIT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

[[ -f settings.gradle && -f build.gradle ]] || { echo "Запусти из корня репозитория Foxaria-main"; exit 1; }
[[ -d .git ]] || { echo "Нужен git-клон (нет .git)"; exit 1; }

mv_if_exists() {
  local src="$1" dst="$2"
  [[ -e "$dst" ]] && { echo "  skip (есть): $dst"; return; }
  [[ -e "$src" ]] || { echo "  skip (нет): $src"; return; }
  mkdir -p "$(dirname "$dst")"
  git mv "$src" "$dst"
  echo "  $src -> $dst"
}

echo '[1/4] Переношу папки (git mv)...'
mv_if_exists modules/quests.yml config/gameplay/quests.yml
for d in foxaria-*/; do d="${d%/}"; mv_if_exists "$d" "modules/$d"; done
mv_if_exists auth-server servers/auth
mv_if_exists lobby-server servers/lobby
mv_if_exists test-server servers/game
mv_if_exists proxy-bungeecord servers/proxy
mv_if_exists production-server-template servers/production-template
mv_if_exists SITE-FOXARIA web/site
mv_if_exists deploy.sh deploy/deploy.sh
mv_if_exists ecosystem.config.js deploy/ecosystem.config.js

echo '[2/4] Удаляю .playwright-mcp...'
[[ -d .playwright-mcp ]] && { git rm -r -q --cached .playwright-mcp 2>/dev/null || true; rm -rf .playwright-mcp; }

echo '[3/4] Обновляю файлы конфигурации...'
cp -Rf "$KIT/files/." .

echo '[4/4] Готово. Дальше: git status -> ./gradlew :foxaria-bootstrap:shadowJar -> up.cmd (на Windows)'
