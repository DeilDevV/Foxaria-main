package com.foxaria.crafting.admin;

import com.foxaria.core.gui.MenuManager;
import com.foxaria.crafting.CraftCreationKinds;
import com.foxaria.crafting.CraftJson;
import com.foxaria.crafting.CraftingService;
import com.foxaria.crafting.gui.CraftAdminListMenu;
import com.foxaria.crafting.model.CraftRecipeJson;
import com.foxaria.core.gui.MenuItems;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class CraftAdminListener implements Listener {

    /** Верстак: сетка как в игре. */
    public static final int[] MATRIX_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    public static final int RESULT_SLOT = 24;
    /** Печь: как в меню /craft — вход и результат. */
    public static final int FURNACE_INPUT_SLOT = 10;
    public static final int FURNACE_RESULT_SLOT = 16;
    public static final int BONUS_TEMPLATE_SLOT = 2;
    private static final int LIST_SLOT = 7;
    private static final int MODE_SLOT = 22;
    private static final int BONUS_MINUS = 39;
    private static final int BONUS_PLUS = 41;
    private static final int REG_PRIMARY_SLOT = 50;
    private static final int SAVE_SLOT = 49;
    private static final int CLOSE_SLOT = 53;
    private static final int MIN_KNOW = 1;
    private static final int MAX_KNOW = 20;

    private final JavaPlugin plugin;
    private final MenuManager menus;

    public CraftAdminListener(JavaPlugin plugin, MenuManager menus) {
        this.plugin = plugin;
        this.menus = menus;
    }

    public static void decorate(CraftAdminHolder holder) {
        ItemStack[] keepMatrix = new ItemStack[MATRIX_SLOTS.length];
        for (int i = 0; i < MATRIX_SLOTS.length; i++) {
            keepMatrix[i] = holder.getInventory().getItem(MATRIX_SLOTS[i]);
        }
        ItemStack keepResultTable = holder.getInventory().getItem(RESULT_SLOT);
        ItemStack keepFurnaceIn = holder.getInventory().getItem(FURNACE_INPUT_SLOT);
        ItemStack keepFurnaceRes = holder.getInventory().getItem(FURNACE_RESULT_SLOT);
        ItemStack keepBonus = holder.adminCreationMode > 0 ? holder.getInventory().getItem(BONUS_TEMPLATE_SLOT) : null;

        for (int i = 0; i < 54; i++) {
            holder.getInventory().setItem(i, MenuItems.filler());
        }

        if (holder.adminCreationMode == 0) {
            for (int i = 0; i < MATRIX_SLOTS.length; i++) {
                ItemStack c = keepMatrix[i];
                holder.getInventory().setItem(MATRIX_SLOTS[i], c == null || c.getType().isAir() ? null : c);
            }
            ItemStack r = keepResultTable;
            holder.getInventory().setItem(RESULT_SLOT, r == null || r.getType().isAir() ? null : r);
        } else {
            ItemStack smeltIn = stackOrNull(keepFurnaceIn);
            if (smeltIn == null) {
                smeltIn = firstNonEmpty(keepMatrix);
            }
            ItemStack smeltRes = stackOrNull(keepFurnaceRes);
            if (smeltRes == null) {
                smeltRes = stackOrNull(keepResultTable);
            }
            holder.getInventory().setItem(FURNACE_INPUT_SLOT, smeltIn);
            holder.getInventory().setItem(FURNACE_RESULT_SLOT, smeltRes);
        }
        holder.getInventory().setItem(LIST_SLOT, MenuItems.item(Material.CHEST, "&eСписок крафтов",
            "&7Все рецепты: правка и удаление"));
        holder.getInventory().setItem(4, MenuItems.item(Material.RED_STAINED_GLASS_PANE, "&c− уровень знаний",
            "&7Сейчас: &e" + holder.knowledgeLevel));
        holder.getInventory().setItem(6, MenuItems.item(Material.LIME_STAINED_GLASS_PANE, "&a+ уровень знаний",
            "&7Сейчас: &e" + holder.knowledgeLevel));

        List<String> paperLore = new ArrayList<>();
        if (holder.isEditing()) {
            paperLore.add("&8ID: &f" + holder.editingCraftId);
            paperLore.add("&8 ");
        }
        if (holder.adminCreationMode == 0) {
            paperLore.add("&7Положи предметы &fв тех же клетках&7, что в верстаке.");
            paperLore.add("&7Пустой слот — пустая клетка.");
        } else {
            paperLore.add("&7Слева &f10&7 — из чего, справа &f16&7 — что получаем.");
            paperLore.add("&7Работает в обычной, плавильной и модерн-печи.");
            paperLore.add("&7Слот &f2 &7— образец &e/itemtemplate &7для бонуса (если шанс > 0).");
        }
        paperLore.add(holder.adminCreationMode == 0 ? "&72) Результат — слот справа от сетки." : "&72) Бонус — слот 2, если шанс > 0.");
        paperLore.add(holder.isEditing() ? "&73) &aСохранить изменения" : "&73) &aСоздать крафт");
        holder.getInventory().setItem(13, MenuItems.item(Material.PAPER,
            holder.adminCreationMode == 0 ? "&eСетка 3×3" : "&eПечь · схема",
            paperLore.toArray(new String[0])));

        String modeTitle = switch (holder.adminCreationMode) {
            case 1 -> "&eРежим: печь";
            default -> "&eРежим: верстак";
        };
        Material modeMat = switch (holder.adminCreationMode) {
            case 1 -> Material.FURNACE;
            default -> Material.CRAFTING_TABLE;
        };
        holder.getInventory().setItem(MODE_SLOT, MenuItems.item(modeMat, modeTitle,
            "&7ЛКМ — сменить: верстак ↔ печь",
            "&7Печь: все типы печей + модерн"));

        if (holder.adminCreationMode > 0) {
            holder.getInventory().setItem(BONUS_TEMPLATE_SLOT, keepBonus == null || keepBonus.getType().isAir() ? null : keepBonus.clone());
            holder.getInventory().setItem(BONUS_PLUS, MenuItems.item(Material.LIME_DYE, "&aШанс бонуса +1%",
                "&7Сейчас: &f" + holder.bonusChancePercent + "%"));
            holder.getInventory().setItem(BONUS_MINUS, MenuItems.item(Material.RED_DYE, "&cШанс бонуса −1%",
                "&7Сейчас: &f" + holder.bonusChancePercent + "%"));
            holder.getInventory().setItem(REG_PRIMARY_SLOT, MenuItems.item(
                holder.smeltRegisterPrimary ? Material.LIME_CONCRETE : Material.BARRIER,
                holder.smeltRegisterPrimary ? "&aРецепт Bukkit: да" : "&cРецепт Bukkit: нет",
                "&7ЛКМ — переключить",
                "&7«Нет» — только /craft и бонус (без дубля ванильной плавки)"));
        }

        if (holder.isEditing()) {
            holder.getInventory().setItem(SAVE_SLOT, MenuItems.item(Material.LIME_CONCRETE, "&a&lСохранить изменения",
                "&7Обновит рецепт на сервере"));
        } else {
            holder.getInventory().setItem(SAVE_SLOT, MenuItems.item(Material.LIME_CONCRETE, "&a&lСоздать крафт",
                "&7Сохранит рецепт и зарегистрирует его на сервере"));
        }
        holder.getInventory().setItem(CLOSE_SLOT, MenuItems.item(Material.BARRIER, "&cЗакрыть"));
        for (int i = 36; i < 45; i++) {
            if (i != SAVE_SLOT && i != CLOSE_SLOT && i != REG_PRIMARY_SLOT) {
                holder.getInventory().setItem(i, MenuItems.item(Material.PURPLE_STAINED_GLASS_PANE, "&8 "));
            }
        }
    }

    private static ItemStack stackOrNull(ItemStack s) {
        if (s == null || s.getType().isAir()) {
            return null;
        }
        return s.clone();
    }

    private static ItemStack firstNonEmpty(ItemStack[] cells) {
        if (cells == null) {
            return null;
        }
        for (ItemStack c : cells) {
            if (c != null && !c.getType().isAir()) {
                return c.clone();
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CraftAdminHolder holder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        Inventory bottom = event.getView().getBottomInventory();
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) {
            event.setCancelled(true);
            return;
        }
        int raw = event.getRawSlot();
        if (clicked == bottom) {
            if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
            }
            return;
        }
        if (raw == LIST_SLOT) {
            event.setCancelled(true);
            CraftingService svc = holder.service();
            menus.open(player, new CraftAdminListMenu(plugin, menus, svc));
            return;
        }
        if (raw == SAVE_SLOT) {
            event.setCancelled(true);
            trySave(player, holder);
            return;
        }
        if (raw == CLOSE_SLOT) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }
        if (raw == MODE_SLOT) {
            event.setCancelled(true);
            holder.adminCreationMode = (holder.adminCreationMode + 1) % 2;
            decorate(holder);
            return;
        }
        if (raw == REG_PRIMARY_SLOT && holder.adminCreationMode > 0) {
            event.setCancelled(true);
            holder.smeltRegisterPrimary = !holder.smeltRegisterPrimary;
            decorate(holder);
            return;
        }
        if (raw == BONUS_PLUS && holder.adminCreationMode > 0) {
            event.setCancelled(true);
            holder.bonusChancePercent = Math.min(100, holder.bonusChancePercent + 1);
            decorate(holder);
            return;
        }
        if (raw == BONUS_MINUS && holder.adminCreationMode > 0) {
            event.setCancelled(true);
            holder.bonusChancePercent = Math.max(0, holder.bonusChancePercent - 1);
            decorate(holder);
            return;
        }
        if (raw == 4) {
            event.setCancelled(true);
            holder.knowledgeLevel = Math.max(MIN_KNOW, holder.knowledgeLevel - 1);
            decorate(holder);
            return;
        }
        if (raw == 6) {
            event.setCancelled(true);
            holder.knowledgeLevel = Math.min(MAX_KNOW, holder.knowledgeLevel + 1);
            decorate(holder);
            return;
        }
        if (holder.adminCreationMode == 0) {
            boolean matrix = false;
            for (int s : MATRIX_SLOTS) {
                if (raw == s) {
                    matrix = true;
                    break;
                }
            }
            if (matrix || raw == RESULT_SLOT) {
                event.setCancelled(false);
                return;
            }
        } else {
            if (raw == FURNACE_INPUT_SLOT || raw == FURNACE_RESULT_SLOT) {
                event.setCancelled(false);
                return;
            }
            if (raw == BONUS_TEMPLATE_SLOT) {
                event.setCancelled(false);
                return;
            }
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof CraftAdminHolder holder) {
            for (int raw : event.getRawSlots()) {
                if (raw < 54 && !isEditable(raw, holder)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    private static boolean isEditable(int raw, CraftAdminHolder holder) {
        if (holder.adminCreationMode == 0) {
            if (raw == RESULT_SLOT) {
                return true;
            }
            for (int s : MATRIX_SLOTS) {
                if (s == raw) {
                    return true;
                }
            }
            return false;
        }
        return raw == FURNACE_INPUT_SLOT || raw == FURNACE_RESULT_SLOT || raw == BONUS_TEMPLATE_SLOT;
    }

    private void trySave(Player player, CraftAdminHolder holder) {
        CraftingService service = holder.service();
        ItemStack[] grid = new ItemStack[9];
        Arrays.fill(grid, null);
        ItemStack result;
        if (holder.adminCreationMode == 0) {
            for (int i = 0; i < MATRIX_SLOTS.length; i++) {
                ItemStack it = holder.getInventory().getItem(MATRIX_SLOTS[i]);
                grid[i] = it == null || it.getType().isAir() ? null : it.clone();
            }
            result = holder.getInventory().getItem(RESULT_SLOT);
        } else {
            ItemStack in = holder.getInventory().getItem(FURNACE_INPUT_SLOT);
            grid[0] = in == null || in.getType().isAir() ? null : in.clone();
            result = holder.getInventory().getItem(FURNACE_RESULT_SLOT);
        }
        try {
            CraftRecipeJson json;
            if (holder.adminCreationMode == 0) {
                json = CraftJson.encodeGrid(grid, result, holder.knowledgeLevel, "&fКрафт");
            } else {
                String kind = CraftCreationKinds.FURNACE;
                ItemStack bonusProbe = holder.getInventory().getItem(BONUS_TEMPLATE_SLOT);
                String bonusTid = "";
                if (bonusProbe != null && !bonusProbe.getType().isAir()) {
                    bonusTid = service.templates().findMatchingTemplateId(bonusProbe).orElse("");
                }
                if (holder.bonusChancePercent > 0 && bonusTid.isBlank()) {
                    throw new IllegalArgumentException("Для бонуса > 0 положи в слот 2 предмет из шаблона /itemtemplate");
                }
                json = CraftJson.encodeSmelt(
                    grid,
                    result,
                    holder.knowledgeLevel,
                    "&fПлавка",
                    kind,
                    holder.smeltCookTicks,
                    holder.smeltExperience,
                    holder.smeltRegisterPrimary,
                    bonusTid,
                    holder.bonusChancePercent
                );
            }
            String id = holder.isEditing() ? holder.editingCraftId : ("c_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
            int order = holder.isEditing() ? holder.editingSortOrder : service.allCrafts().size();
            service.saveNewCraft(id, json, order).whenComplete((v, ex) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (ex != null) {
                    player.sendMessage("§cНе удалось сохранить: " + ex.getMessage());
                    return;
                }
                String where = holder.adminCreationMode == 0
                    ? "верстаке"
                    : "печи (все типы и модерн-печь)";
                player.sendMessage(holder.isEditing()
                    ? "§aКрафт обновлён: §f" + id
                    : "§aКрафт сохранён: §f" + id + " §7— в " + where + ", в меню /craft.");
                holder.clearEdit();
                player.closeInventory();
            }));
        } catch (Exception ex) {
            player.sendMessage("§c" + ex.getMessage());
        }
    }
}
