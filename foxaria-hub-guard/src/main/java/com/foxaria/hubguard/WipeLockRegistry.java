package com.foxaria.hubguard;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class WipeLockRegistry {

    private final Set<String> locked = ConcurrentHashMap.newKeySet();

    public boolean isLocked(String bungeeServerName) {
        return locked.contains(bungeeServerName);
    }

    /** Toggles lock state. Returns true if now locked, false if now unlocked. */
    public boolean toggle(String bungeeServerName) {
        if (locked.remove(bungeeServerName)) {
            return false;
        }
        locked.add(bungeeServerName);
        return true;
    }

    /** Sets lock state explicitly. Returns true if now locked. */
    public boolean set(String bungeeServerName, boolean lock) {
        if (lock) {
            locked.add(bungeeServerName);
        } else {
            locked.remove(bungeeServerName);
        }
        return lock;
    }

    /** Replaces entire registry state with the given set of locked server names. */
    public void syncAll(Set<String> lockedServers) {
        locked.clear();
        if (lockedServers != null) {
            locked.addAll(lockedServers);
        }
    }
}
