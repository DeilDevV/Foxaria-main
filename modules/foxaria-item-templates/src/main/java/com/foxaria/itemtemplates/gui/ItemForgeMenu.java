package com.foxaria.itemtemplates.gui;

import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import com.foxaria.core.gui.MenuStyle;
import com.foxaria.itemtemplates.ability.AbilityCodec;
import com.foxaria.itemtemplates.ability.AbilityInstance;
import com.foxaria.itemtemplates.ability.AbilityLoreRenderer;
import com.foxaria.itemtemplates.ability.ItemAbility;
import com.foxaria.itemtemplates.ability.ItemRarity;
import com.foxaria.itemtemplates.session.ItemTemplateWorkbenchSession;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Кузница предметов — единственный экран редактора.
 *
 * Чем отличается от старого верстака (1053 строки, 8 вложенных панелей):
 *  - предмет из руки виден прямо в меню и обновляется после каждого клика,
 *    поэтому не нужно выходить и заходить, чтобы посмотреть результат;
 *  - все действия происходят без закрытия инвентаря;
 *  - способности настраиваются на самой иконке (ЛКМ/ПКМ/Shift), без
 *    перехода в отдельные экраны;
 *  - описание пересобирается целиком из PDC, дубли строк невозможны.
 */
public final class ItemForgeMenu extends BaseMenu {

    private static final int SLOT_PREVIEW = 4;
    private static final int[] CONTENT_SLOTS = {
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34
    };

    private final JavaPlugin plugin;
    private final ItemAbility.Slot tab;

    public ItemForgeMenu(JavaPlugin plugin) {
        this(plugin, ItemAbility.Slot.MELEE);
    }

    public ItemForgeMenu(JavaPlugin plugin, ItemAbility.Slot tab) {
        super("&8⟨ &6&lКузница &8│ &f" + tab.title() + " &8⟩", 54);
        this.plugin = plugin;
        this.tab = tab;
    }

    @Override
    protected void draw(Player viewer) {
        ItemStack hand = viewer.getInventory().getItemInMainHand();
        boolean hasItem = hand != null && !hand.getType().isAir();

        drawFrame();
        drawPreview(viewer, hand, hasItem);
        drawTabs(viewer);

        if (!hasItem) {
            setItem(31, MenuItems.item(Material.BARRIER,
                "&c&lВозьми предмет в руку",
                "&7Кузница работает с предметом",
                "&7в главной руке.",
                MenuStyle.divider(),
                "&8Меч, лук, броня, инструмент — что угодно."), null);
            drawFooter(viewer, false);
            return;
        }

        // Разовый перенос описания со старого редактора.
        AbilityCodec.migrateLegacyLore(plugin, hand);

        List<ItemAbility> abilities = ItemAbility.bySlot(tab);
        for (int i = 0; i < CONTENT_SLOTS.length && i < abilities.size(); i++) {
            ItemAbility ability = abilities.get(i);
            setItem(CONTENT_SLOTS[i], abilityIcon(hand, ability), click -> handleAbilityClick(viewer, ability, click.getClick()));
        }

        drawFooter(viewer, true);
    }

    // ── Превью предмета ──────────────────────────────────────────────

    private void drawPreview(Player viewer, ItemStack hand, boolean hasItem) {
        if (!hasItem) {
            setItem(SLOT_PREVIEW, MenuItems.item(Material.ITEM_FRAME,
                "&7&lПредмет не выбран", "&8Возьми что-нибудь в руку"), null);
            return;
        }
        // Показываем настоящий предмет: сразу видно имя, описание и чары.
        ItemStack preview = hand.clone();
        setItem(SLOT_PREVIEW, preview, null);

        ItemRarity rarity = AbilityCodec.rarity(plugin, hand);
        int count = AbilityCodec.read(plugin, hand).size();
        setItem(13, MenuItems.item(Material.NAME_TAG,
            rarity.color() + "&lРедкость: " + rarity.title(),
            "&7Подсвечивает предмет в описании.",
            MenuStyle.divider(),
            "&7Особых свойств: &f" + count,
            MenuStyle.divider(),
            MenuStyle.hintLeft("следующая редкость"),
            MenuStyle.hintRight("предыдущая"),
            "&bShift+ЛКМ &8→ &7подобрать по числу свойств"
        ), click -> {
            ItemStack item = viewer.getInventory().getItemInMainHand();
            if (item == null || item.getType().isAir()) {
                return;
            }
            ItemRarity current = AbilityCodec.rarity(plugin, item);
            ItemRarity next = switch (click.getClick()) {
                case RIGHT, SHIFT_RIGHT -> current.previous();
                case SHIFT_LEFT -> ItemRarity.suggest(AbilityCodec.read(plugin, item).size());
                default -> current.next();
            };
            AbilityCodec.setRarity(plugin, item, next);
            AbilityLoreRenderer.apply(plugin, item);
            refresh(viewer);
        });
    }

