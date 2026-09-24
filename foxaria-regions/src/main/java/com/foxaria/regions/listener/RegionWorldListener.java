package com.foxaria.regions.listener;

import com.foxaria.regions.RegionBlockMarkers;
import com.foxaria.regions.RegionFacade;
import com.foxaria.regions.gui.RegionCabinetMenu;
import com.foxaria.regions.RegionItems;
import com.foxaria.regions.RegionKeys;
import com.foxaria.regions.RegionRecord;
import com.foxaria.regions.RegionSession;
import com.foxaria.api.service.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;
import java.util.UUID;

public final class RegionWorldListener implements Listener {

    private final RegionFacade f;

    public RegionWorldListener(RegionFacade f) {
        this.f = f;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlacePrivat(BlockPlaceEvent event) {
        if (!RegionItems.isPrivat(f.plugin(), event.getItemInHand())) {
            return;
        }
        Block bottom = event.getBlockPlaced();
        if (bottom.getType() != Material.SMITHING_TABLE) {
            event.setCancelled(true);
            return;
        }
        Player player = event.getPlayer();
        Block up = bottom.getRelative(BlockFace.UP);
        if (!up.getType().isAir() && !up.isReplaceable()) {
            event.setCancelled(true);
            f.messages().send(player, "region.cabinet-no-space", "&cНужно 2 блока высотой свободного места.");
            return;
        }
        if (f.repo().findAnyMembershipRegion(player.getUniqueId()).join().isPresent()) {
            event.setCancelled(true);
            f.messages().send(player, "region.cannot-place-member", "&cВы уже в привате — нельзя поставить ещё одно ядро.");
            return;
        }
        int cx = bottom.getX();
        int cz = bottom.getZ();
        String world = bottom.getWorld().getName();
        int savedLevel = RegionItems.privatSavedLevel(f.plugin(), event.getItemInHand());
        int half = f.config().level(savedLevel).halfSizeBlocks();
        int footY = bottom.getY();
        if (f.manager().overlapsNewCore(bottom.getWorld(), cx, cz, footY, half)) {
            event.setCancelled(true);
            f.messages().send(player, "region.overlap", "&cЗдесь пересекается другой приват.");
            return;
        }

        UUID owner = player.getUniqueId();
        long now = System.currentTimeMillis();
        int id = f.repo().insertRegion(world, cx, cz, half, savedLevel, owner, cx, bottom.getY(), cz, f.config().coreMaxHp).join();
        f.repo().addMember(id, owner, "owner").join();

        RegionRecord rec = new RegionRecord(id, world, cx, cz, half, savedLevel, owner, cx, bottom.getY(), cz,
            f.config().coreMaxHp, f.config().coreMaxHp, now, 0, 0, 3, null);
        f.manager().add(rec);

        up.setType(Material.SMOKER, false);
        RegionBlockMarkers.markCore(f.plugin(), bottom, id);
        RegionBlockMarkers.markCore(f.plugin(), up, id);
        int span = half * 2 + 1;
        f.messages().send(player, "region.created", "&aПриват создан. &7От ядра по X и Z: &f±" + half + " &7блок (&f" + span + "×" + span + "&7). &7Подробности — &fстатистика в GUI ядра&7.");
    }

    /**
     * Рейдовый TNT при установке блока сразу превращается в подожжённый (без огнива).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRaidTntPlacedPrime(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.TNT) {
            return;
        }
        int tier = RegionItems.dynamiteTier(f.plugin(), event.getItemInHand());
        if (tier <= 0) {
            return;
        }
        Block b = event.getBlockPlaced();
        f.plugin().getServer().getScheduler().runTask(f.plugin(), () -> {
            if (b.getType() != Material.TNT) {
                return;
            }
            var w = b.getWorld();
            var center = b.getLocation().add(0.5, 0.5, 0.5);
            b.setType(Material.AIR, false);
            w.spawn(center, TNTPrimed.class, tnt -> {
                tnt.setFuseTicks(80);
                tnt.getPersistentDataContainer().set(RegionKeys.dynamiteTier(f.plugin()), PersistentDataType.INTEGER, tier);
            });
        });
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBreak(BlockBreakEvent event) {
        var loc = event.getBlock().getLocation();
        Optional<RegionRecord> reg = f.manager().findContaining(loc);
        if (reg.isEmpty()) {
            return;
        }
        RegionRecord r = reg.get();
        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();
        if (r.isCoreBlock(bx, by, bz)) {
            event.setCancelled(true);
            return;
        }
        if (f.repo().isMember(r.id(), event.getPlayer().getUniqueId()).join()) {
            return;
        }
        event.setCancelled(true);
        f.messages().send(event.getPlayer(), "region.break-not-member",
            "&cВы не состоите в этом регионе.");
    }

    /**
     * Нижний блок (кузница) часто без TileState/PDC — определяем ядро по координатам из БД.
     * HIGHEST + отмена использования блока, чтобы не открывался стандартный GUI кузницы.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractCabinet(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block b = event.getClickedBlock();
        Material t = b.getType();
        if (t != Material.SMITHING_TABLE && t != Material.SMOKER) {
            return;
        }
        Optional<RegionRecord> byCore = f.manager().findCoreAt(b.getLocation());
        Optional<Integer> ridPdc = RegionBlockMarkers.regionIdOf(f.plugin(), b);
        int regionId;
        if (byCore.isPresent()) {
            regionId = byCore.get().id();
        } else if (ridPdc.isPresent()) {
            regionId = ridPdc.get();
        } else {
            return;
        }
        Optional<RegionRecord> reg = f.manager().byId(regionId);
        if (reg.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        if (!f.repo().isMember(reg.get().id(), player.getUniqueId()).join()) {
            event.setCancelled(true);
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            RegionRecord r = reg.get();
            f.messages().send(player, "region.foreign-core",
                "&cЧужое ядро, здоровье &f<hp>&7/&f<max>&c (&7Можно сломать оружием&c).",
                new MessageService.Placeholder("hp", String.valueOf(r.coreHp())),
                new MessageService.Placeholder("max", String.valueOf(r.coreMaxHp())));
            return;
        }
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        RegionSession.setLastRegion(player, reg.get().id());
        f.menus().open(player, new RegionCabinetMenu(f, reg.get().id()));
    }

    /**
     * Ванильный GUI стола кузнеца / коптильни на ядре: участникам — наш кабинет;
     * чужакам — только сообщение, без меню.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVanillaCoreGuiEscape(InventoryOpenEvent event) {
        InventoryType type = event.getInventory().getType();
        if (type != InventoryType.SMITHING && type != InventoryType.SMOKER) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        org.bukkit.Location loc = event.getInventory().getLocation();
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        Optional<RegionRecord> core = f.manager().findCoreAt(loc);
        if (core.isEmpty()) {
            return;
        }
        RegionRecord rec = core.get();
        if (!f.repo().isMember(rec.id(), player.getUniqueId()).join()) {
            event.setCancelled(true);
            f.messages().send(player, "region.foreign-core",
                "&cЧужое ядро, здоровье &f<hp>&7/&f<max>&c (&7Можно сломать оружием&c).",
                new MessageService.Placeholder("hp", String.valueOf(rec.coreHp())),
                new MessageService.Placeholder("max", String.valueOf(rec.coreMaxHp())));
            return;
        }
        event.setCancelled(true);
        int rid = rec.id();
        Bukkit.getScheduler().runTask(f.plugin(), () -> {
            RegionSession.setLastRegion(player, rid);
            f.menus().open(player, new RegionCabinetMenu(f, rid));
        });
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDynamiteUse(PlayerInteractEvent event) {
        ItemStack it = event.getItem();
        if (it == null) {
            return;
        }
        int tier = RegionItems.dynamiteTier(f.plugin(), it);
        if (tier <= 0) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        event.setCancelled(true);
        Player p = event.getPlayer();
        if (p.getGameMode() != GameMode.CREATIVE) {
            it.setAmount(it.getAmount() - 1);
        }
        var loc = event.getClickedBlock() != null
            ? event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0.0625, 0.5)
            : p.getEyeLocation().add(p.getLocation().getDirection().multiply(1.5));
        loc.getWorld().spawn(loc, TNTPrimed.class, tnt -> {
            tnt.setFuseTicks(80);
            tnt.getPersistentDataContainer().set(RegionKeys.dynamiteTier(f.plugin()), PersistentDataType.INTEGER, tier);
        });
    }
}
