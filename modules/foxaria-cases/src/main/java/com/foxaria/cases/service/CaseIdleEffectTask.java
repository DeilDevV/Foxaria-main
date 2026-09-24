package com.foxaria.cases.service;

import com.foxaria.cases.model.CaseDefinition;
import com.foxaria.cases.model.CaseLocation;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;

public final class CaseIdleEffectTask implements Runnable {

    private final CaseLocation location;
    private final CaseDefinition definition;
    private final ConfigCaseService config;
    private final World world;
    private double angle;

    public CaseIdleEffectTask(
        CaseLocation location,
        CaseDefinition definition,
        ConfigCaseService config,
        World world
    ) {
        this.location = location;
        this.definition = definition;
        this.config = config;
        this.world = world;
    }

    @Override
    public void run() {
        if (world == null) {
            return;
        }
        double radius = config.idleEffectRadius();
        double centerX = location.x() + 0.5D;
        double centerY = location.y() + 1.0D;
        double centerZ = location.z() + 0.5D;
        Particle particle = definition.idleEffects().particle();
        Particle.DustOptions dust = dustOptions(definition.idleEffects().colorHex());
        String type = definition.idleEffects().type().toLowerCase(Locale.ROOT);
        angle += 0.18D;
        switch (type) {
            case "ring" -> spawnRing(particle, dust, centerX, centerY, centerZ, radius, angle);
            case "helix" -> spawnHelix(particle, dust, centerX, centerY, centerZ, radius, angle);
            default -> spawnSpiral(particle, dust, centerX, centerY, centerZ, radius, angle);
        }
    }

    private void spawnSpiral(Particle particle, Particle.DustOptions dust, double cx, double cy, double cz, double radius, double baseAngle) {
        for (int i = 0; i < 3; i++) {
            double a = baseAngle + i * (Math.PI * 2 / 3);
            double y = cy + Math.sin(baseAngle * 2 + i) * 0.6D;
            spawnPoint(particle, dust, cx + Math.cos(a) * radius, y, cz + Math.sin(a) * radius);
        }
    }

    private void spawnRing(Particle particle, Particle.DustOptions dust, double cx, double cy, double cz, double radius, double baseAngle) {
        for (int i = 0; i < 8; i++) {
            double a = baseAngle + i * (Math.PI / 4);
            spawnPoint(particle, dust, cx + Math.cos(a) * radius, cy, cz + Math.sin(a) * radius);
        }
    }

    private void spawnHelix(Particle particle, Particle.DustOptions dust, double cx, double cy, double cz, double radius, double baseAngle) {
        for (int i = 0; i < 6; i++) {
            double a = baseAngle + i * 0.9D;
            double y = cy + (i * 0.25D) - 0.6D;
            spawnPoint(particle, dust, cx + Math.cos(a) * radius, y, cz + Math.sin(a) * radius);
        }
    }

    private void spawnPoint(Particle particle, Particle.DustOptions dust, double x, double y, double z) {
        Location at = new Location(world, x, y, z);
        if (dust != null && particle.getDataType() == Particle.DustOptions.class) {
            world.spawnParticle(particle, at, 1, 0, 0, 0, 0, dust);
        } else {
            world.spawnParticle(particle, at, 1, 0, 0, 0, 0);
        }
    }

    private Particle.DustOptions dustOptions(String hex) {
        if (hex == null || !hex.startsWith("#") || hex.length() != 7) {
            return new Particle.DustOptions(Color.WHITE, 1.0F);
        }
        try {
            int rgb = Integer.parseInt(hex.substring(1), 16);
            return new Particle.DustOptions(Color.fromRGB(rgb), 1.1F);
        } catch (NumberFormatException ignored) {
            return new Particle.DustOptions(Color.WHITE, 1.0F);
        }
    }

    public static BukkitTask start(
        org.bukkit.plugin.java.JavaPlugin plugin,
        CaseLocation location,
        CaseDefinition definition,
        ConfigCaseService config,
        World world
    ) {
        return plugin.getServer().getScheduler().runTaskTimer(
            plugin,
            new CaseIdleEffectTask(location, definition, config, world),
            0L,
            2L
        );
    }
}
