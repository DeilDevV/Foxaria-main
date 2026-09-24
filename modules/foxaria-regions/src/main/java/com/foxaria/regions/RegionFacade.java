package com.foxaria.regions;

import com.foxaria.api.service.EconomyService;
import com.foxaria.api.service.MessageService;
import com.foxaria.api.service.RegionInteractionGuard;
import com.foxaria.core.gui.MenuManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

public record RegionFacade(
    JavaPlugin plugin,
    RegionConfig config,
    RegionRepository repo,
    RegionManager manager,
    EconomyService economy,
    RegionDamageService damage,
    MessageService messages,
    MenuManager menus
) implements RegionInteractionGuard {

    @Override
    public boolean allowsForeignBlockUse(Player player, Location blockLocation) {
        Optional<RegionRecord> reg = manager.findContaining(blockLocation);
        if (reg.isEmpty()) {
            return true;
        }
        // Вызывается из обработчиков блоков — блокирующий join() тут недопустим.
        return repo.isMemberCached(reg.get().id(), player.getUniqueId());
    }
}
