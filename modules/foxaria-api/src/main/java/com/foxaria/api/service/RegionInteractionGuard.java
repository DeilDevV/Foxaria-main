package com.foxaria.api.service;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Проверка: можно ли игроку взаимодействовать с блоком в этой точке (свой приват или вне зоны).
 */
public interface RegionInteractionGuard {

    /**
     * @return {@code true}, если вне привата или игрок — участник; {@code false} для чужого региона.
     */
    boolean allowsForeignBlockUse(Player player, Location blockLocation);
}
