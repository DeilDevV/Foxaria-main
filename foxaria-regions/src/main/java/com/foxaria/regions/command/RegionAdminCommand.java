package com.foxaria.regions.command;

import com.foxaria.api.service.MessageService;
import com.foxaria.regions.RegionDamageService;
import com.foxaria.regions.RegionFacade;
import com.foxaria.regions.RegionRecord;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class RegionAdminCommand implements CommandExecutor, TabCompleter {

    private final RegionFacade f;

    public RegionAdminCommand(RegionFacade f) {
        this.f = f;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("foxaria.region.admin")) {
            f.messages().send(sender, "general.no-permission", "&cНет прав.");
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "list" -> list(sender);
            case "tp" -> tp(sender, args);
            case "delete", "remove" -> delete(sender, args);
            case "purge" -> purge(sender, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    private static void sendUsage(CommandSender sender) {
        sender.sendMessage("§e/regionadmin list §7— список приватов");
        sender.sendMessage("§e/regionadmin tp <id> §7— телепорт к ядру");
        sender.sendMessage("§e/regionadmin delete <id> §7— удалить регион и снести ядро");
        sender.sendMessage("§e/regionadmin purge confirm §7— §cочистить ВСЕ приваты в БД и ядра в мире");
    }

    private void list(CommandSender sender) {
        var list = f.manager().snapshot();
        if (list.isEmpty()) {
            f.messages().send(sender, "region.admin-list-empty", "&7Нет записей приватов в базе.");
            return;
        }
        sender.sendMessage("§6Приваты §7(" + list.size() + "):");
        for (RegionRecord r : list) {
            String name = r.displayName() != null && !r.displayName().isBlank() ? r.displayName() : ("#" + r.id());
            sender.sendMessage("§f" + r.id() + " §7| §f" + name + " §7| §f" + r.world()
                + " §7ядро §f" + r.cabinetX() + " " + r.cabinetY() + " " + r.cabinetZ()
                + " §7lvl§f" + r.level());
        }
    }

    private void tp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            f.messages().send(sender, "general.players-only", "&cТолько для игроков.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§cУкажите id: /regionadmin tp <id>");
            return;
        }
        int id;
        try {
            id = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            f.messages().send(sender, "region.admin-bad-id", "&cНеверный номер региона.");
            return;
        }
        Optional<RegionRecord> rec = f.manager().byId(id);
        if (rec.isEmpty()) {
            f.messages().send(sender, "region.admin-not-found", "&cРегион с таким id не найден.");
            return;
        }
        RegionRecord r = rec.get();
        var w = Bukkit.getWorld(r.world());
        if (w == null) {
            f.messages().send(sender, "region.admin-world-missing", "&cМир не загружен: &f<w>",
                new MessageService.Placeholder("w", r.world()));
            return;
        }
        Location loc = new Location(w, r.cabinetX() + 0.5, r.cabinetY() + 1.0, r.cabinetZ() + 0.5, player.getLocation().getYaw(), player.getLocation().getPitch());
        player.teleport(loc);
        f.messages().send(sender, "region.admin-tp-done", "&aТелепорт к ядру региона &f#<id>", new MessageService.Placeholder("id", String.valueOf(id)));
    }

    private void delete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cУкажите id: /regionadmin delete <id>");
            return;
        }
        int id;
        try {
            id = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            f.messages().send(sender, "region.admin-bad-id", "&cНеверный номер региона.");
            return;
        }
        Optional<RegionRecord> rec = f.manager().byId(id);
        if (rec.isEmpty()) {
            f.messages().send(sender, "region.admin-not-found", "&cРегион не найден.");
            return;
        }
        f.damage().adminDeleteRegion(rec.get());
        f.messages().send(sender, "region.admin-deleted", "&aРегион &f#<id> &aудалён, ядро убрано.",
            new MessageService.Placeholder("id", String.valueOf(id)));
    }

    private void purge(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            sender.sendMessage("§cЭто удалит все приваты. Повторите: §f/regionadmin purge confirm");
            return;
        }
        List<RegionRecord> snap = List.copyOf(f.manager().snapshot());
        RegionDamageService dmg = f.damage();
        for (RegionRecord r : snap) {
            dmg.adminDeleteRegion(r);
        }
        f.repo().purgeAllRegions().join();
        f.manager().replaceAll(Collections.emptyList());
        f.messages().send(sender, "region.admin-purged", "&aБаза приватов очищена, ядра снесены (&f<n>&a шт.).",
            new MessageService.Placeholder("n", String.valueOf(snap.size())));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("foxaria.region.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filter(List.of("list", "tp", "delete", "purge"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("purge")) {
            return filter(List.of("confirm"), args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("tp") || args[0].equalsIgnoreCase("delete"))) {
            List<String> ids = new ArrayList<>();
            for (RegionRecord r : f.manager().snapshot()) {
                ids.add(String.valueOf(r.id()));
            }
            return filter(ids, args[1]);
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
