package com.foxaria.moderation.command;

import com.foxaria.api.service.AuditService;
import com.foxaria.api.service.ConfigService;
import com.foxaria.api.service.MessageService;
import com.foxaria.api.service.SecurityService;
import com.foxaria.core.command.FoxariaStaffPermissions;
import com.foxaria.core.command.StaffCommandInput;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.core.staff.StaffPanelSettings;
import com.foxaria.moderation.JdbcModerationService;
import com.foxaria.moderation.gui.ModerationDashboardMenu;
import com.foxaria.moderation.gui.PlayerProfileMenu;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ModerationPanelCommand implements CommandExecutor {

    private final JdbcModerationService moderationService;
    private final SecurityService securityService;
    private final AuditService auditService;
    private final MenuManager menuManager;
    private final MessageService messages;
    private final ConfigService configs;

    public ModerationPanelCommand(JdbcModerationService moderationService, SecurityService securityService, AuditService auditService, MenuManager menuManager, MessageService messages, ConfigService configs) {
        this.moderationService = moderationService;
        this.securityService = securityService;
        this.auditService = auditService;
        this.menuManager = menuManager;
        this.messages = messages;
        this.configs = configs;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.players-only", "&cЭту команду могут использовать только игроки.");
            return true;
        }
        if (!FoxariaStaffPermissions.has(sender, "foxaria.mod.panel")) {
            messages.send(sender, "general.no-permission", "&cУ вас нет прав.");
            return true;
        }
        if (!StaffPanelSettings.panelsEnabled(configs.module("modules/moderation.yml"))) {
            messages.send(sender, "mod.panels-unavailable", "&7Панели персонала отключены на этом узле.");
            return true;
        }

        StaffCommandInput input = StaffCommandInput.parse(args);
        if (input.args().length > 0) {
            Player target = Bukkit.getPlayerExact(input.args()[0]);
            if (target == null) {
                messages.send(sender, "general.player-not-found", "&cИгрок не найден.");
                return true;
            }
            menuManager.open(player, new PlayerProfileMenu(target.getUniqueId(), moderationService, securityService, auditService, null));
            return true;
        }

        menuManager.open(player, new ModerationDashboardMenu(moderationService, securityService, auditService));
        return true;
    }
}
