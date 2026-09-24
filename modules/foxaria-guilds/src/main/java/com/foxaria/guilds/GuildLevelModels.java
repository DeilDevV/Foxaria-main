package com.foxaria.guilds;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.List;

public final class GuildLevelModels {

    private GuildLevelModels() {
    }

    public enum GuildObjectiveType {
        BLOCK_BREAK,
        SUBMIT_ITEMS,
        ENTITY_KILL;

        public static GuildObjectiveType parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return BLOCK_BREAK;
            }
            try {
                return GuildObjectiveType.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return BLOCK_BREAK;
            }
        }
    }

    public record GuildObjectiveDef(GuildObjectiveType type, String materialKey, String entityKey, int amount) {

        public String progressLine(long current) {
            String key = type == GuildObjectiveType.ENTITY_KILL ? entityKey : materialKey;
            String label = switch (type) {
                case BLOCK_BREAK -> "&7Сломать: &f" + key + " &8× &f" + Math.min(current, amount) + "&7/&f" + amount;
                case SUBMIT_ITEMS -> "&7Сдать в общий сундук: &f" + key + " &8× &f" + Math.min(current, amount) + "&7/&f" + amount;
                case ENTITY_KILL -> "&7Убить: &f" + key + " &8× &f" + Math.min(current, amount) + "&7/&f" + amount;
            };
            return label;
        }

        public boolean matchesBlockBreak(Material mat) {
            return type == GuildObjectiveType.BLOCK_BREAK
                && mat != null
                && materialKey != null
                && mat.name().equalsIgnoreCase(materialKey);
        }

        public boolean matchesSubmitMaterial(Material mat) {
            return type == GuildObjectiveType.SUBMIT_ITEMS
                && mat != null
                && materialKey != null
                && mat.name().equalsIgnoreCase(materialKey);
        }

        public boolean matchesEntity(String entityTypeName) {
            return type == GuildObjectiveType.ENTITY_KILL
                && entityKey != null
                && entityTypeName != null
                && entityTypeName.equalsIgnoreCase(entityKey);
        }
    }

    public record TemplateRewardEntry(String templateId, int amount) {
    }

    public record LevelRewardBundle(
        BigDecimal moneyToBank,
        List<ItemStack> plainItems,
        List<TemplateRewardEntry> templateEntries
    ) {
        public static LevelRewardBundle empty() {
            return new LevelRewardBundle(BigDecimal.ZERO, List.of(), List.of());
        }

        public boolean hasAny() {
            return moneyToBank.compareTo(BigDecimal.ZERO) > 0
                || !plainItems.isEmpty()
                || !templateEntries.isEmpty();
        }
    }
}
