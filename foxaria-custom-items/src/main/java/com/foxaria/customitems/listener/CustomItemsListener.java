package com.foxaria.customitems.listener;

import com.foxaria.customitems.ConfigCrateService;
import com.foxaria.customitems.PdcCustomItemService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public final class CustomItemsListener implements Listener {

    private final ConfigCrateService crateService;
    private final PdcCustomItemService itemService;

    public CustomItemsListener(ConfigCrateService crateService, PdcCustomItemService itemService) {
        this.crateService = crateService;
        this.itemService = itemService;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack itemStack = event.getItem();
        if (itemStack == null) {
            return;
        }
        String type = itemService.readType(itemStack);
        if (type == null || !type.startsWith("one_time_reward:")) {
            return;
        }
        event.setCancelled(true);
        crateService.redeemOneTimeReward(event.getPlayer(), itemStack);
    }
}
