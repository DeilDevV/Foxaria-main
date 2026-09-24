package com.foxaria.modernfurnace;

import java.util.ArrayList;
import java.util.List;

/** Снимок для Gson (поля public). */
@SuppressWarnings("unused")
public final class PersistedFurnaceJson {

    public int speedLevel;
    public int fuelLevel;
    public int outputLevel;
    public int parallelLevel;
    public boolean pipesUnlocked;
    /** Печь выдана админом с макс. характеристиками (для донат-витрины). */
    public boolean donorMax;

    /** Последний игрок, открывавший GUI печи (для бонусов плавки по знаниям). */
    public String lastSmelterUuid;

    public String inputChestWorld;
    public Integer inputChestX;
    public Integer inputChestY;
    public Integer inputChestZ;

    public String outputChestWorld;
    public Integer outputChestX;
    public Integer outputChestY;
    public Integer outputChestZ;

    public String fuelStackB64;
    public int fuelTicksRemaining;

    public List<CookLineJson> lines = new ArrayList<>();
    public List<String> outputStacksB64 = new ArrayList<>();

    public static final class CookLineJson {
        public String inputB64;
        public int cookProgress;
    }
}
