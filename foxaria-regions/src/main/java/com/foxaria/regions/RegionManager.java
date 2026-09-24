package com.foxaria.regions;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RegionManager {

    private final List<RegionRecord> regions = new CopyOnWriteArrayList<>();
    /** {@code < 0} — низ региона до минимальной высоты мира. */
    private int blocksDown = 100;
    private int blocksUp = 100;

    public void setVerticalBounds(int down, int up) {
        this.blocksDown = down;
        this.blocksUp = Math.max(0, up);
    }

    public int blocksDown() {
        return blocksDown;
    }

    public int blocksUp() {
        return blocksUp;
    }

    public void replaceAll(List<RegionRecord> list) {
        regions.clear();
        regions.addAll(list);
    }

    public void add(RegionRecord r) {
        regions.add(r);
    }

    public void remove(int id) {
        regions.removeIf(r -> r.id() == id);
    }

    public List<RegionRecord> snapshot() {
        return List.copyOf(regions);
    }

    public Optional<RegionRecord> findContaining(Location loc) {
        if (loc.getWorld() == null) {
            return Optional.empty();
        }
        int worldMinY = loc.getWorld().getMinHeight();
        for (RegionRecord r : regions) {
            if (r.contains(loc, r.halfSize(), blocksDown, blocksUp, worldMinY)) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    /** Новое ядро (ещё не в списке) пересекается с любым существующим регионом. */
    public boolean overlapsNewCore(World world, int cx, int cz, int coreFootY, int half) {
        int worldMinY = world.getMinHeight();
        String wname = world.getName();
        for (RegionRecord r : regions) {
            if (RegionRecord.newCoreOverlaps(wname, cx, cz, coreFootY, half, r, blocksDown, blocksUp, worldMinY)) {
                return true;
            }
        }
        return false;
    }

    /** После расширения до newHalf регион self пересечётся с другим (текущие размеры соседа). */
    public boolean wouldBlockUpgrade(RegionRecord self, int newHalf) {
        World w = Bukkit.getWorld(self.world());
        if (w == null) {
            return false;
        }
        int worldMinY = w.getMinHeight();
        for (RegionRecord o : regions) {
            if (o.id() == self.id()) {
                continue;
            }
            if (self.overlapsWith(o, newHalf, o.halfSize(), blocksDown, blocksUp, worldMinY)) {
                return true;
            }
        }
        return false;
    }

    public Optional<RegionRecord> byId(int id) {
        for (RegionRecord r : regions) {
            if (r.id() == id) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    public void patchCore(int id, int hp, long lastDmg) {
        for (int i = 0; i < regions.size(); i++) {
            RegionRecord r = regions.get(i);
            if (r.id() == id) {
                regions.set(i, new RegionRecord(
                    r.id(), r.world(), r.centerX(), r.centerZ(), r.halfSize(), r.level(), r.ownerUuid(),
                    r.cabinetX(), r.cabinetY(), r.cabinetZ(),
                    hp, r.coreMaxHp(), lastDmg,
                    r.depositedWood(), r.depositedIron(), r.flags(),
                    r.displayName()
                ));
                return;
            }
        }
    }

    public void patchDeposits(int id, int wood, int iron) {
        for (int i = 0; i < regions.size(); i++) {
            RegionRecord r = regions.get(i);
            if (r.id() == id) {
                regions.set(i, new RegionRecord(
                    r.id(), r.world(), r.centerX(), r.centerZ(), r.halfSize(), r.level(), r.ownerUuid(),
                    r.cabinetX(), r.cabinetY(), r.cabinetZ(),
                    r.coreHp(), r.coreMaxHp(), r.coreLastDamageMs(),
                    wood, iron, r.flags(),
                    r.displayName()
                ));
                return;
            }
        }
    }

    public void patchLevelAndSize(int id, int half, int level) {
        for (int i = 0; i < regions.size(); i++) {
            RegionRecord r = regions.get(i);
            if (r.id() == id) {
                regions.set(i, new RegionRecord(
                    r.id(), r.world(), r.centerX(), r.centerZ(), half, level, r.ownerUuid(),
                    r.cabinetX(), r.cabinetY(), r.cabinetZ(),
                    r.coreHp(), r.coreMaxHp(), r.coreLastDamageMs(),
                    0, 0, r.flags(),
                    r.displayName()
                ));
                return;
            }
        }
    }

    public void replace(RegionRecord r) {
        for (int i = 0; i < regions.size(); i++) {
            if (regions.get(i).id() == r.id()) {
                regions.set(i, r);
                return;
            }
        }
    }

    public void patchFlags(int id, int flags) {
        for (int i = 0; i < regions.size(); i++) {
            RegionRecord r = regions.get(i);
            if (r.id() == id) {
                regions.set(i, new RegionRecord(
                    r.id(), r.world(), r.centerX(), r.centerZ(), r.halfSize(), r.level(), r.ownerUuid(),
                    r.cabinetX(), r.cabinetY(), r.cabinetZ(),
                    r.coreHp(), r.coreMaxHp(), r.coreLastDamageMs(),
                    r.depositedWood(), r.depositedIron(), flags,
                    r.displayName()
                ));
                return;
            }
        }
    }

    public void patchDisplayName(int id, String displayName) {
        for (int i = 0; i < regions.size(); i++) {
            RegionRecord r = regions.get(i);
            if (r.id() == id) {
                regions.set(i, new RegionRecord(
                    r.id(), r.world(), r.centerX(), r.centerZ(), r.halfSize(), r.level(), r.ownerUuid(),
                    r.cabinetX(), r.cabinetY(), r.cabinetZ(),
                    r.coreHp(), r.coreMaxHp(), r.coreLastDamageMs(),
                    r.depositedWood(), r.depositedIron(), r.flags(),
                    displayName
                ));
                return;
            }
        }
    }

    public Optional<RegionRecord> findCoreAt(Location loc) {
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        String w = loc.getWorld().getName();
        for (RegionRecord r : regions) {
            if (!r.world().equals(w)) {
                continue;
            }
            if (r.isCoreBlock(x, y, z)) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }
}
