package com.foxaria.cases.service;

import com.foxaria.cases.model.CaseLocation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CaseOpenLockService {

    private final Map<String, UUID> locks = new ConcurrentHashMap<>();

    public boolean tryLock(CaseLocation location, UUID playerId) {
        String key = location.key();
        UUID existing = locks.putIfAbsent(key, playerId);
        return existing == null || existing.equals(playerId);
    }

    public boolean isLockedByOther(CaseLocation location, UUID playerId) {
        UUID owner = locks.get(location.key());
        return owner != null && !owner.equals(playerId);
    }

    public void unlock(CaseLocation location) {
        locks.remove(location.key());
    }

    public void unlock(CaseLocation location, UUID playerId) {
        locks.computeIfPresent(location.key(), (key, owner) -> owner.equals(playerId) ? null : owner);
    }
}
