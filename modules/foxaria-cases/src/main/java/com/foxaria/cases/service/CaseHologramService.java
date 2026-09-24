package com.foxaria.cases.service;

import com.foxaria.cases.model.CaseDefinition;
import com.foxaria.cases.model.CaseLocation;
import com.foxaria.core.text.FoxariaText;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CaseHologramService {

    private final ConfigCaseService config;

    public CaseHologramService(ConfigCaseService config) {
        this.config = config;
    }

    public List<UUID> spawn(CaseLocation location, CaseDefinition definition, World world) {
        List<String> lines = definition.hologram().lines();
        if (lines.isEmpty()) {
            return List.of();
        }
        double baseY = location.y() + definition.hologram().offsetY();
        double spacing = config.hologramLineSpacing();
        List<UUID> ids = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            final String line = lines.get(index);
            Location spawnAt = new Location(
                world,
                location.x() + 0.5D,
                baseY + (lines.size() - 1 - index) * spacing,
                location.z() + 0.5D
            );
            TextDisplay display = world.spawn(spawnAt, TextDisplay.class, entity -> {
                entity.text(FoxariaText.legacy(line));
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setSeeThrough(true);
                entity.setDefaultBackground(false);
                entity.setShadowed(true);
                entity.setPersistent(false);
            });
            ids.add(display.getUniqueId());
        }
        return List.copyOf(ids);
    }

    public void despawn(World world, List<UUID> entityIds) {
        if (entityIds == null || entityIds.isEmpty()) {
            return;
        }
        for (UUID id : entityIds) {
            Entity entity = world.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
    }
}
