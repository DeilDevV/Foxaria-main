package com.foxaria.modernfurnace;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;

public final class ModernFurnaceParticles {

    private ModernFurnaceParticles() {
    }

    public static void start(JavaPlugin plugin, ModernFurnaceService service) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<String, PersistedFurnaceJson> e : service.snapshotCache().entrySet()) {
                    Location loc = ModernFurnaceService.parseKey(e.getKey());
                    PersistedFurnaceJson d = e.getValue();
                    if (loc == null || loc.getWorld() == null || !d.pipesUnlocked) {
                        continue;
                    }
                    World w = loc.getWorld();
                    Location a = loc.clone().add(0.5, 0.7, 0.5);
                    if (d.inputChestX != null && d.inputChestWorld != null) {
                        Location b = ModernFurnaceEngine.chestWorldLoc(d.inputChestWorld, d.inputChestX, d.inputChestY, d.inputChestZ);
                        if (b != null) {
                            line(w, a, b.clone().add(0.5, 0.5, 0.5), Color.fromRGB(200, 40, 40));
                        }
                    }
                    if (d.outputChestX != null && d.outputChestWorld != null) {
                        Location b = ModernFurnaceEngine.chestWorldLoc(d.outputChestWorld, d.outputChestX, d.outputChestY, d.outputChestZ);
                        if (b != null) {
                            line(w, a, b.clone().add(0.5, 0.5, 0.5), Color.fromRGB(60, 200, 80));
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 40L, 40L);
    }

    private static void line(World w, Location from, Location to, Color color) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double len = Math.max(0.1, Math.sqrt(dx * dx + dy * dy + dz * dz));
        int steps = (int) Math.min(32, Math.ceil(len * 2));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double x = from.getX() + dx * t;
            double y = from.getY() + dy * t;
            double z = from.getZ() + dz * t;
            w.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0,
                new Particle.DustOptions(color, 0.85f));
        }
    }
}