    // ── Вкладки ──────────────────────────────────────────────────────

    private void drawTabs(Player viewer) {
        int[] slots = {1, 2, 3, 5, 6};
        ItemAbility.Slot[] tabs = ItemAbility.Slot.values();
        for (int i = 0; i < tabs.length && i < slots.length; i++) {
            ItemAbility.Slot value = tabs[i];
            boolean active = value == tab;
            setItem(slots[i], MenuItems.item(
                active ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
                (active ? "&a&l▶ " : "&7") + value.title(),
                "&7Способностей: &f" + ItemAbility.bySlot(value).size(),
                MenuStyle.divider(),
                active ? "&8Текущая вкладка" : MenuStyle.hintLeft("открыть")
            ), click -> {
                if (!active) {
                    ItemForgeMenu menu = new ItemForgeMenu(plugin, value);
                    menu.render(viewer);
                    viewer.openInventory(menu.inventory());
                }
            });
        }
    }

    // ── Иконка способности ───────────────────────────────────────────

    private ItemStack abilityIcon(ItemStack hand, ItemAbility ability) {
        Optional<AbilityInstance> active = AbilityCodec.read(plugin, hand).stream()
            .filter(instance -> instance.ability() == ability)
            .findFirst();

        List<String> lore = new ArrayList<>();
        lore.add("&7" + ability.description());
        lore.add(MenuStyle.divider());

        if (active.isPresent()) {
            AbilityInstance instance = active.get();
            lore.add("&a✔ Включено");
            if (ability.usesChance()) {
                lore.add("&7Шанс: &f" + instance.chance() + "%");
            }
            if (ability.usesPower()) {
                lore.add("&7" + capitalize(ability.powerLabel()) + ": &f" + instance.power());
            }
            if (ability.usesSeconds()) {
                lore.add("&7Длительность: &f" + instance.seconds() + " сек");
            }
            lore.add(MenuStyle.divider());
            lore.add("&eЛКМ &8→ &7выключить");
            if (ability.usesChance()) {
                lore.add("&6ПКМ &8→ &7шанс &a+5%&7 / &bShift+ПКМ &8→ &c−5%");
            }
            if (ability.usesPower()) {
                lore.add("&6Колесо/Q &8→ &7" + ability.powerLabel() + " &a+1&7 / &bShift &8→ &c−1");
            }
            if (ability.usesSeconds()) {
                lore.add("&6Двойной клик &8→ &7время &a+1с&7 / &bShift &8→ &c−1с");
            }
        } else {
            lore.add("&8○ Выключено");
            lore.add(MenuStyle.divider());
            lore.add(MenuStyle.hintLeft("включить способность"));
        }

        String title = (active.isPresent() ? ability.slot().color() + "&l" : "&8")
            + ability.title();
        return MenuItems.item(active.isPresent() ? ability.icon() : Material.GRAY_DYE, title,
            lore.toArray(new String[0]));
    }

    /**
     * Все действия над способностью — на одной иконке, без вложенных экранов.
     */
    private void handleAbilityClick(Player viewer, ItemAbility ability, ClickType click) {
        ItemStack item = viewer.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            return;
        }
        Optional<AbilityInstance> current = AbilityCodec.read(plugin, item).stream()
            .filter(instance -> instance.ability() == ability)
            .findFirst();

