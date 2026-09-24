package com.foxaria.cases.model;

import org.bukkit.Material;

import java.util.List;

public record CaseReward(
    String id,
    String displayName,
    int weight,
    Material icon,
    String rarity,
    List<String> menuLore,
    List<RewardCommandGroup> commandGroups
) {
}
