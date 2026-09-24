package com.foxaria.crafting.command;

import com.foxaria.api.service.KnowledgeService;
import com.foxaria.api.service.MessageService;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.crafting.CraftingService;
import com.foxaria.crafting.gui.CraftPlayerMenu;
import com.foxaria.regions.RegionConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class CraftCommand implements CommandExecutor {

    private final CraftingService crafting;
    private final RegionConfig regionConfig;
    private final KnowledgeService knowledge;
    private final MessageService messages;
    private final MenuManager menus;

    public CraftCommand(
        CraftingService crafting,
        RegionConfig regionConfig,
        KnowledgeService knowledge,
        MessageService messages,
        MenuManager menus
    ) {
        this.crafting = crafting;
        this.regionConfig = regionConfig;
        this.knowledge = knowledge;
        this.messages = messages;
        this.menus = menus;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.players-only", "&cТолько для игроков.");
            return true;
        }
        if (!player.hasPermission("foxaria.region.use")) {
            messages.send(sender, "general.no-permission", "&cНет прав.");
            return true;
        }
        menus.open(player, new CraftPlayerMenu(menus, crafting, regionConfig, knowledge));
        return true;
    }
}
