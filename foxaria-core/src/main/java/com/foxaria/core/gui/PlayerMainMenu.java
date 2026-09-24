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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class PlayerMainMenu extends BaseMenu {

    private final JavaPlugin plugin;
    private final ServiceRegistry services;
    private final FileConfiguration ui;
    private final DecimalFormat balanceFormat = new DecimalFormat("#,##0.00");

    public PlayerMainMenu(JavaPlugin plugin, ServiceRegistry services, ConfigService configs) {
        super(
            configs.module("modules/player-ui.yml").getString("menu.title", "&6&lЦентр Foxaria"),
            configs.module("modules/player-ui.yml").getInt("menu.rows", 6) * 9
        );
        this.plugin = plugin;
        this.services = services;
        this.ui = configs.module("modules/player-ui.yml");
    }

    @Override
    protected void draw(Player viewer) {
        int size = inventory().getSize();
        for (int i = 0; i < size; i++) {
            setItem(i, MenuItems.filler(), null);
        }

        int sepStart = ui.getInt("menu.separator-slot-start", 36);
        for (int i = sepStart; i < sepStart + 9 && i < size; i++) {
            setItem(i, MenuItems.item(Material.BLACK_STAINED_GLASS_PANE, "&8 "), null);
        }

        button(10, Material.PAPER, "&e&lБаланс", List.of(
            "&7Монеты: &f/bal",
            "&7Токены: &b/token",
            "&7Донат-магазин: &d/donateshop"
        ), () -> viewer.performCommand("bal"));

        button(11, Material.COMPASS, "&6&lСпавн", List.of(
            "&7Безопасная точка",
            "&a▶ /spawn"
        ), () -> viewer.performCommand("spawn"));

        button(12, Material.ENDER_PEARL, "&d&lСлучайный ТП", List.of(
            "&7Случайная точка в мире",
            "&a▶ /rtp"
        ), () -> viewer.performCommand("rtp"));

        setItem(13, loadingProfile(viewer), null);

        button(14, Material.CHEST, "&a&lНаборы", List.of(
            "&7Просмотр и получение",
            "&a▶ /kits"
        ), () -> viewer.performCommand("kits"));

        button(15, Material.EMERALD, "&d&lДонат-магазин", List.of(
            "&7Покупка за &bтокены",
            "&a▶ /donateshop"
        ), () -> viewer.performCommand("donateshop"));

        button(16, Material.GOLD_NUGGET, "&6&lЗа монеты", List.of(
            "&7Магазин за монеты",
            "&7Часть товаров от &dуровня знаний&7.",
            "&a▶ /shop  &7|  &f/quest"
        ), () -> viewer.performCommand("shop"));

        button(19, Material.GOLD_INGOT, "&6&lАукцион", List.of(
            "&7Лоты и почта",
            "&a▶ /ah"
        ), () -> viewer.performCommand("ah"));

        button(20, Material.BEACON, "&b&lНаграды", List.of(
            "&7За время в игре",
            "&a▶ /rewards"
        ), () -> viewer.performCommand("rewards"));

        button(21, Material.OAK_DOOR, "&6&lДома", List.of(
            "&7Точки телепорта",
            "&a▶ /homes"
        ), () -> viewer.performCommand("homes"));

        button(22, Material.NETHER_STAR, "&d&lСерверы", List.of(
            "&7Сеть Foxaria",
            "&a▶ /server"
        ), () -> viewer.performCommand("server"));

        button(23, Material.TOTEM_OF_UNDYING, "&e&lСерия входов", List.of(
            "&7Ежедневный бонус",
            "&a▶ /streak"
        ), () -> viewer.performCommand("streak"));

        button(24, Material.SMITHING_TABLE, "&7&lПриват", List.of(
            "&7Регионы",
            "&a▶ /region"
        ), () -> viewer.performCommand("region"));

        button(25, Material.SHIELD, "&b&lГильдии", List.of(
            "&7Клан и войны",
            "&a▶ /guild"
        ), () -> viewer.performCommand("guild"));

        // Ряд 4: крафты слева, зазор, знания/квесты правее (слоты 29–33 без кнопок — «воздух» под будущие пункты)
        button(28, Material.CRAFTING_TABLE, "&6&lКрафты", List.of(
            "&7Серверные рецепты в верстаке",
            "&a▶ /craft"
        ), () -> viewer.performCommand("craft"));

        button(34, Material.WRITTEN_BOOK, "&d&lЗнания и квесты", List.of(
            "&7Цепочка заданий и уровень знаний",
            "&a▶ /quest"
        ), () -> viewer.performCommand("quest"));

        int bottom = size - 9;
        List<String> helpLines = new ArrayList<>(ui.getStringList("menu.help-lore"));
        if (helpLines.isEmpty()) {
            helpLines.add("&7Разделы — кнопками вокруг профиля.");
        }
        setItem(bottom, MenuItems.item(Material.WRITABLE_BOOK, "&e&lКоманды", List.of(
            "&7/menu &f— это меню",
            "&7/bal &f— монеты",
            "&7/token &f— токены",
            "&7/donateshop &f— донат за токены",
            "&7/shop &f— магазин за монеты",
            "&7/craft &f— крафты",
            "&7/quest &f— квесты и знания"
        ).toArray(new String[0])), null);

        setItem(bottom + 1, MenuItems.item(Material.BOOK, "&f&lПодсказки", helpLines.toArray(new String[0])), null);

        setItem(bottom + 4, MenuItems.item(Material.DIAMOND, "&b&lДонат", List.of(
            "&7Токены и наборы — на сайте Foxaria.",
            "&7Подробности в Discord и на сайте."
        ).toArray(new String[0])), e -> viewer.sendMessage("§7Донат: §fсайт сервера §7или §fDiscord§7. Токены начисляются после оплаты."));

        setItem(bottom + 8, MenuItems.item(Material.BARRIER, "&c&lЗакрыть", "&7Закрыть меню"), e -> viewer.closeInventory());

        loadProfile(viewer);
    }

    private void button(int slot, Material mat, String title, List<String> lore, Runnable action) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(FoxariaText.legacy(title));
        meta.lore(FoxariaText.legacyLore(lore));
        stack.setItemMeta(meta);
        setItem(slot, stack, e -> action.run());
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
                setItem(13, item, null);
                inventory().setItem(13, item);
            })
        );
    }

    private ItemStack loadingProfile(Player viewer) {
        return profileItem(viewer, List.of(
            FoxariaText.plain("Загрузка…")
        ));
    }

    private ItemStack createProfileItem(Player viewer, ProfileState state) {
        List<Component> lore = new ArrayList<>();
        lore.add(FoxariaText.legacy("&7Ранг: &f&l" + humanizeRank(state.rank())));
        lore.add(FoxariaText.legacy("&7Знания: &d" + Math.max(1, state.knowledgeLevel())));
        lore.add(FoxariaText.legacy("&7Монеты: &e" + balanceFormat.format(state.balance().balance())));
        lore.add(FoxariaText.legacy("&7Токены: &b" + state.balance().tokens()));
        lore.add(FoxariaText.legacy("&7Онлайн: &a" + state.onlineCount()));
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
