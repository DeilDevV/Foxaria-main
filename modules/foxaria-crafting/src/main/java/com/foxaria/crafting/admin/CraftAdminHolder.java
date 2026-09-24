package com.foxaria.crafting.admin;

import com.foxaria.crafting.CraftingService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class CraftAdminHolder implements InventoryHolder {

    private final CraftingService service;
    private final Inventory inventory;
    int knowledgeLevel = 1;
    /** 0 — верстак, 1 — печь (все типы печей + модерн). */
    public int adminCreationMode;
    public boolean smeltRegisterPrimary = true;
    public int smeltCookTicks = 200;
    public float smeltExperience = 0.2f;
    /** 0–100, доп. дроп из слота 2 (шаблон). */
    public int bonusChancePercent;
    /** Если не null — перезапись существующего id при сохранении. */
    String editingCraftId;
    int editingSortOrder;

    public CraftAdminHolder(CraftingService service) {
        this(service, "&6Foxaria &8| &fредактор крафта");
    }

    /**
     * @param titleRaw цветовые коды {@code &}, например {@code &6Foxaria &8| &fид_крафта}
     */
    public CraftAdminHolder(CraftingService service, String titleRaw) {
        this.service = service;
        this.inventory = Bukkit.createInventory(this, 54, ChatColor.translateAlternateColorCodes('&', titleRaw));
    }

    public CraftingService service() {
        return service;
    }

    public void beginEdit(String craftId, int sortOrder) {
        this.editingCraftId = craftId;
        this.editingSortOrder = sortOrder;
    }

    public void clearEdit() {
        this.editingCraftId = null;
        this.editingSortOrder = 0;
    }

    public boolean isEditing() {
        return editingCraftId != null && !editingCraftId.isBlank();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
