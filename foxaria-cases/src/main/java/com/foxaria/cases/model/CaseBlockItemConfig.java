package com.foxaria.cases.model;

import org.bukkit.Material;

import java.util.List;

public record CaseBlockItemConfig(Material material, String name, List<String> lore) {
}
