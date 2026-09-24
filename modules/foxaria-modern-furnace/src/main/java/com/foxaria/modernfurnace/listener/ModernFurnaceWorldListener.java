package com.foxaria.modernfurnace.listener;

import com.foxaria.api.service.MessageService;
import com.foxaria.api.service.RegionInteractionGuard;
import com.foxaria.modernfurnace.ModernFurnaceItems;
import com.foxaria.modernfurnace.ModernFurnaceKeys;
import com.foxaria.modernfurnace.ModernFurnaceConfig;
import com.foxaria.modernfurnace.ModernFurnaceHolder;
import com.foxaria.modernfurnace.ModernFurnaceMenus;
import com.foxaria.modernfurnace.ModernFurnaceService;
import com.foxaria.modernfurnace.ModernFurnaceStateFactory;
import com.foxaria.modernfurnace.PersistedFurnaceJson;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

public final class ModernFurnaceWorldListener implements Listener {

    private final ModernFurnaceKeys keys;
    private final ModernFurnaceService service;
    private final ModernFurnaceConfig config;
    private final MessageService messages;
    private final RegionInteractionGuard regionGuard;

    public ModernFurnaceWorldListener(
        ModernFurnaceKeys keys,
        ModernFurnaceService service,
        ModernFurnaceConfig config,
        MessageService messages,
        RegionInteractionGuard regionGuard
    ) {
        this.keys = keys;
        this.service = service;
        this.config = config;
        this.messages = messages;
        this.regionGuard = regionGuard;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack hand = event.getItemInHand();
        if (!ModernFurnaceItems.isModernFurnace(keys, hand)) {
            return;
        }
        Location loc = event.getBlockPlaced().getLocation();
        String blob = ModernFurnaceItems.readStateBlob(keys, hand);
        PersistedFurnaceJson j;
        if (blob != null && !blob.isBlank()) {
            j = ModernFurnaceStateFactory.fromItemBlob(blob);
        } else if (ModernFurnaceItems.isMaxFurnaceItem(keys, hand)) {
            j = ModernFurnaceStateFactory.empty(true, config);
        } else {
            j = ModernFurnaceStateFactory.empty(false, null);
        }
        service.putMemory(loc, j);
        service.saveAsync(loc);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.FURNACE) {
            return;
        }
        Location loc = block.getLocation();
        Optional<PersistedFurnaceJson> data = service.findOurFurnace(loc);
        if (data.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        if (regionGuard != null && !regionGuard.allowsForeignBlockUse(player, loc)) {
            event.setCancelled(true);
            messages.send(player, "region.break-not-member", "&cВы не состоите в этом регионе.");
            return;
        }
        event.setDropItems(false);
        event.setExpToDrop(0);
        ItemStack drop = ModernFurnaceItems.dropFromBlock(keys, data.get(), data.get().donorMax);
        Location dropLoc = loc.clone().add(0.5, 0.5, 0.5);
        loc.getWorld().dropItemNaturally(dropLoc, drop);
        service.removeBlock(loc);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block.getType() != Material.FURNACE) {
            return;
        }
        Location loc = block.getLocation();
        Player player = event.getPlayer();
        if (regionGuard != null && !regionGuard.allowsForeignBlockUse(player, loc)) {
            event.setCancelled(true);
            messages.send(player, "region.break-not-member", "&cВы не состоите в этом регионе.");
            return;
        }
        ItemStack hand = event.getItem();
        if (hand != null && ModernFurnaceItems.isKey(keys, hand)) {
            Optional<PersistedFurnaceJson> pipeData = service.findOurFurnace(loc);
            if (pipeData.isPresent() && pipeData.get().pipesUnlocked) {
                event.setCancelled(true);
                ModernFurnaceHolder pipeHolder = ModernFurnaceMenus.openPipes(loc, pipeData.get());
                player.openInventory(pipeHolder.getInventory());
                return;
            }
        }
        Optional<PersistedFurnaceJson> d = service.findOurFurnace(loc);
        if (d.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        if (player.getGameMode() != GameMode.SPECTATOR) {
            PersistedFurnaceJson data = d.get();
            data.lastSmelterUuid = player.getUniqueId().toString();
            service.putMemory(loc, data);
            ModernFurnaceHolder holder = ModernFurnaceMenus.openMain(loc, data, config);
            player.openInventory(holder.getInventory());
        }
    }
}
