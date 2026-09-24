#!/bin/bash
# [VM] путь установки на хостинге — поправь cd под свой каталог
cd /opt/foxaria/servers/proxy

java -Xms512M -Xmx1G \
  -XX:+UseG1GC \
  -XX:+ParallelRefProcEnabled \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UnlockExperimentalVMOptions \
  -XX:+DisableExplicitGC \
  -jar BungeeCord.jar
