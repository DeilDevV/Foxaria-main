package com.foxaria.crafting.command;

import com.foxaria.api.service.MessageService;
import com.foxaria.crafting.CraftingService;
import com.foxaria.crafting.admin.CraftAdminHolder;
import com.foxaria.crafting.admin.CraftAdminListener;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class AdminFoxCraftCommand implements CommandExecutor {

    private final CraftingService crafting;
    private final MessageService messages;

    public AdminFoxCraftCommand(CraftingService crafting, MessageService messages) {
        this.crafting = crafting;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("foxaria.craft.admin")) {
            messages.send(sender, "general.no-permission", "&cНет прав.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.players-only", "&cТолько для игроков.");
            return true;
        }
        CraftAdminHolder holder = new CraftAdminHolder(crafting);
        CraftAdminListener.decorate(holder);
        player.openInventory(holder.getInventory());
        return true;
    }
}
