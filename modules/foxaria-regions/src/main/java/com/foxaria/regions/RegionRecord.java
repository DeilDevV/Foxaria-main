package com.foxaria.regions;

import org.bukkit.Location;

import java.util.UUID;

public record RegionRecord(
    int id,
    String world,
    int centerX,
    int centerZ,
    int halfSize,
    int level,
    UUID ownerUuid,
    int cabinetX,
    int cabinetY,
    int cabinetZ,
    int coreHp,
    int coreMaxHp,
    long coreLastDamageMs,
    int depositedWood,
    int depositedIron,
    int flags,
    /** Имя для boss bar; {@code null} — показывать «Приват №id». */
    String displayName
) {

    public String effectiveDisplayTitle() {
        if (displayName != null && !displayName.isBlank()) {
            return displayName.trim();
        }
        return "Приват №" + id;
    }

    /**
     * Точка привязки по Y — нижний блок ядра (стол кузницы).
     * {@code blocksDown < 0} — низ региона до {@code worldMinY} (дно мира).
     */
    public boolean contains(Location loc, int half, int blocksDown, int blocksUp, int worldMinY) {
        if (loc.getWorld() == null || !loc.getWorld().getName().equals(world)) {
            return false;
        }
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        if (Math.abs(x - centerX) > half || Math.abs(z - centerZ) > half) {
            return false;
        }
        int minY = blocksDown < 0 ? worldMinY : cabinetY - blocksDown;
        int maxY = cabinetY + blocksUp;
        return y >= minY && y <= maxY;
    }

    /** Пересечение объёмов привата (по XZ: расстояние центров меньше суммы полуразмеров). */
    public boolean overlapsWith(RegionRecord other, int myHalf, int otherHalf, int blocksDown, int blocksUp, int worldMinY) {
        if (!world.equals(other.world())) {
            return false;
        }
        if (Math.abs(centerX - other.centerX()) >= myHalf + otherHalf) {
            return false;
        }
        if (Math.abs(centerZ - other.centerZ()) >= myHalf + otherHalf) {
            return false;
        }
        int sMinY = blocksDown < 0 ? worldMinY : cabinetY - blocksDown;
        int sMaxY = cabinetY + blocksUp;
        int oMinY = blocksDown < 0 ? worldMinY : other.cabinetY() - blocksDown;
        int oMaxY = other.cabinetY() + blocksUp;
        return sMaxY >= oMinY && sMinY <= oMaxY;
    }

    public static boolean newCoreOverlaps(
        String world,
        int cx,
        int cz,
        int coreFootY,
        int half,
        RegionRecord existing,
        int blocksDown,
        int blocksUp,
        int worldMinY
    ) {
        if (!world.equals(existing.world())) {
            return false;
        }
        if (Math.abs(cx - existing.centerX()) >= half + existing.halfSize()) {
            return false;
        }
        if (Math.abs(cz - existing.centerZ()) >= half + existing.halfSize()) {
            return false;
        }
        int nMinY = blocksDown < 0 ? worldMinY : coreFootY - blocksDown;
        int nMaxY = coreFootY + blocksUp;
        int oMinY = blocksDown < 0 ? worldMinY : existing.cabinetY() - blocksDown;
        int oMaxY = existing.cabinetY() + blocksUp;
        return nMaxY >= oMinY && nMinY <= oMaxY;
    }

    public boolean isCoreBlock(int bx, int by, int bz) {
        return bx == cabinetX && bz == cabinetZ && (by == cabinetY || by == cabinetY + 1);
    }
}
