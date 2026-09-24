package com.foxaria.core.gui;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Единый стиль всех меню Foxaria.
 *
 * До этого заголовки были в семи разных форматах ("&6&lАукцион &7| &fлоты",
 * "&8Ядро привата", "&eПросмотр: ..."), а кнопки «назад/закрыть» стояли
 * где придётся. Здесь собраны общие элементы, чтобы все экраны выглядели
 * одинаково: заголовок, подсказки в описании и нижняя панель навигации.
 */
public final class MenuStyle {

    /** Акцентные цвета разделов. */
    public static final String ECONOMY = "&6";
    public static final String DONATE = "&d";
    public static final String SOCIAL = "&b";
    public static final String PROGRESS = "&5";
    public static final String STAFF = "&4";
    public static final String DANGER = "&c";
    public static final String NEUTRAL = "&f";

    /** Слоты нижней панели в меню на 54 ячейки. */
    public static final int SLOT_BACK = 45;
    public static final int SLOT_INFO = 49;
    public static final int SLOT_CLOSE = 53;

    private MenuStyle() {
    }

    /** Заголовок: &8⟨ &6&lНазвание &8⟩ */
    public static String title(String accent, String name) {
        return "&8⟨ " + accent + "&l" + name + " &8⟩";
    }

    /** Заголовок с подразделом: &8⟨ &6&lНазвание &8│ &fподраздел &8⟩ */
    public static String title(String accent, String name, String sub) {
        return "&8⟨ " + accent + "&l" + name + " &8│ &f" + sub + " &8⟩";
    }

    /** Разделительная линия в описании предмета. */
    public static String divider() {
        return "&8▬▬▬▬▬▬▬▬▬▬▬▬▬";
    }

    /** Подсказка действия: &eЛКМ &8→ &7текст */
    public static String hintLeft(String text) {
        return "&eЛКМ &8→ &7" + text;
    }

    public static String hintRight(String text) {
        return "&6ПКМ &8→ &7" + text;
    }

    public static String hintShift(String text) {
        return "&bShift+ЛКМ &8→ &7" + text;
    }

    /** Строка команды: &8▸ &f/команда */
    public static String command(String command) {
        return "&8▸ &f/" + command;
    }

    public static ItemStack backButton() {
        return MenuItems.item(Material.ARROW, "&7&lНазад", "&8Вернуться на предыдущий экран");
    }

    public static ItemStack closeButton() {
        return MenuItems.item(Material.BARRIER, "&c&lЗакрыть", "&8Закрыть меню");
    }

    public static ItemStack refreshButton() {
        return MenuItems.item(Material.ENDER_EYE, "&d&lОбновить", "&8Перечитать данные");
    }

    /** Тёмная панель-разделитель. */
    public static ItemStack separator() {
        return MenuItems.item(Material.BLACK_STAINED_GLASS_PANE, "&8");
    }
}
