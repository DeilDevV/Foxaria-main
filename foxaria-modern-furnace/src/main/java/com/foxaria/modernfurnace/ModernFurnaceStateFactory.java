package com.foxaria.modernfurnace;

import com.google.gson.Gson;

import java.util.ArrayList;

public final class ModernFurnaceStateFactory {

    private static final Gson GSON = new Gson();

    private ModernFurnaceStateFactory() {
    }

    public static PersistedFurnaceJson empty(boolean maxed, ModernFurnaceConfig cfg) {
        PersistedFurnaceJson j = new PersistedFurnaceJson();
        if (maxed && cfg != null) {
            j.speedLevel = cfg.maxSpeedLevel();
            j.fuelLevel = cfg.maxFuelLevel();
            j.outputLevel = cfg.maxOutputLevel();
            j.parallelLevel = cfg.maxParallelLevel();
            j.pipesUnlocked = true;
            j.donorMax = true;
        }
        j.lines = new ArrayList<>();
        j.outputStacksB64 = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            PersistedFurnaceJson.CookLineJson line = new PersistedFurnaceJson.CookLineJson();
            line.inputB64 = null;
            line.cookProgress = 0;
            j.lines.add(line);
            j.outputStacksB64.add(null);
        }
        j.fuelTicksRemaining = 0;
        j.fuelStackB64 = null;
        return j;
    }

    public static PersistedFurnaceJson normalize(PersistedFurnaceJson j) {
        if (j.lines == null) {
            j.lines = new ArrayList<>();
        }
        while (j.lines.size() < 4) {
            PersistedFurnaceJson.CookLineJson line = new PersistedFurnaceJson.CookLineJson();
            line.inputB64 = null;
            line.cookProgress = 0;
            j.lines.add(line);
        }
        if (j.outputStacksB64 == null) {
            j.outputStacksB64 = new ArrayList<>();
        }
        while (j.outputStacksB64.size() < 4) {
            j.outputStacksB64.add(null);
        }
        return j;
    }

    public static PersistedFurnaceJson fromItemBlob(String json) {
        if (json == null || json.isBlank()) {
            return empty(false, null);
        }
        try {
            return normalize(GSON.fromJson(json, PersistedFurnaceJson.class));
        } catch (Exception e) {
            return empty(false, null);
        }
    }
}
