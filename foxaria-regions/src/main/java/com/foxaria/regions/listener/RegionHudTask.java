package com.foxaria.regions.listener;

import com.foxaria.regions.RegionFacade;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class RegionHudTask extends BukkitRunnable {

    private final RegionHudListener hud;

    public RegionHudTask(JavaPlugin plugin, RegionHudListener hud) {
        this.hud = hud;
    }

    @Override
    public void run() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            hud.tick(p);
        }
    }
}
