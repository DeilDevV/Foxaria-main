#!/bin/bash
cd /opt/foxaria/auth-server

java -Xms512M -Xmx2G \
  -XX:+UseG1GC \
  -XX:+ParallelRefProcEnabled \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UnlockExperimentalVMOptions \
  -XX:+DisableExplicitGC \
  -jar paper.jar nogui
