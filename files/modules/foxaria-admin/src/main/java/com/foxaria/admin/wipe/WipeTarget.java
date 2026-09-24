package com.foxaria.admin.wipe;

import org.bukkit.Material;

import java.util.List;

/**
 * Что именно очищает вайп.
 *
 * Принцип: сносится только игровой прогресс текущего сезона.
 * Донат (токены, купленные ранги), баны и наказания НЕ трогаются никогда —
 * соответствующих таблиц просто нет в списках ниже.
 */
public enum WipeTarget {

    REGIONS(
        "Приваты",
        Material.SMITHING_TABLE,
        "Все регионы, их участники и повреждения ядер",
        List.of("fx_region_damaged_blocks", "fx_region_member_prefs", "fx_region_members", "fx_regions")
    ),

    GUILDS(
        "Гильдии",
        Material.SHIELD,
        "Гильдии, состав, казна, уровни и войны",
        List.of(
            "fx_guild_chest_items", "fx_guild_invites", "fx_guild_level_objective_progress",
            "fx_guild_level_rewards_claimed", "fx_guild_level_rewards", "fx_guild_members",
            "fx_guild_objectives", "fx_guild_roles", "fx_guild_stats", "fx_guild_upgrades",
            "fx_guild_war_activity", "fx_guild_war_arenas", "fx_guild_activity", "fx_guilds"
        )
    ),

    AUCTION(
        "Аукцион",
        Material.GOLD_INGOT,
        "Активные лоты и невыданная почта аукциона",
        List.of("fx_auction_listings", "fx_mailbox_deliveries")
    ),

    MONEY(
        "Монеты",
        Material.GOLD_NUGGET,
        "Обнуляет игровые монеты у всех. Токены доната сохраняются",
        List.of()
    ),

    PLAYER_SHOPS(
        "Магазины игроков",
        Material.BARREL,
        "Лавки игроков и их предложения",
        List.of("fx_player_shop_offers", "fx_player_shops")
    ),

    HOMES(
        "Дома и точки",
        Material.OAK_DOOR,
        "Точки /home и сохранённые места входа",
        List.of("fx_player_homes", "fx_player_entry_points")
    ),

    PROGRESSION(
        "Прогресс и квесты",
        Material.WRITTEN_BOOK,
        "Уровень знаний, квесты, серия входов и награды за время",
        List.of(
            "fx_player_progression", "fx_login_streaks", "fx_playtime_rewards",
            "fx_reward_claims", "fx_vote_claims", "fx_referrals", "fx_kit_claims"
        )
    );

    private final String title;
    private final Material icon;
    private final String description;
    private final List<String> tables;

    WipeTarget(String title, Material icon, String description, List<String> tables) {
        this.title = title;
        this.icon = icon;
        this.description = description;
        this.tables = tables;
    }

    public String title() {
        return title;
    }

    public Material icon() {
        return icon;
    }

    public String description() {
        return description;
    }

    public List<String> tables() {
        return tables;
    }

    /** Монеты обнуляются UPDATE'ом, а не удалением строк (иначе слетят токены). */
    public boolean isMoneyReset() {
        return this == MONEY;
    }
}
