package com.foxaria.regions.listener;

import com.foxaria.regions.RegionConfig;
import com.foxaria.regions.RegionManager;
import com.foxaria.regions.RegionRecord;
import com.foxaria.regions.RegionRepository;
import com.foxaria.regions.RegionRepository.DamagedBlockRow;
import com.foxaria.regions.RegionRepository.DamagedRowWithRegion;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public final class RegionRegenTask extends BukkitRunnable {

    private final JavaPlugin plugin;
    private final RegionConfig config;
    private final RegionRepository repo;
    private final RegionManager manager;

    public RegionRegenTask(JavaPlugin plugin, RegionConfig config, RegionRepository repo, RegionManager manager) {
        this.plugin = plugin;
        this.config = config;
        this.repo = repo;
        this.manager = manager;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        long idle = config.regenIdleBeforeMs;
        int tickAmt = config.regenAmountPerTick;

        for (RegionRecord r : manager.snapshot()) {
            if (r.coreHp() >= r.coreMaxHp()) {
                continue;
            }
            if (now - r.coreLastDamageMs() < idle) {
                continue;
            }
            int nh = Math.min(r.coreMaxHp(), r.coreHp() + tickAmt);
            if (nh != r.coreHp()) {
                repo.updateCore(r.id(), nh, r.coreLastDamageMs()).join();
                manager.patchCore(r.id(), nh, r.coreLastDamageMs());
            }
        }

        List<DamagedRowWithRegion> rows = repo.loadAllDamaged().join();
        for (DamagedRowWithRegion wrap : rows) {
            DamagedBlockRow row = wrap.row();
            if (now - row.lastDamageMs() < idle) {
                continue;
            }
            if (row.currentHp() >= row.maxHp()) {
                repo.deleteDamagedBlock(wrap.regionId(), row.x(), row.y(), row.z()).join();
                continue;
            }
            manager.byId(wrap.regionId()).ifPresent(regionRec -> {
                World w = plugin.getServer().getWorld(regionRec.world());
                if (w == null) {
                    return;
                }
                Block b = w.getBlockAt(row.x(), row.y(), row.z());
                try {
                    Material stored = Material.valueOf(row.material());
                    if (b.getType() != stored) {
                        repo.deleteDamagedBlock(wrap.regionId(), row.x(), row.y(), row.z()).join();
                        return;
                    }
                } catch (IllegalArgumentException e) {
                    repo.deleteDamagedBlock(wrap.regionId(), row.x(), row.y(), row.z()).join();
                    return;
                }
                int nh = Math.min(row.maxHp(), row.currentHp() + tickAmt);
                long last = row.lastDamageMs();
                if (nh != row.currentHp()) {
                    repo.upsertDamagedBlock(wrap.regionId(), new DamagedBlockRow(
                        row.x(), row.y(), row.z(), row.material(), nh, row.maxHp(), last
                    )).join();
                }
            });
        }
    }
}
