package com.foxaria.regions.listener;

import com.foxaria.regions.RegionBlockMarkers;
import com.foxaria.regions.RegionDamageService;
import com.foxaria.regions.RegionFacade;
import com.foxaria.regions.RegionRecord;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RegionCombatListener implements Listener {

    private static final long NOT_MEMBER_MSG_MS = 600L;

    private final RegionFacade f;
    private final RegionDamageService damage;
    private final ConcurrentHashMap<UUID, Long> lastNotMemberMsgMs = new ConcurrentHashMap<>();

    public RegionCombatListener(RegionFacade f, RegionDamageService damage) {
        this.f = f;
        this.damage = damage;
    }

    private void sendNotMemberThrottled(Player player) {
        long now = System.currentTimeMillis();
        UUID id = player.getUniqueId();
        if (lastNotMemberMsgMs.getOrDefault(id, 0L) + NOT_MEMBER_MSG_MS > now) {
            return;
        }
        lastNotMemberMsgMs.put(id, now);
        f.messages().send(player, "region.break-not-member", "&cВы не состоите в этом регионе.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageBlock(BlockDamageEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Optional<RegionRecord> region = f.manager().findContaining(block.getLocation());
        if (region.isEmpty()) {
            return;
        }
        boolean coreHit = RegionBlockMarkers.regionIdOf(f.plugin(), block).isPresent()
            || region.get().isCoreBlock(block.getX(), block.getY(), block.getZ());

        if (!coreHit) {
            if (f.repo().isMemberCached(region.get().id(), player.getUniqueId())) {
                return;
            }
            event.setCancelled(true);
            sendNotMemberThrottled(player);
            return;
        }

        event.setCancelled(true);
        if (!raidMeleeWeapon(player)) {
            damage.msgCannotBreakThis(player);
            return;
        }
        if (player.getAttackCooldown() < f.config().minAttackCooldown) {
            return;
        }
        damage.damageCore(player, region.get(), f.config().coreWeaponDamage);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectile(ProjectileHitEvent event) {
        if (event.getHitBlock() == null) {
            return;
        }
        Block block = event.getHitBlock();
        Optional<RegionRecord> region = f.manager().findContaining(block.getLocation());
        if (region.isEmpty()) {
            return;
        }
        boolean coreHit = RegionBlockMarkers.regionIdOf(f.plugin(), block).isPresent()
            || region.get().isCoreBlock(block.getX(), block.getY(), block.getZ());
        if (!coreHit) {
            Projectile proj = event.getEntity();
            ProjectileSource shooter = proj.getShooter();
            if (shooter instanceof Player p) {
                if (f.repo().isMemberCached(region.get().id(), p.getUniqueId())) {
                    return;
                }
                sendNotMemberThrottled(p);
            }
            event.setCancelled(true);
            return;
        }
        Projectile proj = event.getEntity();
        if (!(proj.getShooter() instanceof Player player)) {
            return;
        }
        event.setCancelled(true);
        proj.remove();
        damage.damageCore(player, region.get(), f.config().coreWeaponDamage);
    }

    private static boolean raidMeleeWeapon(Player player) {
        Material m = player.getInventory().getItemInMainHand().getType();
        if (m.isAir()) {
            return false;
        }
        String n = m.name();
        return n.endsWith("_SWORD")
            || n.endsWith("_AXE")
            || n.endsWith("_PICKAXE")
            || n.endsWith("_SHOVEL")
            || n.endsWith("_HOE")
            || m == Material.TRIDENT;
    }
}
