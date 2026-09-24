package com.foxaria.modernfurnace.listener;

import com.foxaria.modernfurnace.ModernFurnaceEngine;
import com.foxaria.modernfurnace.ModernFurnaceItems;
import com.foxaria.modernfurnace.ModernFurnaceKeys;
import com.foxaria.modernfurnace.ModernFurnacePending;
import com.foxaria.modernfurnace.ModernFurnaceService;
import com.foxaria.modernfurnace.PersistedFurnaceJson;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Optional;

public final class ModernFurnacePipeChestListener implements Listener {

    private final ModernFurnacePending pending;
    private final ModernFurnaceService service;
    private final ModernFurnaceKeys keys;

    public ModernFurnacePipeChestListener(ModernFurnacePending pending, ModernFurnaceService service, ModernFurnaceKeys keys) {
        this.pending = pending;
        this.service = service;
        this.keys = keys;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChest(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        ModernFurnacePending.Pending pend = pending.peek(player.getUniqueId());
        if (pend == null) {
            return;
        }
        Block block = event.getClickedBlock();
        Material t = block.getType();
        if (t != Material.CHEST && t != Material.TRAPPED_CHEST && t != Material.BARREL) {
            return;
        }
        if (!holdingPipeKey(player)) {
            player.sendMessage("§cДержи в руке §fключ труб §cи снова нажми ПКМ по сундуку.");
            return;
        }
        pending.poll(player.getUniqueId());
        Location furnace = pend.furnace();
        Location chest = block.getLocation();
        if (!ModernFurnaceEngine.pipeReach(furnace, chest)) {
            pending.set(player.getUniqueId(), pend.furnace(), pend.mode());
            player.sendMessage("§cСлишком далеко (макс. §f5§c блоков по манхэттену).");
            return;
        }
        Optional<PersistedFurnaceJson> opt = service.findOurFurnace(furnace);
        if (opt.isEmpty()) {
            return;
        }
        PersistedFurnaceJson d = opt.get();
        String wid = chest.getWorld().getUID().toString();
        int x = chest.getBlockX();
        int y = chest.getBlockY();
        int z = chest.getBlockZ();
        if (pend.mode() == ModernFurnacePending.Mode.INPUT_CHEST) {
            d.inputChestWorld = wid;
            d.inputChestX = x;
            d.inputChestY = y;
            d.inputChestZ = z;
            player.sendMessage("§aВход привязан к сундуку.");
        } else {
            d.outputChestWorld = wid;
            d.outputChestX = x;
            d.outputChestY = y;
            d.outputChestZ = z;
            player.sendMessage("§aВыход привязан к сундуку.");
        }
        service.putMemory(furnace, d);
        service.saveAsync(furnace);
        event.setCancelled(true);
    }

    private boolean holdingPipeKey(Player player) {
        return ModernFurnaceItems.isKey(keys, player.getInventory().getItemInMainHand())
            || ModernFurnaceItems.isKey(keys, player.getInventory().getItemInOffHand());
    }
}
