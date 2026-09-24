package com.foxaria.admin.gui;

import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/**
 * Справочник команд Foxaria прямо в админ-панели —
 * чтобы не держать в голове весь список.
 */
public final class CommandsReferenceMenu extends BaseMenu {

    private final Consumer<Player> back;

    public CommandsReferenceMenu(Consumer<Player> back) {
        super("&8⟨ &e&lКоманды &8⟩", 54);
        this.back = back;
    }

    @Override
    protected void draw(Player viewer) {
        fillStaffDecorFrame();

        setItem(4, MenuItems.item(
            Material.KNOWLEDGE_BOOK,
            "&e&lСправочник команд",
            "&7Всё, что умеет Foxaria — по разделам.",
            "&8───────────────",
            "&7Наведись на раздел, чтобы увидеть команды."
        ), null);

        setItem(19, MenuItems.item(
            Material.NETHER_STAR,
            "&4&lАдминистрирование",
            "&f/adminpanel &8— эта панель",
            "&f/foxreload &8— перечитать конфиги",
            "&f/maintenance &8— режим техработ",
            "&f/restartcountdown &8— отсчёт до рестарта",
            "&f/auditlog &8— журнал действий персонала",
            "&f/permdebug &8— диагностика прав"
        ), null);

        setItem(20, MenuItems.item(
            Material.SUNFLOWER,
            "&6&lДонат и экономика",
            "&f/fdonate &8— токены и донат-ранги",
            "&f/eco &8— выдача/списание валюты",
            "&f/token &8— управление токенами",
            "&f/bal&7, &f/pay&7, &f/baltop",
            "&f/donateshop &8— донат-магазин",
            "&f/storegrant &8— выдача покупки с сайта"
        ), null);

        setItem(21, MenuItems.item(
            Material.IRON_SWORD,
            "&c&lМодерация",
            "&f/modpanel &8— панель модератора",
            "&f/punish&7, &f/unpunish &8— санкции",
            "&f/ban &f/tempban &f/unban &f/kick",
            "&f/mute &f/tempmute &f/unmute &f/warn",
            "&f/freeze &f/unfreeze &f/check &f/uncheck",
            "&f/history &f/note &f/report"
        ), null);

        setItem(22, MenuItems.item(
            Material.ENDER_EYE,
            "&5&lПерсонал",
            "&f/staffchat &8— чат персонала",
            "&f/socialspy&7, &f/commandspy",
            "&f/vanish &8— скрытность",
            "&f/invsee&7, &f/echest &8— просмотр (админ)",
            "&f/security &8— инциденты",
            "&f/grimhook &8— античит-хук"
        ), null);

        setItem(29, MenuItems.item(
            Material.GRASS_BLOCK,
            "&a&lИгровые системы",
            "&f/kit &f/kits &f/kitadmin",
            "&f/shop &f/quest &f/adminquestshop",
            "&f/ah &8— аукцион",
            "&f/crate &f/rewards &f/streak &f/voteclaim",
            "&f/refer &f/season"
        ), null);

        setItem(30, MenuItems.item(
            Material.SHIELD,
            "&b&lГильдии и регионы",
            "&f/guild &7(&f/g&7) &8— гильдии",
            "&f/region &f/regionadmin &8— приваты",
            "&f/raid &8— рейды",
            "&f/craft &f/adminfoxcraft &8— кастом-крафт",
            "&f/adminfurnaces &8— печи",
            "&f/itemtemplate &7(&f/itpl&7) &8— шаблоны предметов"
        ), null);

        setItem(31, MenuItems.item(
            Material.COMPASS,
            "&3&lИгрокам",
            "&f/menu &8— меню сервера",
            "&f/rtp &f/home &f/sethome &f/homes",
            "&f/tpa &f/tpaccept &f/tpdeny &f/tpahere",
            "&f/server &8— выбор сервера",
            "&f/help &8— помощь"
        ), null);

        setItem(32, MenuItems.item(
            Material.PAPER,
            "&7&lПодсказка",
            "&7У каждой команды есть таб-подсказки.",
            "&7Права описаны в &fplugin.yml&7,",
            "&7группы — в &fmodules/ranks.yml&7."
        ), null);

        if (back != null) {
            setItem(49, MenuItems.item(Material.ARROW, "&7&lНазад", "&7Вернуться в админ-панель"),
                click -> back.accept(viewer));
        }
    }
}
