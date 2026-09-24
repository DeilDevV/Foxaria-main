package com.foxaria.regions.command;

import com.foxaria.api.service.MessageService;
import com.foxaria.regions.RegionFacade;
import com.foxaria.regions.RegionItems;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RaidCommand implements CommandExecutor, TabCompleter {

    private final RegionFacade f;

    public RaidCommand(RegionFacade f) {
        this.f = f;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("give")) {
            return handleGive(sender, args);
        }
        if (!(sender instanceof Player player)) {
            f.messages().send(sender, "general.players-only", "&cТолько для игроков.");
            return true;
        }
        if (!player.hasPermission("foxaria.region.use")) {
            f.messages().send(sender, "general.no-permission", "&cНет прав.");
            return true;
        }
        f.messages().send(player, "region.raid-use-hint", "&7Динамит рейда: справка в &f/craft&7. Выдача админам: &f/raid give <1-4> [кол-во]");
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("foxaria.region.dynamite.give")) {
            f.messages().send(sender, "region.raid-give-no-perm", "&cНет прав на выдачу динамита.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            f.messages().send(sender, "general.players-only", "&cТолько для игроков.");
            return true;
        }
        if (args.length < 2) {
            f.messages().send(sender, "region.raid-give-usage", "&cИспользование: /raid give <1-4> [кол-во]");
            return true;
        }
        int tier;
        try {
            tier = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            f.messages().send(sender, "region.raid-give-bad-tier", "&cУровень динамита: 1–4.");
            return true;
        }
        if (tier < 1 || tier > 4) {
            f.messages().send(sender, "region.raid-give-bad-tier", "&cУровень динамита: 1–4.");
            return true;
        }
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                f.messages().send(sender, "region.raid-give-bad-amount", "&cКоличество от 1 до 64.");
                return true;
            }
        }
        if (amount < 1 || amount > 64) {
            f.messages().send(sender, "region.raid-give-bad-amount", "&cКоличество от 1 до 64.");
            return true;
        }
        int dmg = f.config().dynamiteDamage(tier);
        ItemStack stack = RegionItems.raidDynamite(f.plugin(), tier, dmg);
        stack.setAmount(amount);
        var left = player.getInventory().addItem(stack);
        if (!left.isEmpty()) {
            left.values().forEach(s -> player.getWorld().dropItemNaturally(player.getLocation(), s));
        }
        f.messages().send(sender, "region.raid-give-done", "&aВыдан динамит рейда &f<tier>&a × &f<amt>",
            new MessageService.Placeholder("tier", String.valueOf(tier)),
            new MessageService.Placeholder("amt", String.valueOf(amount)));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> sub = new ArrayList<>();
            sub.add("give");
            return filter(sub, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return filter(List.of("1", "2", "3", "4"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filter(List.of("1", "8", "16", "32", "64"), args[2]);
        }
        return Collections.emptyList();
    }

    private static List<String> filter(List<String> opts, String prefix) {
        if (prefix.isEmpty()) {
            return opts;
        }
        String p = prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String o : opts) {
            if (o.toLowerCase().startsWith(p)) {
                out.add(o);
            }
        }
        return out;
    }
}
