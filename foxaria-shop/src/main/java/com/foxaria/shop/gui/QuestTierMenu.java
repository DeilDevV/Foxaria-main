package com.foxaria.shop.gui;

import com.foxaria.api.service.ItemTemplateService;
import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import com.foxaria.core.text.FoxariaText;
import com.foxaria.shop.progression.PlayerProgressionState;
import com.foxaria.shop.progression.ProgressionService;
import com.foxaria.shop.progression.QuestCatalog;
import com.foxaria.shop.progression.QuestDefinition;
import com.foxaria.shop.progression.QuestObjective;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class QuestTierMenu extends BaseMenu {

    private static final int[] ROW_BASE = {10, 19, 28};

    private final ProgressionService progression;
    private final int tier;

    public QuestTierMenu(ProgressionService progression, int tier) {
        super(progression.catalog().tierMenuTitle(tier) + " &8| &7квесты", 54);
        this.progression = progression;
        this.tier = tier;
    }

    @Override
    protected void draw(Player player) {
        for (int i = 0; i < 54; i++) {
            setItem(i, MenuItems.filler(), null);
        }
        for (int i = 36; i < 45; i++) {
            setItem(i, MenuItems.item(Material.PURPLE_STAINED_GLASS_PANE, "&8 "), null);
        }

        QuestCatalog cat = progression.catalog();
        PlayerProgressionState state = progression.cachedState(player.getUniqueId());
        ItemTemplateService tpl = progression.itemTemplates();

        List<String> headLore = new ArrayList<>();
        headLore.add("&7В одном квесте может быть несколько целей.");
        headLore.add("&7Следующие две ячейки вы видите заранее.");
        if (state != null) {
            headLore.add("&8 ");
            headLore.add("&7Прогресс ступени: &e" + cat.completedCountInTier(state, tier)
                + "&7/&f" + QuestCatalog.QUESTS_PER_TIER);
            if (!cat.tierUnlocked(state, tier)) {
                headLore.add("&cЭта ступень пока закрыта.");
            }
        }
        setItem(4, MenuItems.item(Material.BOOK, "&d&lСтупень " + tier, headLore.toArray(new String[0])), null);

        List<QuestDefinition> quests = cat.tierQuests(tier);
        for (int i = 0; i < QuestCatalog.QUESTS_PER_TIER; i++) {
            int row = i / 7;
            int col = i % 7;
            int slot = ROW_BASE[row] + col;
            if (state == null) {
                setItem(slot, MenuItems.item(Material.CLOCK, "&e…", "&7Загрузка"), null);
                continue;
            }
            QuestDefinition def = quests.get(i);
            QuestCatalog.QuestSlotState st = cat.slotState(state, tier, i);
            int[] progress = state.objectiveProgressArray(def);
            boolean needSubmitClick = st == QuestCatalog.QuestSlotState.ACTIVE
                && def.hasAnySubmitObjective()
                && def.hasIncompleteSubmit(progress);

            Material mat;
            String nameLegacy;
            switch (st) {
                case TIER_LOCKED -> {
                    mat = Material.BARRIER;
                    nameLegacy = "&8№" + (i + 1) + " &7— ступень закрыта";
                }
                case LOCKED -> {
                    mat = Material.GRAY_STAINED_GLASS_PANE;
                    nameLegacy = "&8№" + (i + 1) + " &8— скоро";
                }
                case NEXT_PREVIEW -> {
                    mat = Material.YELLOW_STAINED_GLASS_PANE;
                    nameLegacy = "&e№" + (i + 1) + " &r" + def.title();
                }
                case ACTIVE -> {
                    mat = needSubmitClick ? Material.CHEST : Material.WRITABLE_BOOK;
                    nameLegacy = "&6&l№" + (i + 1) + " &r" + def.title();
                }
                case DONE -> {
                    mat = Material.LIME_STAINED_GLASS_PANE;
                    nameLegacy = "&a№" + (i + 1) + " &a✓ &r" + def.title();
                }
                default -> {
                    mat = Material.BARRIER;
                    nameLegacy = "&c?";
                }
            }

            List<String> lore = new ArrayList<>();
            if (st == QuestCatalog.QuestSlotState.ACTIVE) {
                lore.addAll(def.descriptionLines());
                lore.add("&8 ");
                for (int oi = 0; oi < def.objectives().size(); oi++) {
                    QuestObjective o = def.objectives().get(oi);
                    lore.add("&r" + o.summaryForLore(progress[oi]));
                }
                if (needSubmitClick) {
                    lore.add("&e▶ ЛКМ &7— сдать предметы (частями, по очереди целей)");
                } else {
                    lore.add("&7Прогресс в мире обновляется сам.");
                }
                lore.add("&8 ");
                lore.add("&7Награды:");
                for (String r : def.rewards().rewardSummaryLore(tpl)) {
                    lore.add(r);
                }
            } else if (st == QuestCatalog.QuestSlotState.NEXT_PREVIEW) {
                lore.add("&7Скоро после текущего задания.");
                lore.add("&8 ");
                lore.addAll(def.descriptionLines());
            } else if (st == QuestCatalog.QuestSlotState.LOCKED) {
                lore.add("&7Сначала пройдите предыдущие задания.");
                lore.add("&7Открывается по три цели подряд.");
            } else if (st == QuestCatalog.QuestSlotState.TIER_LOCKED) {
                lore.add("&7Завершите прошлую ступень целиком.");
            } else if (st == QuestCatalog.QuestSlotState.DONE) {
                lore.add("&aВыполнено.");
            }

            ItemStack stack = new ItemStack(mat);
            ItemMeta meta = stack.getItemMeta();
            meta.displayName(FoxariaText.legacy(nameLegacy));
            if (!lore.isEmpty()) {
                meta.lore(FoxariaText.legacyLore(lore));
            }
            stack.setItemMeta(meta);
            if (needSubmitClick) {
                setItem(slot, stack, (InventoryClickEvent e) -> {
                    if (e.getWhoClicked() instanceof Player p) {
                        progression.trySubmitItems(p, tier);
                    }
                });
            } else {
                setItem(slot, stack, null);
            }
        }

        setItem(45, MenuItems.item(Material.ARROW, "&7◀ Назад", "&7К списку ступеней"), e -> progression.openQuestMenu(player));
        setItem(49, MenuItems.item(Material.BARRIER, "&cЗакрыть", "&7Закрыть"), e -> player.closeInventory());
    }
}
