package com.foxaria.crafting.gui;

import com.foxaria.api.service.KnowledgeService;
import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.core.text.FoxariaText;
import com.foxaria.crafting.CraftJson;
import com.foxaria.crafting.CraftingService;
import com.foxaria.crafting.CustomCraftDefinition;
import com.foxaria.crafting.model.CraftRecipeJson;
import com.foxaria.regions.RegionConfig;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CraftPlayerMenu extends BaseMenu {

    private final MenuManager menus;
    private final CraftingService crafting;
    private final RegionConfig regionConfig;
    private final KnowledgeService knowledge;

    public CraftPlayerMenu(
        MenuManager menus,
        CraftingService crafting,
        RegionConfig regionConfig,
        KnowledgeService knowledge
    ) {
        super("&6&lКрафты &8| &fFoxaria", 54);
        this.menus = menus;
        this.crafting = crafting;
        this.regionConfig = regionConfig;
        this.knowledge = knowledge;
    }

    @Override
    protected void draw(Player player) {
        for (int i = 0; i < 54; i++) {
            setItem(i, MenuItems.filler(), null);
        }

        setItem(4, MenuItems.item(Material.CRAFTING_TABLE, "&6&lКрафты Foxaria",
            "&7Верстак или печь — подпись у каждого рецепта.",
            "&7Нажми на предмет ниже — откроется схема."), null);

        /** Первый предмет списка: отступ 1 слот сверху и слева от «рамки» сетки (слот 10). */
        int slot = 10;
        Optional<CustomCraftDefinition> builtinSulfur = crafting.builtinSulfurDefinition();
        if (builtinSulfur.isPresent()) {
            CustomCraftDefinition def = builtinSulfur.get();
            if (slot < 45) {
                ItemStack icon = crafting.templates().cloneTemplate("sulfur").orElseGet(() -> new ItemStack(Material.BARRIER));
                icon.setAmount(1);
                ItemMeta meta = icon.getItemMeta();
                if (meta != null) {
                    List<Component> lore = new ArrayList<>();
                    if (meta.lore() != null && !meta.lore().isEmpty()) {
                        lore.addAll(meta.lore());
                        lore.add(FoxariaText.legacy("&8 "));
                    }
                    lore.add(FoxariaText.legacy("&7Мин. знаний: &e1"));
                    lore.add(FoxariaText.legacy("&8 "));
                    lore.add(FoxariaText.legacy("&7Создание: в печи"));
                    lore.add(FoxariaText.legacy("&8(&7Все печи: обычная, плавильная, модерн&8)"));
                    lore.add(FoxariaText.legacy("&8 "));
                    lore.add(FoxariaText.legacy("&e▶ ЛКМ &7— открыть рецепт"));
                    meta.lore(lore);
                    icon.setItemMeta(meta);
                }
                CustomCraftDefinition clickDef = def;
                setItem(slot, icon, e -> menus.open(player, new CraftRecipeDetailMenu(menus, crafting, regionConfig, knowledge, clickDef)));
                slot++;
                if ((slot + 1) % 9 == 0) {
                    slot += 2;
                }
            }
        }

        List<CustomCraftDefinition> crafts = crafting.allCrafts();
        for (CustomCraftDefinition def : crafts) {
            if (slot >= 45) {
                break;
            }
            if (crafting.isRedundantDbSulfurCraft(def)) {
                continue;
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
                // Имя и модель — как у реального результата крафта; в лор только подсказки меню
                List<Component> lore = new ArrayList<>();
                if (meta.lore() != null && !meta.lore().isEmpty()) {
                    lore.addAll(meta.lore());
                    lore.add(FoxariaText.legacy("&8 "));
                }
                lore.add(FoxariaText.legacy("&7Мин. знаний: &e" + j.requiredKnowledge));
                lore.add(FoxariaText.legacy("&8 "));
                for (String ln : CraftJson.creationLoreLines(j)) {
                    lore.add(FoxariaText.legacy(ln));
                }
                lore.add(FoxariaText.legacy("&8 "));
                lore.add(FoxariaText.legacy("&e▶ ЛКМ &7— открыть рецепт"));
                meta.lore(lore);
                icon.setItemMeta(meta);
            }
            CustomCraftDefinition clickDef = def;
            setItem(slot, icon, e -> menus.open(player, new CraftRecipeDetailMenu(menus, crafting, regionConfig, knowledge, clickDef)));
            slot++;
            if ((slot + 1) % 9 == 0) {
                slot += 2;
            }
        }

        if (crafts.isEmpty() && crafting.builtinSulfurDefinition().isEmpty()) {
            setItem(31, MenuItems.item(Material.BARRIER, "&7Пока нет крафтов"), null);
        }

        for (int i = 45; i < 54; i++) {
            setItem(i, MenuItems.item(Material.PURPLE_STAINED_GLASS_PANE, "&8 "), null);
        }

        List<String> dynLore = new ArrayList<>();
        dynLore.add("&7Динамит рейда ломает блоки в чужих приватах.");
        dynLore.add("&8 ");
        for (int t = 1; t <= 4; t++) {
            int dmg = regionConfig.dynamiteDamage(t);
            dynLore.add("&7Уровень &f" + t + "&7: урон по блокам &c" + dmg);
        }
        setItem(46, MenuItems.item(Material.TNT, "&cДинамит рейда", dynLore.toArray(new String[0])), null);

        setItem(49, MenuItems.item(Material.ARROW, "&7Закрыть"), e -> player.closeInventory());
    }
}
