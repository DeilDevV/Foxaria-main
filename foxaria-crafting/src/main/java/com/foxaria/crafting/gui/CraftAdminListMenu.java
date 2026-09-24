package com.foxaria.crafting.gui;

import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.core.text.FoxariaText;
import com.foxaria.crafting.CraftJson;
import com.foxaria.crafting.CraftingService;
import com.foxaria.crafting.CustomCraftDefinition;
import com.foxaria.crafting.admin.CraftAdminHolder;
import com.foxaria.crafting.admin.CraftAdminListener;
import com.foxaria.crafting.admin.CraftAdminUi;
import com.foxaria.crafting.model.CraftRecipeJson;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Список серверных крафтов: ЛКМ — правка, Shift+ПКМ — удаление.
 */
public final class CraftAdminListMenu extends BaseMenu {

    private final JavaPlugin plugin;
    private final MenuManager menus;
    private final CraftingService crafting;

    public CraftAdminListMenu(JavaPlugin plugin, MenuManager menus, CraftingService crafting) {
        super("&6&lКрафты &8| &fсписок", 54);
        this.plugin = plugin;
        this.menus = menus;
        this.crafting = crafting;
    }

    @Override
    protected void draw(Player player) {
        for (int i = 0; i < 54; i++) {
            setItem(i, MenuItems.filler(), null);
        }
        setItem(4, MenuItems.item(Material.CHEST, "&6&lВсе рецепты",
            "&7ЛКМ &f— открыть в редакторе",
            "&7Shift+ПКМ &c— удалить"), null);

        List<CustomCraftDefinition> list = crafting.allCrafts();
        int slot = 10;
        for (CustomCraftDefinition def : list) {
            if (slot > 43) {
                break;
            }
            CraftRecipeJson j = def.parsed();
            ItemStack icon;
            try {
                icon = CraftJson.resolveResult(crafting.templates(), j).clone();
            } catch (Exception ex) {
                icon = new ItemStack(Material.BARRIER);
            }
            icon.setAmount(1);
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                List<Component> lore = new ArrayList<>();
                if (meta.lore() != null && !meta.lore().isEmpty()) {
                    lore.addAll(meta.lore());
                    lore.add(FoxariaText.legacy("&8 "));
                }
                lore.add(FoxariaText.legacy("&8id: &7" + def.craftId()));
                lore.add(FoxariaText.legacy("&7Знаний: &e" + j.requiredKnowledge));
                for (String ln : CraftJson.creationLoreLines(j)) {
                    lore.add(FoxariaText.legacy(ln));
                }
                lore.add(FoxariaText.legacy("&8 "));
                lore.add(FoxariaText.legacy("&aЛКМ &7— редактировать"));
                lore.add(FoxariaText.legacy("&cShift+ПКМ &7— удалить"));
                meta.lore(lore);
                icon.setItemMeta(meta);
            }
            CustomCraftDefinition clickDef = def;
            setItem(slot, icon, e -> {
                if (e.isShiftClick() && e.isRightClick()) {
                    crafting.deleteCraft(clickDef.craftId()).whenComplete((v, ex) ->
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (ex != null) {
                                player.sendMessage("§cНе удалось удалить: " + ex.getMessage());
                                return;
                            }
                            player.sendMessage("§eКрафт удалён: §f" + clickDef.craftId());
                            menus.open(player, new CraftAdminListMenu(plugin, menus, crafting));
                        }));
                    return;
                }
                if (e.isLeftClick() && !e.isShiftClick()) {
                    CraftAdminUi.openForEdit(player, menus, crafting, clickDef);
                }
            });
            slot++;
            if ((slot + 1) % 9 == 0) {
                slot += 2;
            }
        }

        if (list.isEmpty()) {
            setItem(22, MenuItems.item(Material.BARRIER, "&7Рецептов нет"), null);
        }

        setItem(45, MenuItems.item(Material.ARROW, "&7Новый крафт"), e -> {
            CraftAdminHolder h = new CraftAdminHolder(crafting);
            h.clearEdit();
            CraftAdminListener.decorate(h);
            player.openInventory(h.getInventory());
        });

        setItem(49, MenuItems.item(Material.BARRIER, "&cЗакрыть"), e -> player.closeInventory());
    }
}
