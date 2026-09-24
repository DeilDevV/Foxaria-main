#!/bin/bash
# Foxaria Linux deploy script — builds and deploys only shadow JARs
set -e
ROOT=/opt/foxaria
BUILD=/.gradle-build

echo '=== [1/4] Building shadow JARs ==='
cd 
./gradlew :foxaria-bootstrap:shadowJar :foxaria-hub-guard:shadowJar :foxaria-proxy-bungee:shadowJar   --no-daemon -q 2>&1 | tail -5

BOOT_JAR=$BUILD/foxaria-bootstrap/libs/foxaria-bootstrap-0.1.0-SNAPSHOT.jar
HUB_JAR=$BUILD/foxaria-hub-guard/libs/foxaria-hub-guard-0.1.0-SNAPSHOT.jar
PROXY_JAR=$BUILD/foxaria-proxy-bungee/libs/foxaria-proxy-bungee-0.1.0-SNAPSHOT.jar

echo '=== [2/4] Deploying JARs ==='
# test-server: только bootstrap shadow JAR
rm -f $ROOT/test-server/plugins/*.jar
cp $BOOT_JAR $ROOT/test-server/plugins/Foxaria.jar
echo "  test-server: Foxaria.jar ($(du -sh $ROOT/test-server/plugins/Foxaria.jar | cut -f1))"

# lobby-server: только hub-guard shadow JAR
rm -f $ROOT/lobby-server/plugins/*.jar
cp $HUB_JAR $ROOT/lobby-server/plugins/FoxariaHubGuard.jar
echo "  lobby-server: FoxariaHubGuard.jar"

# auth-server: только hub-guard shadow JAR
rm -f $ROOT/auth-server/plugins/*.jar
cp $HUB_JAR $ROOT/auth-server/plugins/FoxariaHubGuard.jar
echo "  auth-server: FoxariaHubGuard.jar"

# proxy
rm -f $ROOT/proxy-bungeecord/plugins/FoxariaProxy.jar
cp $PROXY_JAR $ROOT/proxy-bungeecord/plugins/FoxariaProxy.jar
echo "  proxy: FoxariaProxy.jar"

echo '=== [3/4] Clearing Paper remapper caches ==='
rm -rf $ROOT/test-server/plugins/.paper-remapped
rm -rf $ROOT/lobby-server/plugins/.paper-remapped
rm -rf $ROOT/auth-server/plugins/.paper-remapped

echo '=== [4/4] Restarting servers ==='
pm2 restart foxaria-auth foxaria-lobby foxaria-test foxaria-proxy
echo 'Done! All servers restarting.'
