package com.foxaria.api.service;

import com.foxaria.api.model.KitDefinition;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface KitService {

    List<KitDefinition> definitions();

    CompletableFuture<Void> claim(Player player, String kitId);
}
