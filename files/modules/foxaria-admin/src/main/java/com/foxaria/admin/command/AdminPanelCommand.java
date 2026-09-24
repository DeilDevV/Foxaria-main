package com.foxaria.admin.command;

import com.foxaria.admin.gui.AdminDashboardMenu;
import com.foxaria.admin.gui.PlayerAdminMenu;
import com.foxaria.api.service.AuditService;
import com.foxaria.api.service.ConfigService;
import com.foxaria.api.service.EconomyService;
import com.foxaria.api.service.MessageService;
import com.foxaria.api.service.ModerationService;
import com.foxaria.api.service.RankService;
import com.foxaria.api.service.SecurityService;
import com.foxaria.core.command.FoxariaStaffPermissions;
import com.foxaria.core.command.StaffCommandInput;
import com.foxaria.admin.wipe.WipeService;
import com.foxaria.core.gui.MenuManager;
import org.bukkit.plugin.java.JavaPlugin;
import com.foxaria.core.staff.StaffPanelSettings;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class AdminPanelCommand implements CommandExecutor {

    private final MenuManager menuManager;
    private final EconomyService economyService;
    private final ModerationService moderationService;
    private final SecurityService securityService;
    private final RankService rankService;
    private final AuditService auditService;
    private final MessageService messages;
    private final ConfigService configs;
    private final JavaPlugin plugin;
    private final WipeService wipeService;

    public AdminPanelCommand(MenuManager menuManager, EconomyService economyService, ModerationService moderationService, SecurityService securityService, RankService rankService, AuditService auditService, MessageService messages, ConfigService configs, JavaPlugin plugin, WipeService wipeService) {
        this.menuManager = menuManager;
        this.economyService = economyService;
        this.moderationService = moderationService;
        this.securityService = securityService;
        this.rankService = rankService;
        this.auditService = auditService;
        this.messages = messages;
        this.configs = configs;
        this.plugin = plugin;
        this.wipeService = wipeService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("auditlog")) {
            return handleAudit(sender, args);
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.players-only", "&cЭту команду могут использовать только игроки.");
            return true;
        }
        if (!FoxariaStaffPermissions.has(sender, "foxaria.admin.panel")) {
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
            menuManager.open(player, new PlayerAdminMenu(target, economyService, moderationService, securityService, rankService, auditService));
            return true;
        }

        menuManager.open(player, new AdminDashboardMenu(economyService, moderationService, securityService, rankService, auditService, plugin, wipeService));
        return true;
    }

    private boolean handleAudit(CommandSender sender, String[] args) {
        if (!FoxariaStaffPermissions.has(sender, "foxaria.admin.auditlog")) {
            messages.send(sender, "general.no-permission", "&cУ вас нет прав.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.players-only", "&cЭту команду могут использовать только игроки.");
            return true;
        }
        StaffCommandInput input = StaffCommandInput.parse(args);
        if (input.args().length < 1) {
            messages.send(sender, "admin.audit-usage", "&cИспользование: /auditlog <игрок> [-s]");
            return true;
        }
        Player target = Bukkit.getPlayerExact(input.args()[0]);
        if (target == null) {
            messages.send(sender, "general.player-not-found", "&cИгрок не найден.");
            return true;
        }
        auditService.recent(target.getUniqueId(), 10).thenAccept(events -> events.forEach(event ->
            player.sendMessage(Component.text("- " + event.type() + " | " + event.summary()))
        ));
        return true;
    }
}
