#!/bin/bash
# [VM] путь установки на хостинге — поправь cd под свой каталог
cd /opt/foxaria/servers/game

java -Xms2G -Xmx6G \
  -XX:+UseG1GC \
  -XX:+ParallelRefProcEnabled \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UnlockExperimentalVMOptions \
  -XX:+DisableExplicitGC \
  -jar paper-1.21.11.jar nogui
