package com.foxaria.crafting.listener;

import com.foxaria.crafting.CraftRecipeRegistry;
import com.foxaria.crafting.CraftingService;
import org.bukkit.Keyed;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.Recipe;

public final class CraftItemListener implements Listener {

    private final CraftingService crafting;

    public CraftItemListener(CraftingService crafting) {
        this.crafting = crafting;
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Recipe recipe = event.getRecipe();
        if (recipe instanceof Keyed keyed && CraftRecipeRegistry.isFoxariaCraftRecipe(recipe, crafting.plugin())) {
            String cid = crafting.registry().resolveCraftId(keyed);
            if (cid == null || !crafting.playerMeetsKnowledge(player, cid)) {
                event.setCancelled(true);
            }
        }
    }
}
