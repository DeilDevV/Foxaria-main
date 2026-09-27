package com.foxaria.core.gui;

import com.foxaria.api.model.BalanceSnapshot;
import com.foxaria.api.service.ConfigService;
import com.foxaria.api.service.EconomyService;
import com.foxaria.api.service.KnowledgeService;
import com.foxaria.api.service.RankService;
import com.foxaria.api.service.ServiceRegistry;
import com.foxaria.core.text.FoxariaText;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Главное меню игрока (/menu). Оптимизированная версия.
 */
public final class PlayerMainMenu extends BaseMenu {

    private final JavaPlugin plugin;
    private final ServiceRegistry services;
    private final FileConfiguration ui;
    private final DecimalFormat balanceFormat = new DecimalFormat("#,##0.00");

    public PlayerMainMenu(JavaPlugin plugin, ServiceRegistry services, ConfigService configs) {
        super(
            configs.module("modules/player-ui.yml")
                .getString("menu.title", MenuStyle.title(MenuStyle.ECONOMY, "FOXARIA", "меню игрока")),
            54
        );
        this.plugin = plugin;
        this.services = services;
        this.ui = configs.module("modules/player-ui.yml");
    }

    @Override
    protected void draw(Player viewer) {
        drawFrame();

        // ─── Ряд 2: перемещения ───
        entry(10, Material.ENDER_PEARL, "&d&lСлучайный заброс", "rtp", "&7Перенос в случайную точку мира.");
        entry(11, Material.OAK_DOOR, "&e&lДома", "homes", "&7Твои личные точки телепорта.");
        entry(12, Material.ENDER_EYE, "&b&lТелепорт к игроку", "tpa", "&7Запрос на ТП к другу.");
        entry(13, Material.CRAFTING_TABLE, "&6&lКрафты", "craft", "&7Уникальные рецепты Foxaria.");
        entry(14, Material.CHEST, "&a&lНаборы", "kits", "&7Стартовые и донатные наборы.");
        entry(15, Material.TRIPWIRE_HOOK, "&e&lКейсы", "crate", "&7Открытие кейсов с лутом.");
        entry(16, Material.KNOWLEDGE_BOOK, "&f&lПомощь", "help", "&7Список всех команд сервера.");

        // ─── Ряд 3: экономика ───
        entry(19, Material.GOLD_NUGGET, "&6&lМагазин", "shop", "&7Покупка предметов за монеты.");
        entry(20, Material.GOLD_INGOT, "&6&lАукцион", "ah", "&7Торговля между игроками.");
        entry(21, Material.EMERALD, "&e&lБаланс", "bal", "&7Твои монеты и токены.");
        
        setItem(22, loadingProfile(viewer), null);

        entry(23, Material.DIAMOND, "&d&lДонат-магазин", "donateshop", "&7Покупки за токены.");
        entry(24, Material.WRITTEN_BOOK, "&5&lКвесты", "quest", "&7Задания и уровень знаний.");
        entry(25, Material.BEACON, "&b&lНаграды", "rewards", "&7Бонусы за время в игре.");

        // ─── Ряд 4: Сообщество ───
        entry(30, Material.SHIELD, "&b&lГильдии", "guild", "&7Кланы, войны и казна.");
        entry(31, Material.SMITHING_TABLE, "&a&lПриваты", "region", "&7Защита твоей территории.");
        entry(32, Material.WRITABLE_BOOK, "&c&lЖалоба", "report", "&7Сообщить о нарушителе.");

        // ─── Нижняя панель ───
        setItem(49, MenuItems.item(Material.NETHER_STAR, "&d&lСеть серверов", "&7Переход между мирами."),
            click -> { viewer.closeInventory(); viewer.performCommand("server"); });

        setItem(53, MenuStyle.closeButton(), click -> viewer.closeInventory());

        loadProfile(viewer);
    }

    private void drawFrame() {
        int size = inventory().getSize();
        for (int i = 0; i < size; i++) {
            int row = i / 9;
            int col = i % 9;
            if (row == 0 || col == 0 || col == 8 || row == 5) setItem(i, MenuItems.filler(), null);
        }
        for (int i = 36; i < 45; i++) setItem(i, MenuStyle.separator(), null);
    }

    private void entry(int slot, Material material, String title, String command, String desc) {
        setItem(slot, MenuItems.item(material, title, new String[]{desc, MenuStyle.divider(), MenuStyle.hintLeft("нажми для /" + command)}),
            click -> { click.getWhoClicked().closeInventory(); ((Player) click.getWhoClicked()).performCommand(command); });
    }

    private ItemStack loadingProfile(Player viewer) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        meta.setOwningPlayer(viewer);
        meta.displayName(FoxariaText.legacy("&6&l" + viewer.getName()));
        meta.lore(List.of(FoxariaText.legacy("&7Загрузка...")));
        skull.setItemMeta(meta);
        return skull;
    }

    private void loadProfile(Player viewer) {
        EconomyService eco = services.optional(EconomyService.class);
        RankService rs = services.optional(RankService.class);
        if (eco == null || rs == null) return;

        eco.balance(viewer.getUniqueId()).thenAccept(snap -> {
            rs.primaryGroup(viewer.getUniqueId()).thenAccept(rank -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!viewer.isOnline()) return;
                    ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                    SkullMeta meta = (SkullMeta) skull.getItemMeta();
                    meta.setOwningPlayer(viewer);
                    meta.displayName(FoxariaText.legacy("&6&l" + viewer.getName()));
                    meta.lore(List.of(
                        FoxariaText.legacy("&8───────────────"),
                        FoxariaText.legacy("&7Ранг: &f" + rank),
                        FoxariaText.legacy("&7Баланс: &a" + balanceFormat.format(snap.balance())),
                        FoxariaText.legacy("&7Токены: &b" + snap.tokens())
                    ));
                    skull.setItemMeta(meta);
                    setItem(22, skull, null);
                });
            });
        });
    }
}
