package com.foxaria.regions.listener;

import com.foxaria.regions.RegionFacade;
import com.foxaria.regions.RegionItems;
import com.foxaria.regions.RegionRecord;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Чужак в чужом привате: PvP, рейдовый динамит (ПКМ или установка TNT — подрыв без поджига).
 * Подбор предметов с земли разрешён.
 */
public final class RegionForeignProtectionListener implements Listener {

    private static final long MSG_COOLDOWN_MS = 400L;

    private static final Set<InventoryType> BLOCK_CONTAINER_TYPES = EnumSet.of(
        InventoryType.CHEST,
        InventoryType.DISPENSER,
        InventoryType.DROPPER,
        InventoryType.FURNACE,
        InventoryType.BREWING,
        InventoryType.HOPPER,
        InventoryType.BARREL,
        InventoryType.BLAST_FURNACE,
        InventoryType.SMOKER,
        InventoryType.SHULKER_BOX,
        InventoryType.ENDER_CHEST,
        InventoryType.ANVIL,
        InventoryType.GRINDSTONE,
        InventoryType.STONECUTTER,
        InventoryType.LOOM,
        InventoryType.CARTOGRAPHY,
        InventoryType.LECTERN,
        InventoryType.ENCHANTING,
        InventoryType.BEACON
    );

    private final RegionFacade f;
    private final ConcurrentHashMap<UUID, Long> lastNotMemberMsgMs = new ConcurrentHashMap<>();

    public RegionForeignProtectionListener(RegionFacade f) {
        this.f = f;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        RegionIntruderGlow.clear(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlaceForeign(BlockPlaceEvent event) {
        if (RegionItems.isPrivat(f.plugin(), event.getItemInHand())) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE && player.hasPermission("foxaria.region.admin")) {
            return;
        }
        Optional<RegionRecord> reg = f.manager().findContaining(event.getBlockPlaced().getLocation());
        if (reg.isEmpty()) {
            return;
        }
        if (f.repo().isMember(reg.get().id(), player.getUniqueId()).join()) {
            return;
        }
        if (RegionItems.dynamiteTier(f.plugin(), event.getItemInHand()) > 0
            && event.getBlockPlaced().getType() == Material.TNT) {
            return;
        }
        event.setCancelled(true);
        sendNotMember(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractForeign(PlayerInteractEvent event) {
        if (event.isCancelled()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE && player.hasPermission("foxaria.region.admin")) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked != null) {
            Material bt = clicked.getType();
            if (bt == Material.SMITHING_TABLE || bt == Material.SMOKER) {
                if (f.manager().findCoreAt(clicked.getLocation()).isPresent()) {
                    return;
                }
            }
        }

        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK
            && action != Action.RIGHT_CLICK_BLOCK
            && action != Action.PHYSICAL) {
            return;
        }
        if (clicked == null) {
            return;
        }
        Optional<RegionRecord> reg = f.manager().findContaining(clicked.getLocation());
        if (reg.isEmpty()) {
            return;
        }
        if (f.repo().isMember(reg.get().id(), player.getUniqueId()).join()) {
            return;
        }

        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        if (event.getHand() == EquipmentSlot.HAND || event.getHand() == EquipmentSlot.OFF_HAND) {
            event.setUseItemInHand(Event.Result.DENY);
        }
        sendNotMember(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntityForeign(PlayerInteractEntityEvent event) {
        if (event.isCancelled()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE && player.hasPermission("foxaria.region.admin")) {
            return;
        }
        Optional<RegionRecord> reg = f.manager().findContaining(event.getRightClicked().getLocation());
        if (reg.isEmpty()) {
            return;
        }
        if (f.repo().isMember(reg.get().id(), player.getUniqueId()).join()) {
            return;
        }
        event.setCancelled(true);
        sendNotMember(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpenForeign(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE && player.hasPermission("foxaria.region.admin")) {
            return;
        }
        InventoryType type = event.getInventory().getType();
        org.bukkit.Location loc = event.getInventory().getLocation();
        if (loc != null && loc.getWorld() != null
            && (type == InventoryType.SMITHING || type == InventoryType.SMOKER)
            && f.manager().findCoreAt(loc).isPresent()) {
            return;
        }
        if (!BLOCK_CONTAINER_TYPES.contains(type)) {
            return;
        }
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        Optional<RegionRecord> reg = f.manager().findContaining(loc);
        if (reg.isEmpty()) {
            return;
        }
        if (f.repo().isMember(reg.get().id(), player.getUniqueId()).join()) {
            return;
        }
        event.setCancelled(true);
        sendNotMember(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmptyForeign(org.bukkit.event.player.PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE && player.hasPermission("foxaria.region.admin")) {
            return;
        }
        Block target = event.getBlockClicked().getRelative(event.getBlockFace());
        Optional<RegionRecord> reg = f.manager().findContaining(target.getLocation());
        if (reg.isEmpty()) {
            return;
        }
        if (f.repo().isMember(reg.get().id(), player.getUniqueId()).join()) {
            return;
        }
        event.setCancelled(true);
        sendNotMember(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockIgniteForeign(BlockIgniteEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE && player.hasPermission("foxaria.region.admin")) {
            return;
        }
        Optional<RegionRecord> reg = f.manager().findContaining(event.getBlock().getLocation());
        if (reg.isEmpty()) {
            return;
        }
        if (f.repo().isMember(reg.get().id(), player.getUniqueId()).join()) {
            return;
        }
        event.setCancelled(true);
        sendNotMember(player);
    }

    private void sendNotMember(Player player) {
        long now = System.currentTimeMillis();
        UUID id = player.getUniqueId();
        if (lastNotMemberMsgMs.getOrDefault(id, 0L) + MSG_COOLDOWN_MS > now) {
            return;
        }
        lastNotMemberMsgMs.put(id, now);
        f.messages().send(player, "region.break-not-member", "&cВы не состоите в этом регионе.");
    }
}
