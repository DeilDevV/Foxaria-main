package com.foxaria.admin.listener;

import com.foxaria.admin.AdminState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

public final class AdminListener implements Listener {

    private final AdminState state;

    public AdminListener(AdminState state) {
        this.state = state;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (state.maintenance()) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "Сервер находится на технических работах.");
        }
    }
}
