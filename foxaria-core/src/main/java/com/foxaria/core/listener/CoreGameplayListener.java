package com.foxaria.core.listener;

import com.foxaria.api.model.AuditEvent;
import com.foxaria.api.service.AuditService;
import com.foxaria.api.service.ConfigService;
import com.foxaria.core.service.CombatTagService;
import com.foxaria.core.service.CoreRepository;
import com.foxaria.core.service.TeleportService;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Map;

public final class CoreGameplayListener implements Listener {

    private final ConfigService configs;
    private final CoreRepository repository;
    private final TeleportService teleportService;
    private final CombatTagService combatTagService;
    private final AuditService audits;

    public CoreGameplayListener(
        ConfigService configs,
        CoreRepository repository,
        TeleportService teleportService,
        CombatTagService combatTagService,
        AuditService audits
    ) {
        this.configs = configs;
        this.repository = repository;
        this.teleportService = teleportService;
        this.combatTagService = combatTagService;
        this.audits = audits;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        if (configs.main().getBoolean("broadcast.hide-vanilla-join-quit", true)) {
            event.setJoinMessage(null);
        }
        repository.upsertPlayer(event.getPlayer());
        combatTagService.handleJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        if (configs.main().getBoolean("broadcast.hide-vanilla-join-quit", true)) {
            event.setQuitMessage(null);
        }
        repository.upsertPlayer(event.getPlayer());
        combatTagService.handleQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player damaged)) {
            return;
        }
        Player attacker = resolvePlayerDamager(event.getDamager());
        if (attacker == null || attacker.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        combatTagService.tag(attacker, damaged);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        teleportService.handleMove(event.getPlayer(), event.getFrom(), to);

        if (!configs.main().getBoolean("nether-roof-protection.enabled", true)) {
            return;
        }
        if (event.getPlayer().getWorld().getEnvironment() == World.Environment.NETHER
            && to.getY() > configs.main().getDouble("nether-roof-protection.max-y", 127.0D)
            && !event.getPlayer().hasPermission("foxaria.core.bypass.netherroof")) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isSpawnProtected(event.getBlockPlaced().getLocation(), event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isSpawnProtected(event.getBlock().getLocation(), event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT
            && configs.main().getBoolean("spawn-protection.block-chorus-into-spawn", true)
            && isProtectedRegion(event.getTo())) {
            event.setCancelled(true);
            audits.append(new AuditEvent(
                "SPAWN_REGION_TELEPORT_BLOCKED",
                event.getPlayer().getUniqueId(),
                null,
                event.getPlayer().getName(),
                null,
                "Blocked chorus fruit teleport into protected spawn",
                Map.of("world", event.getPlayer().getWorld().getName()),
                System.currentTimeMillis()
            ));
        }
    }

    private boolean isSpawnProtected(Location location, Player player) {
        if (!configs.main().getBoolean("spawn-protection.enabled", true)) {
            return false;
        }
        if (player.hasPermission("foxaria.core.bypass.spawnprotection")) {
            return false;
        }
        return isProtectedRegion(location);
    }

    private boolean isProtectedRegion(Location location) {
        if (location == null) {
            return false;
        }
        String configuredWorld = configs.main().getString("spawn-protection.world", "world");
        if (!location.getWorld().getName().equalsIgnoreCase(configuredWorld)) {
            return false;
        }
        int radius = configs.main().getInt("spawn-protection.radius", 64);
        Block block = location.getBlock();
        return Math.abs(block.getX()) <= radius && Math.abs(block.getZ()) <= radius;
    }

    private Player resolvePlayerDamager(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Arrow arrow) {
            ProjectileSource source = arrow.getShooter();
            if (source instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}
