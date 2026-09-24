package com.foxaria.crafting.listener;

import com.foxaria.api.item.FoxariaItemPdc;
import com.foxaria.crafting.CraftRecipeRegistry;
import com.foxaria.crafting.CraftingService;
import org.bukkit.Keyed;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

public final class CraftPrepareListener implements Listener {

    private final CraftingService crafting;

    public CraftPrepareListener(CraftingService crafting) {
        this.crafting = crafting;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPrepare(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        CraftingInventory inv = event.getInventory();
        ItemStack[] matrix = inv.getMatrix();
        boolean hasCore = false;
        if (matrix != null) {
            for (ItemStack s : matrix) {
                if (FoxariaItemPdc.isCraftingCoreIngredient(crafting.plugin(), s)) {
                    hasCore = true;
                    break;
                }
            }
        }
        if (hasCore) {
            if (recipe == null || !CraftRecipeRegistry.isFoxariaCraftRecipe(recipe, crafting.plugin())) {
                inv.setResult(null);
                return;
            }
        }
        if (recipe != null && CraftRecipeRegistry.isFoxariaCraftRecipe(recipe, crafting.plugin()) && recipe instanceof Keyed keyed) {
            HumanEntity he = event.getView().getPlayer();
            if (!(he instanceof Player player)) {
                inv.setResult(null);
                return;
            }
            String cid = crafting.registry().resolveCraftId(keyed);
            if (cid == null || !crafting.playerMeetsKnowledge(player, cid)) {
                inv.setResult(null);
            }
        }
    }
}
