package com.foxaria.regions.listener;

import com.foxaria.regions.RegionFacade;
import com.foxaria.regions.RegionFlags;
import com.foxaria.regions.RegionRecord;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Optional;

/**
 * Частицы вдоль ближайшей к игроку границы привата — только для участников,
 * если включено владельцем и не выключено у игрока.
 */
public final class RegionBoundaryParticleTask extends BukkitRunnable {

    private static final int MAX_EDGE_DIST = 96;
    private static final int STRIPE_HALF = 24;
    private static final int STEP = 5;

    private final RegionFacade f;

    public RegionBoundaryParticleTask(RegionFacade f) {
        this.f = f;
    }

    @Override
    public void run() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            tickPlayer(p);
        }
    }

    private void tickPlayer(Player p) {
        Location loc = p.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        Optional<RegionRecord> regO = f.manager().findContaining(loc);
        if (regO.isEmpty()) {
            return;
        }
        RegionRecord r = regO.get();
        if (!r.world().equals(world.getName())) {
            return;
        }
        if (!f.repo().isMember(r.id(), p.getUniqueId()).join()) {
            return;
        }
        if (!RegionFlags.boundaryParticles(r.flags())) {
            return;
        }
        if (f.repo().hideBoundaryParticles(r.id(), p.getUniqueId()).join()) {
            return;
        }

        int H = r.halfSize();
        int cx = r.centerX();
        int cz = r.centerZ();
        int x1 = cx - H;
        int x2 = cx + H;
        int z1 = cz - H;
        int z2 = cz + H;

        int pbx = loc.getBlockX();
        int pbz = loc.getBlockZ();
        double py = loc.getY() + 0.2;

        int dLeft = pbx - x1;
        int dRight = x2 - pbx;
        int dSouth = pbz - z1;
        int dNorth = z2 - pbz;

        int minD = Math.min(Math.min(dLeft, dRight), Math.min(dSouth, dNorth));
        if (minD > MAX_EDGE_DIST) {
            return;
        }

        if (minD == dLeft) {
            spawnAlongZ(world, x1, py, z1, z2, pbz);
        } else if (minD == dRight) {
            spawnAlongZ(world, x2, py, z1, z2, pbz);
        } else if (minD == dSouth) {
            spawnAlongX(world, z1, py, x1, x2, pbx);
        } else {
            spawnAlongX(world, z2, py, x1, x2, pbx);
        }
    }

    private static void spawnAlongZ(World world, int x, double y, int zMin, int zMax, int pz) {
        int z0 = Math.max(zMin, pz - STRIPE_HALF);
        int z1 = Math.min(zMax, pz + STRIPE_HALF);
        for (int z = z0; z <= z1; z += STEP) {
            world.spawnParticle(Particle.END_ROD, x + 0.5, y, z + 0.5, 1, 0, 0, 0, 0);
        }
    }

    private static void spawnAlongX(World world, int z, double y, int xMin, int xMax, int px) {
        int x0 = Math.max(xMin, px - STRIPE_HALF);
        int x1 = Math.min(xMax, px + STRIPE_HALF);
        for (int x = x0; x <= x1; x += STEP) {
            world.spawnParticle(Particle.END_ROD, x + 0.5, y, z + 0.5, 1, 0, 0, 0, 0);
        }
    }
}
