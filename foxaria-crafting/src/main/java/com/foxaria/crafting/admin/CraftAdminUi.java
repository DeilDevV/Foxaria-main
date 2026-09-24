package com.foxaria.crafting.admin;

import com.foxaria.core.gui.MenuManager;
import com.foxaria.crafting.CraftJson;
import com.foxaria.crafting.CraftingService;
import com.foxaria.crafting.CustomCraftDefinition;
import com.foxaria.crafting.model.CraftRecipeJson;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Открытие редактора с уже сохранённым рецептом.
 */
public final class CraftAdminUi {

    private CraftAdminUi() {
    }

    public static void openForEdit(Player player, MenuManager menus, CraftingService service, CustomCraftDefinition def) {
        CraftRecipeJson j = def.parsed();
        String id = def.craftId();
        CraftAdminHolder h = new CraftAdminHolder(service, "&6&lFoxaria &8| &f" + id);
        h.beginEdit(id, def.sortOrder());
        h.knowledgeLevel = Math.max(1, j.requiredKnowledge);
        if (CraftJson.isSmeltingKind(j)) {
            h.adminCreationMode = 1;
        } else {
            h.adminCreationMode = 0;
        }
        h.smeltRegisterPrimary = j.registerPrimaryRecipe;
        h.smeltCookTicks = j.smeltCookTicks > 0 ? j.smeltCookTicks : 200;
        h.smeltExperience = j.smeltExperience;
        h.bonusChancePercent = (int) Math.round(Math.max(0, Math.min(100, j.bonusChancePercent)));

        if (CraftJson.isSmeltingKind(j)) {
            ItemStack[] grid = CraftJson.resolveGrid(service.templates(), j);
            ItemStack firstIn = null;
            for (ItemStack cell : grid) {
                if (cell != null && !cell.getType().isAir()) {
                    firstIn = cell.clone();
                    break;
                }
            }
            h.getInventory().setItem(CraftAdminListener.FURNACE_INPUT_SLOT, firstIn);
            try {
                ItemStack res = CraftJson.resolveResult(service.templates(), j).clone();
                h.getInventory().setItem(CraftAdminListener.FURNACE_RESULT_SLOT, res);
            } catch (Exception ignored) {
                h.getInventory().setItem(CraftAdminListener.FURNACE_RESULT_SLOT, null);
            }
        } else {
            ItemStack[] grid = CraftJson.resolveGrid(service.templates(), j);
            for (int i = 0; i < CraftAdminListener.MATRIX_SLOTS.length; i++) {
                ItemStack cell = grid[i];
                int raw = CraftAdminListener.MATRIX_SLOTS[i];
                h.getInventory().setItem(raw, cell == null || cell.getType().isAir() ? null : cell.clone());
            }
            try {
                ItemStack res = CraftJson.resolveResult(service.templates(), j).clone();
                h.getInventory().setItem(CraftAdminListener.RESULT_SLOT, res);
            } catch (Exception ignored) {
                h.getInventory().setItem(CraftAdminListener.RESULT_SLOT, null);
            }
        }
        if (j.bonusTemplateId != null && !j.bonusTemplateId.isBlank()) {
            service.templates().cloneTemplate(j.bonusTemplateId).ifPresent(b ->
                h.getInventory().setItem(CraftAdminListener.BONUS_TEMPLATE_SLOT, b.clone()));
        }
        CraftAdminListener.decorate(h);
        player.openInventory(h.getInventory());
    }
}
