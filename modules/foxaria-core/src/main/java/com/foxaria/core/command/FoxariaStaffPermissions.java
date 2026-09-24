package com.foxaria.core.command;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

/**
 * Проверка прав модерации/админки. Игроки с {@code /op} на Paper получают доступ к нодам
 * {@code foxaria.mod.*} и {@code foxaria.admin.*} даже без LuckPerms (как в одиночном тесте).
 */
public final class FoxariaStaffPermissions {

    private FoxariaStaffPermissions() {
    }

    public static boolean hasOperatorStaffBypass(CommandSender sender, String permission) {
        if (permission == null || permission.isBlank()) {
            return false;
        }
        if (sender instanceof ConsoleCommandSender) {
            return true;
        }
        if (!(sender instanceof Player player) || !player.isOp()) {
            return false;
        }
        return permission.startsWith("foxaria.mod.")
            || permission.startsWith("foxaria.admin.");
    }

    public static boolean has(CommandSender sender, String permission) {
        if (permission == null || permission.isBlank()) {
            return true;
        }
        if (hasOperatorStaffBypass(sender, permission)) {
            return true;
        }
        return sender.hasPermission(permission);
    }
}