        if (current.isEmpty()) {
            AbilityCodec.toggle(plugin, item, ability);
            viewer.sendMessage("§aДобавлено: §f" + ability.title());
        } else {
            AbilityInstance instance = current.get();
            switch (click) {
                case LEFT -> {
                    AbilityCodec.toggle(plugin, item, ability);
                    viewer.sendMessage("§7Убрано: §f" + ability.title());
                }
                case RIGHT -> AbilityCodec.update(plugin, item, instance.withChance(instance.chance() + 5));
                case SHIFT_RIGHT -> AbilityCodec.update(plugin, item, instance.withChance(instance.chance() - 5));
                case DROP -> AbilityCodec.update(plugin, item, instance.withPower(instance.power() + 1));
                case CONTROL_DROP -> AbilityCodec.update(plugin, item, instance.withPower(instance.power() - 1));
                case DOUBLE_CLICK -> AbilityCodec.update(plugin, item, instance.withSeconds(instance.seconds() + 1));
                case SHIFT_LEFT -> AbilityCodec.update(plugin, item, instance.withSeconds(instance.seconds() - 1));
                default -> {
                    return;
                }
            }
        }
        AbilityLoreRenderer.apply(plugin, item);
        refresh(viewer);
    }

    // ── Нижняя панель ────────────────────────────────────────────────

    private void drawFooter(Player viewer, boolean hasItem) {
        setItem(45, MenuItems.item(Material.OAK_SIGN, "&f&lИмя предмета",
            "&7Задать своё название с цветами.",
            MenuStyle.divider(),
            "&8Цвета: &f&&6 &8— золотой, &f&&c &8— красный",
            MenuStyle.hintLeft("ввести в чат")
        ), click -> {
            if (!hasItem) {
                return;
            }
            ItemTemplateWorkbenchSession.get(viewer).chatPrompt =
                ItemTemplateWorkbenchSession.ChatPrompt.DISPLAY_NAME;
            viewer.closeInventory();
            viewer.sendMessage("§eВведи название в чат (или §fотмена§e):");
        });

        setItem(46, MenuItems.item(Material.WRITABLE_BOOK, "&f&lОписание",
            "&7Свои строки описания сверх способностей.",
            MenuStyle.divider(),
            "&7Сейчас строк: &f" + (hasItem
                ? AbilityCodec.userLore(plugin, viewer.getInventory().getItemInMainHand()).size() : 0),
            MenuStyle.hintLeft("добавить строку"),
            MenuStyle.hintRight("удалить последнюю")
        ), click -> {
            if (!hasItem) {
                return;
            }
            ItemStack item = viewer.getInventory().getItemInMainHand();
            if (click.getClick() == ClickType.RIGHT) {
                AbilityCodec.removeLastUserLoreLine(plugin, item);
                AbilityLoreRenderer.apply(plugin, item);
                refresh(viewer);
                return;
            }
            ItemTemplateWorkbenchSession.get(viewer).chatPrompt =
                ItemTemplateWorkbenchSession.ChatPrompt.LORE_LINE;
            viewer.closeInventory();
            viewer.sendMessage("§eВведи строку описания (или §fотмена§e):");
        });

        setItem(47, MenuItems.item(Material.ANVIL, "&e&lСтарый редактор",
            "&7Полный верстак: атрибуты, зачарования,",
            "&7зелья, YAML и шаблоны в БД.",
            MenuStyle.divider(),
            "&8Команда: &f/itemtemplate workbench"
        ), click -> {
            viewer.closeInventory();
            viewer.performCommand("itemtemplate workbench");
        });

        setItem(48, MenuItems.item(Material.HOPPER, "&c&lСбросить свойства",
            "&7Убирает все особые способности",
            "&7и возвращает обычную редкость.",
            MenuStyle.divider(),
            "&8Имя и описание остаются."
        ), click -> {
            if (!hasItem) {
                return;
            }
            ItemStack item = viewer.getInventory().getItemInMainHand();
            AbilityCodec.clearAbilities(plugin, item);
            AbilityCodec.setRarity(plugin, item, ItemRarity.COMMON);
            AbilityLoreRenderer.apply(plugin, item);
            refresh(viewer);
            viewer.sendMessage("§7Особые свойства убраны.");
        });

        setItem(50, MenuItems.item(Material.EMERALD, "&a&lСохранить как шаблон",
            "&7Сохраняет предмет в базу, чтобы выдавать",
            "&7его через донат-магазин, кейсы и наборы.",
            MenuStyle.divider(),
            "&8Команда: &f/itemtemplate save <id>"
        ), null);

        setItem(49, MenuStyle.refreshButton(), click -> refresh(viewer));
        setItem(53, MenuStyle.closeButton(), click -> viewer.closeInventory());
    }

    /** Перерисовка без закрытия инвентаря — предмет в превью обновляется сразу. */
    private void refresh(Player viewer) {
        render(viewer);
        viewer.updateInventory();
    }

    private void drawFrame() {
        for (int slot = 0; slot < 54; slot++) {
            int row = slot / 9;
            int col = slot % 9;
            if (row == 0 || col == 0 || col == 8 || row == 5) {
                setItem(slot, MenuItems.filler(), null);
            }
        }
        for (int slot = 36; slot < 45; slot++) {
            setItem(slot, MenuStyle.separator(), null);
        }
    }

    private String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
