package com.foxaria.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class FoxariaPlayerAuthenticatedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final boolean returningPlayer;

    public FoxariaPlayerAuthenticatedEvent(Player player, boolean returningPlayer) {
        this.player = player;
        this.returningPlayer = returningPlayer;
    }

    public Player player() {
        return player;
    }

    public boolean returningPlayer() {
        return returningPlayer;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
