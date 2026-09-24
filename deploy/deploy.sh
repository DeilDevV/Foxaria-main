#!/bin/bash
# Foxaria — сборка и деплой shadow JAR на Linux-проде (/opt/foxaria)
# Структура репозитория: modules/ — исходники, servers/ — игровые инстансы.
set -e

ROOT=/opt/foxaria
BUILD=$ROOT/.gradle-build
GAME=$ROOT/servers/game
LOBBY=$ROOT/servers/lobby
AUTH=$ROOT/servers/auth
PROXY=$ROOT/servers/proxy

cd "$ROOT"

echo '=== [1/4] Building shadow JARs ==='
./gradlew :foxaria-bootstrap:shadowJar :foxaria-hub-guard:shadowJar :foxaria-proxy-bungee:shadowJar   --no-daemon -q 2>&1 | tail -5

BOOT_JAR=$BUILD/foxaria-bootstrap/libs/foxaria-bootstrap-0.1.0-SNAPSHOT.jar
HUB_JAR=$BUILD/foxaria-hub-guard/libs/foxaria-hub-guard-0.1.0-SNAPSHOT.jar
PROXY_JAR=$BUILD/foxaria-proxy-bungee/libs/foxaria-proxy-bungee-0.1.0-SNAPSHOT.jar

echo '=== [2/4] Deploying JARs ==='
# game: только bootstrap shadow JAR
rm -f "$GAME"/plugins/*.jar
cp "$BOOT_JAR" "$GAME"/plugins/Foxaria.jar
echo "  game: Foxaria.jar ($(du -sh "$GAME"/plugins/Foxaria.jar | cut -f1))"

# lobby / auth: только hub-guard shadow JAR
rm -f "$LOBBY"/plugins/*.jar
cp "$HUB_JAR" "$LOBBY"/plugins/FoxariaHubGuard.jar
echo "  lobby: FoxariaHubGuard.jar"

rm -f "$AUTH"/plugins/*.jar
cp "$HUB_JAR" "$AUTH"/plugins/FoxariaHubGuard.jar
echo "  auth: FoxariaHubGuard.jar"

# proxy
rm -f "$PROXY"/plugins/FoxariaProxy.jar
cp "$PROXY_JAR" "$PROXY"/plugins/FoxariaProxy.jar
echo "  proxy: FoxariaProxy.jar"

echo '=== [3/4] Clearing Paper remapper caches ==='
rm -rf "$GAME"/plugins/.paper-remapped
rm -rf "$LOBBY"/plugins/.paper-remapped
rm -rf "$AUTH"/plugins/.paper-remapped

echo '=== [4/4] Restarting servers ==='
pm2 restart foxaria-auth foxaria-lobby foxaria-test foxaria-proxy
echo 'Done! All servers restarting.'
