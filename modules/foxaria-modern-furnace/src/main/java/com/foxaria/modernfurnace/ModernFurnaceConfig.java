package com.foxaria.modernfurnace;

import org.bukkit.configuration.file.FileConfiguration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Читает modules/modern-furnace.yml — все множители и цены настраиваются без кода.
 */
public final class ModernFurnaceConfig {

    private final double[] cookTimeFactors;
    private final double[] fuelDurationMultipliers;
    private final int[] outputMultipliers;
    private final int[] parallelLanes;
    private final BigDecimal[] priceSpeed;
    private final BigDecimal[] priceFuel;
    private final BigDecimal[] priceOutput;
    private final BigDecimal[] priceParallel;
    private final BigDecimal pricePipesUnlock;
    private final int guiRefreshIntervalTicks;

    public ModernFurnaceConfig(FileConfiguration c) {
        this.guiRefreshIntervalTicks = Math.max(1, c.getInt("gui.refresh-interval-ticks", 4));
        this.cookTimeFactors = dbl(c, "balance.cook-time-factors", 1.0, 0.84, 0.68, 0.52, 0.36);
        this.fuelDurationMultipliers = dbl(c, "balance.fuel-duration-multipliers", 1.0, 2.0, 3.0, 4.0, 5.0);
        this.outputMultipliers = intr(c, "balance.output-multipliers", 1, 2, 3, 4);
        this.parallelLanes = intr(c, "balance.parallel-lanes", 1, 2, 3, 4);
        this.priceSpeed = money(c, "prices.speed", 500, 1200, 2500, 5000);
        this.priceFuel = money(c, "prices.fuel", 500, 1200, 2500, 5000);
        this.priceOutput = money(c, "prices.output", 800, 2000, 4500);
        this.priceParallel = money(c, "prices.parallel", 1500, 4000, 8000);
        this.pricePipesUnlock = BigDecimal.valueOf(c.getDouble("prices.pipes-unlock", 10000));
    }

    private static double[] dbl(FileConfiguration c, String path, double... def) {
        List<Double> list = c.getDoubleList(path);
        if (list == null || list.isEmpty()) {
            return def;
        }
        double[] out = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            out[i] = list.get(i);
        }
        return out;
    }

    private static int[] intr(FileConfiguration c, String path, int... def) {
        List<Integer> list = c.getIntegerList(path);
        if (list == null || list.isEmpty()) {
            return def;
        }
        int[] out = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            out[i] = list.get(i);
        }
        return out;
    }

    private static BigDecimal[] money(FileConfiguration c, String path, double... def) {
        List<?> raw = c.getList(path);
        if (raw == null || raw.isEmpty()) {
            BigDecimal[] b = new BigDecimal[def.length];
            for (int i = 0; i < def.length; i++) {
                b[i] = BigDecimal.valueOf(def[i]);
            }
            return b;
        }
        BigDecimal[] b = new BigDecimal[raw.size()];
        for (int i = 0; i < raw.size(); i++) {
            b[i] = new BigDecimal(raw.get(i).toString());
        }
        return b;
    }

    public int guiRefreshIntervalTicks() {
        return guiRefreshIntervalTicks;
    }

    public double cookTimeFactor(int speedLevel) {
        return cookTimeFactors[Math.min(speedLevel, cookTimeFactors.length - 1)];
    }

    public double fuelDurationMultiplier(int fuelLevel) {
        return fuelDurationMultipliers[Math.min(fuelLevel, fuelDurationMultipliers.length - 1)];
    }

    public int outputMultiplier(int outputLevel) {
        return outputMultipliers[Math.min(outputLevel, outputMultipliers.length - 1)];
    }

    public int parallelLanes(int parallelLevel) {
        return parallelLanes[Math.min(parallelLevel, parallelLanes.length - 1)];
    }

    public int maxSpeedLevel() {
        return cookTimeFactors.length - 1;
    }

    public int maxFuelLevel() {
        return fuelDurationMultipliers.length - 1;
    }

    public int maxOutputLevel() {
        return outputMultipliers.length - 1;
    }

    public int maxParallelLevel() {
        return parallelLanes.length - 1;
    }

    /** Цена перехода speed: currentLevel → currentLevel+1 */
    public BigDecimal priceSpeedUpgrade(int currentLevel) {
        if (currentLevel < 0 || currentLevel >= priceSpeed.length) {
            return null;
        }
        return priceSpeed[currentLevel];
    }

    public BigDecimal priceFuelUpgrade(int currentLevel) {
        if (currentLevel < 0 || currentLevel >= priceFuel.length) {
            return null;
        }
        return priceFuel[currentLevel];
    }

    public BigDecimal priceOutputUpgrade(int currentLevel) {
        if (currentLevel < 0 || currentLevel >= priceOutput.length) {
            return null;
        }
        return priceOutput[currentLevel];
    }

    public BigDecimal priceParallelUpgrade(int currentLevel) {
        if (currentLevel < 0 || currentLevel >= priceParallel.length) {
            return null;
        }
        return priceParallel[currentLevel];
    }

    public BigDecimal pricePipesUnlock() {
        return pricePipesUnlock;
    }

    /** Процент времени плавки относительно ваниллы (для текста). */
    public int speedPercentOfVanilla(int speedLevel) {
        return (int) Math.round(100.0 * cookTimeFactor(speedLevel));
    }

    /** На сколько % быстрее базы (100 − factor×100). */
    public int speedBonusPercent(int speedLevel) {
        return Math.max(0, 100 - speedPercentOfVanilla(speedLevel));
    }

    /** Примерная «экономия топлива» % при многослойном горении (1 − 1/mult). */
    public int fuelSavingsApproxPercent(int fuelLevel) {
        double m = fuelDurationMultiplier(fuelLevel);
        if (m <= 1.0) {
            return 0;
        }
        return (int) Math.round((1.0 - 1.0 / m) * 100.0);
    }

    public String formatMoney(BigDecimal v) {
        if (v == null) {
            return "—";
        }
        return v.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /** Ограничить уровни при загрузке из БД / предмета, если конфиг укоротили. */
    public void clampLevels(PersistedFurnaceJson j) {
        if (j == null) {
            return;
        }
        j.speedLevel = clamp(j.speedLevel, 0, maxSpeedLevel());
        j.fuelLevel = clamp(j.fuelLevel, 0, maxFuelLevel());
        j.outputLevel = clamp(j.outputLevel, 0, maxOutputLevel());
        j.parallelLevel = clamp(j.parallelLevel, 0, maxParallelLevel());
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.min(hi, Math.max(lo, v));
    }
}
