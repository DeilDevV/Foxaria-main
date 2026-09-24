#!/bin/bash
# [VM] путь установки на хостинге — поправь cd под свой каталог
cd /opt/foxaria/servers/lobby

java -Xms512M -Xmx2G \
  -XX:+UseG1GC \
  -XX:+ParallelRefProcEnabled \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UnlockExperimentalVMOptions \
  -XX:+DisableExplicitGC \
  -jar paper.jar nogui
