package com.foxaria.guilds;

import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public final class GuildModels {

    private GuildModels() {
    }

    public record GuildRecord(
        String id,
        String name,
        UUID ownerUuid,
        long createdAt,
        BigDecimal bankBalance,
        long guildCoins,
        long guildPoints,
        int level,
        int chestRows,
        int shopTier,
        int memberSlotsBonus,
        String motd,
        boolean friendlyFire,
        String tagColor
    ) {
    }

    public record GuildMemberRecord(
        String guildId,
        UUID playerUuid,
        String playerName,
        String role,
        long joinedAt
    ) {
    }

    public record GuildChestSnapshot(String guildId, int rows, Map<Integer, ItemStack> items) {
    }
}
