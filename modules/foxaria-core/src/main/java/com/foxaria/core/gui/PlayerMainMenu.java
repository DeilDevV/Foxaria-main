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
 * Главное меню игрока (/menu).
 *
 * Разложено по смысловым рядам, чтобы не было «каши» и пустых дыр:
 *   ряд 2 — перемещения      (спавн, RTP, дома, заявки TPA)
 *   ряд 3 — экономика        (профиль-голова в центре, магазины, аукцион, донат)
 *   ряд 4 — прогресс         (квесты, крафты, наборы, кейсы)
 *   ряд 5 — сообщество       (гильдии, приваты, награды/серия, сезон, рефералы)
 *   ряд 6 — навигация        (команды, помощь, сеть серверов, закрыть)
 *
 * Раньше половина слотов пустовала («воздух под будущие пункты»), в меню не было
 * кейсов, голосования, рефералов, сезона и заявок на телепорт, а кнопка «Донат»
 * внизу дублировала «Донат-магазин» и просто писала текст в чат.
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
        entry(10, Material.ENDER_PEARL, "&d&lСлучайный заброс", "rtp",
            "&7Переносит в случайную точку мира:",
            "&7подальше от чужих баз и приватов.",
            "&8Общего спавна на сервере нет.");

        entry(11, Material.OAK_DOOR, "&e&lДома", "homes",
            "&7Личные точки телепорта.",
            "&8Поставить: &f/sethome <имя>",
            "&8Удалить: &f/delhome <имя>");

        entry(12, Material.ENDER_EYE, "&b&lТелепорт к игроку", "tpa",
            "&7Заявка на телепорт к другому игроку.",
            "&8Принять: &f/tpaccept &8· отклонить: &f/tpdeny",
            "&8Позвать к себе: &f/tpahere <ник>");

        entry(13, Material.CRAFTING_TABLE, "&6&lКрафты", "craft",
            "&7Серверные рецепты Foxaria",
            "&7и особые предметы в верстаке.");

        entry(14, Material.CHEST, "&a&lНаборы", "kits",
            "&7Стартовые и донатные наборы.",
            "&8Кулдаун у каждого свой.");

        entry(15, Material.TRIPWIRE_HOOK, "&e&lКейсы", "crate",
            "&7Открытие кейсов и просмотр наград.",
            "&8Ключи — за активность и на сайте.");

        entry(16, Material.KNOWLEDGE_BOOK, "&f&lПомощь", "help",
            "&7Список доступных команд Foxaria",
            "&7с коротким описанием.");

        // ─── Ряд 3: профиль и экономика ───
        entry(19, Material.GOLD_NUGGET, "&6&lМагазин за монеты", "shop",
            "&7Покупка предметов за монеты.",
            "&8Ассортимент растёт от &dуровня знаний&8.");

        entry(20, Material.GOLD_INGOT, "&6&lАукцион", "ah",
            "&7Торговля между игроками:",
            "&7выставляй лоты и забирай почту.");

        entry(21, Material.EMERALD, "&e&lБаланс", "bal",
            "&7Монеты и токены на счету.",
            "&8Перевод: &f/pay <ник> <сумма>",
            "&8Топ богатых: &f/baltop");

        setItem(22, loadingProfile(viewer), null);

        entry(23, Material.DIAMOND, "&d&lДонат-магазин", "donateshop",
            "&7Покупка за &bтокены&7: наборы,",
            "&7косметика и привилегии.",
            "&8Токены пополняются на сайте.");

        entry(24, Material.WRITTEN_BOOK, "&5&lКвесты и знания", "quest",
            "&7Цепочка заданий по ступеням.",
            "&7Повышает &dуровень знаний&7 —",
            "&7он открывает товары в &f/shop&7.");

        entry(25, Material.BEACON, "&b&lНаграды за время", "rewards",
            "&7Бонусы за время, проведённое в игре.");

        // ─── Ряд 4: сообщество и территория ───
        entry(28, Material.SHIELD, "&b&lГильдии", "guild",
            "&7Создание клана, состав, казна,",
            "&7уровни и войны гильдий.",
            "&8Кратко: &f/g");

        entry(29, Material.SMITHING_TABLE, "&a&lПриваты", "region",
            "&7Ядро привата: защита территории,",
            "&7доступы и флаги региона.");

        entry(30, Material.TOTEM_OF_UNDYING, "&e&lСерия входов", "streak",
            "&7Ежедневный бонус за заходы подряд.",
            "&8Пропуск дня сбрасывает серию.");

        entry(31, Material.PAPER, "&a&lГолосование", "voteclaim",
            "&7Забрать награду за голос",
            "&7на мониторингах серверов.");

        entry(32, Material.PLAYER_HEAD, "&6&lРефералы", "refer",
            "&7Приведи друга и получи бонус.",
            "&8Активировать код: &f/refer <ник>");

        entry(33, Material.CLOCK, "&d&lСезон", "season",
            "&7Текущий сезон: прогресс,",
            "&7сроки и сезонные награды.");

        entry(34, Material.WRITABLE_BOOK, "&c&lЖалоба", "report",
            "&7Пожаловаться на нарушителя.",
            "&8Использование: &f/report <ник> <причина>",
            "&8Ложные жалобы наказуемы.");

        // ─── Нижняя панель ───
        setItem(45, MenuItems.item(Material.KNOWLEDGE_BOOK, "&e&lВсе команды",
            "&7Основное, что стоит запомнить:",
            MenuStyle.divider(),
            "&f/menu &8— это меню",
            "&f/rtp &f/homes &f/tpa &8— перемещения",
            "&f/bal &f/pay &f/baltop &8— экономика",
            "&f/shop &f/donateshop &f/ah &8— покупки",
            "&f/kits &f/crate &f/rewards &f/streak &8— награды",
            "&f/quest &f/craft &8— прогресс и рецепты",
            "&f/guild &f/region &8— клан и приват",
            "&f/server &f/report &8— сеть и жалобы"), null);

        List<String> helpLines = new ArrayList<>(ui.getStringList("menu.help-lore"));
        if (helpLines.isEmpty()) {
            helpLines = List.of(
                "&7Наводи на кнопки — там описание",
                "&7и команда раздела.",
                MenuStyle.divider(),
                "&7Не нашёл нужного? Полный список —",
                "&7в кнопке «Все команды» слева.");
        }
        setItem(46, MenuItems.item(Material.BOOK, "&f&lПодсказки", helpLines.toArray(new String[0])), null);

        setItem(49, MenuItems.item(Material.NETHER_STAR, "&d&lСеть серверов",
            "&7Переход между серверами Foxaria.",
            "&7Сейчас онлайн: &f" + plugin.getServer().getOnlinePlayers().size(),
            MenuStyle.divider(),
            MenuStyle.hintLeft("открыть список серверов")),
            click -> {
                viewer.closeInventory();
                viewer.performCommand("server");
            });

        setItem(53, MenuStyle.closeButton(), click -> viewer.closeInventory());

        loadProfile(viewer);
    }

    /** Рамка меню: серое стекло по периметру и тёмная полоса над нижней панелью. */
    private void drawFrame() {
        int size = inventory().getSize();
        for (int slot = 0; slot < size; slot++) {
            int row = slot / 9;
            int col = slot % 9;
            if (row == 0 || col == 0 || col == 8) {
                setItem(slot, MenuItems.filler(), null);
            }
        }
        for (int slot = 36; slot < 45; slot++) {
            setItem(slot, MenuStyle.separator(), null);
        }
        for (int slot = 45; slot < size; slot++) {
            if (inventory().getItem(slot) == null) {
                setItem(slot, MenuItems.filler(), null);
            }
        }
    }

    /** Кнопка раздела: заголовок, описание, разделитель и строка команды. */
    private void entry(int slot, Material material, String title, String command, String... description) {
        List<String> lore = new ArrayList<>(List.of(description));
        lore.add(MenuStyle.divider());
        lore.add(MenuStyle.hintLeft("открыть &f/" + command));
        setItem(slot, MenuItems.item(material, title, lore.toArray(new String[0])), click -> {
            click.getWhoClicked().closeInventory();
            ((Player) click.getWhoClicked()).performCommand(command);
        });
    }

    private void loadProfile(Player viewer) {
        EconomyService economyService = services.optional(EconomyService.class);
        RankService rankService = services.optional(RankService.class);
        KnowledgeService knowledgeService = services.optional(KnowledgeService.class);
        java.util.UUID playerUuid = viewer.getUniqueId();
        int onlineCount = plugin.getServer().getOnlinePlayers().size();

        CompletableFuture<BalanceSnapshot> balanceFuture = economyService == null
            ? CompletableFuture.completedFuture(new BalanceSnapshot(playerUuid, BigDecimal.ZERO, 0L, 0L))
            : economyService.balance(playerUuid).exceptionally(ignored ->
                new BalanceSnapshot(playerUuid, BigDecimal.ZERO, 0L, 0L)
            );

        CompletableFuture<String> rankFuture = rankService == null
            ? CompletableFuture.completedFuture("default")
            : rankService.primaryGroup(playerUuid).exceptionally(ignored -> "default");

        CompletableFuture<Integer> knowledgeFuture = knowledgeService == null
            ? CompletableFuture.completedFuture(1)
            : knowledgeService.knowledgeLevel(playerUuid).exceptionally(ignored -> 1);

        CompletableFuture.allOf(balanceFuture, rankFuture, knowledgeFuture).thenAccept(ignored ->
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!viewer.isOnline()) {
                    return;
                }
                ProfileState state = new ProfileState(
                    balanceFuture.join(),
                    rankFuture.join(),
                    onlineCount,
                    knowledgeFuture.join()
                );
                ItemStack item = createProfileItem(viewer, state);
                setItem(22, item, null);
                inventory().setItem(22, item);
            })
        );
    }

    private ItemStack loadingProfile(Player viewer) {
        return profileItem(viewer, List.of(FoxariaText.legacy("&8Загрузка профиля…")));
    }

    private ItemStack createProfileItem(Player viewer, ProfileState state) {
        List<Component> lore = new ArrayList<>();
        lore.add(FoxariaText.legacy("&7Ранг: &f&l" + humanizeRank(state.rank())));
        lore.add(FoxariaText.legacy("&7Уровень знаний: &d" + Math.max(1, state.knowledgeLevel())));
        lore.add(FoxariaText.legacy(MenuStyle.divider()));
        lore.add(FoxariaText.legacy("&7Монеты: &e" + balanceFormat.format(state.balance().balance())));
        lore.add(FoxariaText.legacy("&7Токены: &b" + state.balance().tokens()));
        lore.add(FoxariaText.legacy(MenuStyle.divider()));
        lore.add(FoxariaText.legacy("&7Игроков онлайн: &a" + state.onlineCount()));
        return profileItem(viewer, lore);
    }

    private ItemStack profileItem(Player viewer, List<Component> lore) {
        ItemStack itemStack = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) itemStack.getItemMeta();
        meta.setOwningPlayer(viewer);
        meta.displayName(FoxariaText.legacy("&6&l" + viewer.getName()));
        meta.lore(lore);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private String humanizeRank(String group) {
        return switch ((group == null ? "default" : group).toLowerCase(Locale.ROOT)) {
            case "supporter" -> "Поддержка";
            case "vip" -> "VIP";
            case "elite" -> "Элита";
            case "helper" -> "Хелпер";
            case "moderator" -> "Модератор";
            case "admin" -> "Админ";
            default -> "Игрок";
        };
    }

    private record ProfileState(BalanceSnapshot balance, String rank, int onlineCount, int knowledgeLevel) {
    }
}
