package com.foxaria.shop.progression;

import com.foxaria.api.service.EconomyService;
import com.foxaria.api.service.ItemTemplateService;
import com.foxaria.api.service.MessageService;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class QuestRewards {

    private final BigDecimal money;
    private final List<ItemReward> items;
    private final List<TemplateReward> templates;

    public QuestRewards(BigDecimal money, List<ItemReward> items, List<TemplateReward> templates) {
        this.money = money == null ? BigDecimal.ZERO : money;
        this.items = items == null ? List.of() : List.copyOf(items);
        this.templates = templates == null ? List.of() : List.copyOf(templates);
    }

    public static QuestRewards empty() {
        return new QuestRewards(BigDecimal.ZERO, List.of(), List.of());
    }

    public static QuestRewards fromConfig(ConfigurationSection rewardsRoot) {
        if (rewardsRoot == null) {
            return empty();
        }
        BigDecimal money = BigDecimal.ZERO;
        try {
            String raw = rewardsRoot.getString("money", "0");
            if (raw != null && !raw.isBlank()) {
                money = new BigDecimal(raw.trim()).setScale(2, RoundingMode.HALF_UP);
            }
        } catch (Exception ignored) {
            money = BigDecimal.ZERO;
        }
        List<ItemReward> items = new ArrayList<>();
        List<?> itemList = rewardsRoot.getList("items");
        if (itemList != null) {
            for (Object o : itemList) {
                if (!(o instanceof Map<?, ?> map)) {
                    continue;
                }
                Object mat = map.get("material");
                Object amt = map.get("amount");
                if (mat == null) {
                    continue;
                }
                int amount = 1;
                if (amt instanceof Number n) {
                    amount = Math.max(1, n.intValue());
                } else if (amt != null) {
                    try {
                        amount = Math.max(1, Integer.parseInt(amt.toString()));
                    } catch (NumberFormatException ignored) {
                        amount = 1;
                    }
                }
                items.add(new ItemReward(mat.toString(), amount));
            }
        }
        List<TemplateReward> tpl = new ArrayList<>();
        List<?> tplList = rewardsRoot.getList("templates");
        if (tplList != null) {
            for (Object o : tplList) {
                if (!(o instanceof Map<?, ?> map)) {
                    continue;
                }
                Object id = map.get("id");
                if (id == null) {
                    continue;
                }
                Object amt = map.get("amount");
                int amount = 1;
                if (amt instanceof Number n) {
                    amount = Math.max(1, n.intValue());
                } else if (amt != null) {
                    try {
                        amount = Math.max(1, Integer.parseInt(amt.toString()));
                    } catch (NumberFormatException ignored) {
                        amount = 1;
                    }
                }
                tpl.add(new TemplateReward(id.toString(), amount));
            }
        }
        return new QuestRewards(money, items, tpl);
    }

    public boolean hasAny() {
        return money.compareTo(BigDecimal.ZERO) > 0 || !items.isEmpty() || !templates.isEmpty();
    }

    /**
     * Вызывать с основного потока.
     */
    public void grant(
        Player player,
        JavaPlugin plugin,
        EconomyService economy,
        ItemTemplateService itemTemplates,
        MessageService messages
    ) {
        UUID uuid = player.getUniqueId();
        if (money.compareTo(BigDecimal.ZERO) > 0 && economy != null) {
            BigDecimal give = money;
            economy.deposit(uuid, give, "quest_reward", null).thenRun(() ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        messages.send(player, "quest.reward-money", "&a+&e<money> &aмонет за квест",
                            new MessageService.Placeholder("money", give.toPlainString()));
                    }
                })
            );
        }
        Map<Integer, ItemStack> overflow = new HashMap<>();
        for (ItemReward ir : items) {
            Material mat;
            try {
                mat = Material.valueOf(ir.materialName().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Quest reward: unknown material " + ir.materialName());
                continue;
            }
            ItemStack stack = new ItemStack(mat, ir.amount());
            overflow.putAll(player.getInventory().addItem(stack));
        }
        if (itemTemplates != null) {
            for (TemplateReward tr : templates) {
                if (!itemTemplates.exists(tr.templateId())) {
                    plugin.getLogger().warning("Quest reward: template not found " + tr.templateId());
                    continue;
                }
                for (int i = 0; i < tr.amount(); i++) {
                    itemTemplates.cloneTemplate(tr.templateId()).ifPresent(stack ->
                        overflow.putAll(player.getInventory().addItem(stack)));
                }
            }
        }
        overflow.values().forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
        if (!items.isEmpty() || !templates.isEmpty()) {
            messages.send(player, "quest.reward-items", "&aПредметы из награды добавлены в инвентарь (или на землю).");
        }
    }

    public List<String> rewardSummaryLore(ItemTemplateService itemTemplates) {
        List<String> lines = new ArrayList<>();
        if (money.compareTo(BigDecimal.ZERO) > 0) {
            lines.add("&7Монеты: &e" + money.toPlainString());
        }
        for (ItemReward ir : items) {
            lines.add("&7Предмет: &f" + ir.materialName() + " &8×&f" + ir.amount());
        }
        for (TemplateReward tr : templates) {
            boolean ok = itemTemplates != null && itemTemplates.exists(tr.templateId());
            lines.add("&7Особый предмет: &f" + tr.templateId() + " &8×&f" + tr.amount()
                + (ok ? "" : " &c(нет шаблона)"));
        }
        if (lines.isEmpty()) {
            lines.add("&7В конфиге квеста нет наград (rewards:).");
        }
        return lines;
    }

    public record ItemReward(String materialName, int amount) {
    }

    public record TemplateReward(String templateId, int amount) {
    }
}
