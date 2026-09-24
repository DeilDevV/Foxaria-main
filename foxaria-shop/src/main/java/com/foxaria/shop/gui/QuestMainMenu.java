package com.foxaria.shop.gui;

import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import com.foxaria.core.text.FoxariaText;
import com.foxaria.shop.progression.PlayerProgressionState;
import com.foxaria.shop.progression.ProgressionService;
import com.foxaria.shop.progression.QuestCatalog;
import com.foxaria.shop.progression.QuestDefinition;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class QuestMainMenu extends BaseMenu {

    private static final int[] TIER_SLOTS = {20, 21, 22, 23, 24};
    private static final Material[] TIER_ICONS = {
        Material.OAK_SAPLING,
        Material.STONE,
        Material.IRON_INGOT,
        Material.GOLD_INGOT,
        Material.DIAMOND
    };

    private final ProgressionService progression;

    public QuestMainMenu(ProgressionService progression) {
        super("&5&lЗнания и квесты", 54);
        this.progression = progression;
    }

    @Override
    protected void draw(Player player) {
        for (int i = 0; i < 54; i++) {
            setItem(i, MenuItems.filler(), null);
        }
        for (int i = 36; i < 45; i++) {
            setItem(i, MenuItems.item(Material.PURPLE_STAINED_GLASS_PANE, "&8 "), null);
        }

        PlayerProgressionState state = progression.cachedState(player.getUniqueId());
        QuestCatalog cat = progression.catalog();

        List<String> summaryLines = new ArrayList<>();
        summaryLines.add("&7Пять ступеней. Следующая открывается после 21/21.");
        summaryLines.add("&8 ");
        if (state == null) {
            summaryLines.add("&eЗагрузка данных…");
        } else {
            int maxAll = QuestCatalog.TIER_COUNT * QuestCatalog.QUESTS_PER_TIER;
            int doneAll = cat.totalCompletedQuests(state);
            summaryLines.add("&7Выполнено квестов: &e" + doneAll + "&7/&f" + maxAll);
            QuestDefinition active = cat.activeQuest(state);
            if (active != null) {
                summaryLines.add("&7Сейчас: &r" + active.title());
            } else {
                summaryLines.add("&7Сейчас: &aвсе цели пройдены");
            }
            summaryLines.add("&7Уровень знаний: &d" + state.knowledgeLevel());
            summaryLines.add("&8 ");
            for (int t = 1; t <= QuestCatalog.TIER_COUNT; t++) {
                int done = cat.completedCountInTier(state, t);
                String lock = cat.tierUnlocked(state, t) ? "" : " &8(закрыта)";
                summaryLines.add("&fСтупень " + t + "&7: &e" + done + "&7/&f" + QuestCatalog.QUESTS_PER_TIER + lock);
            }
        }

        setItem(4, MenuItems.item(Material.ENCHANTED_BOOK, "&d&lПуть знаний", summaryLines.toArray(new String[0])), null);

        for (int t = 1; t <= QuestCatalog.TIER_COUNT; t++) {
            int slot = TIER_SLOTS[t - 1];
            int tier = t;
            if (state == null) {
                setItem(slot, MenuItems.item(Material.CLOCK, "&eСтупень " + t, "&7Загрузка…"), null);
                continue;
            }
            boolean open = cat.tierUnlocked(state, t);
            Material icon = open ? TIER_ICONS[t - 1] : Material.BARRIER;
            List<String> lore = new ArrayList<>(cat.tierMenuDescription(t));
            lore.add("&8 ");
            lore.add("&7Прогресс: &e" + cat.completedCountInTier(state, t) + "&7/&f" + QuestCatalog.QUESTS_PER_TIER);
            if (!open) {
                lore.add("&cСначала завершите предыдущую ступень.");
            } else {
                lore.add("&a▶ Открыть квесты этой ступени");
            }
            ItemStack stack = new ItemStack(icon);
            ItemMeta meta = stack.getItemMeta();
            meta.displayName(FoxariaText.legacy(cat.tierMenuTitle(t)));
            meta.lore(FoxariaText.legacyLore(lore));
            stack.setItemMeta(meta);
            setItem(slot, stack, e -> {
                PlayerProgressionState st = progression.cachedState(player.getUniqueId());
                if (st != null && progression.catalog().tierUnlocked(st, tier)) {
                    progression.openQuestTierMenu(player, tier);
                }
            });
        }

        setItem(31, MenuItems.item(Material.GOLD_INGOT, "&6&lМагазин за монеты", new String[]{
            "&7Товары от уровня знаний",
            "&a▶ /shop"
        }), e -> player.performCommand("shop"));

        setItem(33, MenuItems.item(Material.AMETHYST_SHARD, "&d&lДонат", new String[]{
            "&7Токены — &f/token",
            "&a▶ /donateshop"
        }), e -> player.performCommand("donateshop"));

        setItem(45, MenuItems.item(Material.BARRIER, "&cЗакрыть", "&7Закрыть"), e -> player.closeInventory());
    }
}
