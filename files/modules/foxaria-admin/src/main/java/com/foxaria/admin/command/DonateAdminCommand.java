package com.foxaria.admin.command;

import com.foxaria.api.service.EconomyService;
import com.foxaria.api.service.MessageService;
import com.foxaria.api.service.RankService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Управление донатом прямо с сервера: токены, донат-ранги, сводка по игроку.
 * Раньше донат выдавался только через сайт — на сервере управлять было нечем.
 *
 * /fdonate info <ник>
 * /fdonate tokens <ник> <кол-во>     — выдать токены
 * /fdonate take   <ник> <кол-во>     — забрать токены
 * /fdonate rank   <ник> <группа>     — выдать донат-ранг
 * /fdonate rank   <ник> <группа> <дни> — временный ранг
 * /fdonate unrank <ник>              — вернуть default
 */
public final class DonateAdminCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "foxaria.donate.admin";
    private static final List<String> SUBS = List.of("info", "tokens", "take", "rank", "unrank", "help");
    private static final List<String> RANKS = List.of("supporter", "vip", "elite", "default");

    private final EconomyService economy;
    private final RankService ranks;
    private final MessageService messages;

    public DonateAdminCommand(EconomyService economy, RankService ranks, MessageService messages) {
        this.economy = economy;
        this.ranks = ranks;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            messages.send(sender, "general.no-permission", "&cУ вас нет прав.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length < 2) {
            help(sender);
            return true;
        }

        OfflinePlayer target = resolve(args[1]);
        if (target == null || target.getUniqueId() == null) {
            messages.send(sender, "general.player-not-found", "&cИгрок не найден.");
            return true;
        }
        UUID targetId = target.getUniqueId();
        String targetName = target.getName() == null ? args[1] : target.getName();
        UUID actor = sender instanceof Player p ? p.getUniqueId() : null;

        switch (sub) {
            case "info" -> economy.balance(targetId).thenAccept(balance ->
                ranks.primaryGroup(targetId).thenAccept(group -> {
                    messages.send(sender, "donate.admin.info-header", "&6Донат-профиль: &f<player>",
                        new MessageService.Placeholder("player", targetName));
                    messages.send(sender, "donate.admin.info-rank", "&7Ранг: &f<rank>",
                        new MessageService.Placeholder("rank", group == null ? "default" : group));
                    messages.send(sender, "donate.admin.info-tokens", "&7Токены: &b<tokens>",
                        new MessageService.Placeholder("tokens", String.valueOf(balance.tokens())));
                    messages.send(sender, "donate.admin.info-balance", "&7Баланс: &a<balance>",
                        new MessageService.Placeholder("balance", String.valueOf(balance.balance())));
                }));

            case "tokens", "take" -> {
                if (args.length < 3) {
                    messages.send(sender, "donate.admin.usage-tokens", "&cИспользование: /fdonate <tokens|take> <ник> <кол-во>");
                    return true;
                }
                long amount = parseLong(args[2]);
                if (amount <= 0) {
                    messages.send(sender, "donate.admin.bad-amount", "&cКоличество должно быть положительным числом.");
                    return true;
                }
                boolean give = sub.equals("tokens");
                String reason = "admin:" + (sender instanceof Player p ? p.getName() : "console");
                (give
                    ? economy.depositTokens(targetId, amount, reason, actor)
                    : economy.withdrawTokens(targetId, amount, reason, actor))
                    .thenRun(() -> {
                        messages.send(sender, give ? "donate.admin.tokens-given" : "donate.admin.tokens-taken",
                            give ? "&aВыдано &b<amount> &aтокенов игроку &f<player>&a."
                                 : "&eСписано &b<amount> &eтокенов у игрока &f<player>&e.",
                            new MessageService.Placeholder("amount", String.valueOf(amount)),
                            new MessageService.Placeholder("player", targetName));
                        Player online = Bukkit.getPlayer(targetId);
                        if (online != null && give) {
                            messages.send(online, "donate.admin.tokens-received",
                                "&aНа ваш счёт зачислено &b<amount> &aтокенов.",
                                new MessageService.Placeholder("amount", String.valueOf(amount)));
                        }
                    });
            }

            case "rank" -> {
                if (args.length < 3) {
                    messages.send(sender, "donate.admin.usage-rank", "&cИспользование: /fdonate rank <ник> <группа> [дни]");
                    return true;
                }
                String group = args[2].toLowerCase(Locale.ROOT);
                if (args.length >= 4) {
                    long days = parseLong(args[3]);
                    if (days <= 0) {
                        messages.send(sender, "donate.admin.bad-amount", "&cКоличество дней должно быть положительным.");
                        return true;
                    }
                    ranks.grantTemporaryGroup(targetId, group, days * 86400L).thenRun(() ->
                        messages.send(sender, "donate.admin.rank-temp",
                            "&aИгроку &f<player> &aвыдан ранг &f<rank> &7на <days> дн.",
                            new MessageService.Placeholder("player", targetName),
                            new MessageService.Placeholder("rank", group),
                            new MessageService.Placeholder("days", String.valueOf(days))));
                    return true;
                }
                ranks.setPrimaryGroup(targetId, group).thenRun(() ->
                    messages.send(sender, "donate.admin.rank-set",
                        "&aИгроку &f<player> &aустановлен ранг &f<rank>&a.",
                        new MessageService.Placeholder("player", targetName),
                        new MessageService.Placeholder("rank", group)));
            }

            case "unrank" -> ranks.setPrimaryGroup(targetId, "default").thenRun(() ->
                messages.send(sender, "donate.admin.rank-cleared",
                    "&eИгроку &f<player> &eвозвращён ранг &fdefault&e.",
                    new MessageService.Placeholder("player", targetName)));

            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender sender) {
        messages.send(sender, "donate.admin.help-header", "&6Управление донатом");
        sender.sendMessage("§7/fdonate info §f<ник> §8— сводка: ранг, токены, баланс");
        sender.sendMessage("§7/fdonate tokens §f<ник> <кол-во> §8— выдать токены");
        sender.sendMessage("§7/fdonate take §f<ник> <кол-во> §8— забрать токены");
        sender.sendMessage("§7/fdonate rank §f<ник> <группа> [дни] §8— выдать донат-ранг");
        sender.sendMessage("§7/fdonate unrank §f<ник> §8— вернуть default");
    }

    private OfflinePlayer resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() ? offline : null;
    }

    private long parseLong(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(SUBS, args[0]);
        }
        if (args.length == 2) {
            List<String> names = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
            return filter(names, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("rank")) {
            return filter(RANKS, args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> source, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String s : source) {
            if (s.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(s);
            }
        }
        return out;
    }
}
