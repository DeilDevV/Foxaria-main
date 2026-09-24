package com.foxaria.core.staff;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Настройки из {@code modules/moderation.yml}, секция {@code staff-panels}.
 * <p>По умолчанию панели включены (игровой сервер). На лобби поставьте
 * {@code enabled: false} и при желании {@code register-commands: false}, чтобы команды
 * не отвечали и не светились в табе.</p>
 */
public final class StaffPanelSettings {

    private StaffPanelSettings() {
    }

    public static boolean panelsEnabled(FileConfiguration moderationModule) {
        if (moderationModule == null) {
            return true;
        }
        return moderationModule.getBoolean("staff-panels.enabled", true);
    }

    /**
     * Если {@code false}, {@code /modpanel} и {@code /adminpanel} регистрируются как «тихие заглушки»
     * (без сообщений и таб-комплита) — удобно для лобби.
     */
    public static boolean registerPanelCommands(FileConfiguration moderationModule) {
        if (moderationModule == null) {
            return true;
        }
        return moderationModule.getBoolean("staff-panels.register-commands", true);
    }
}
