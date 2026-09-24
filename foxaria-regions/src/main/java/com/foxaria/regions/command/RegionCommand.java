package com.foxaria.regions.command;

import com.foxaria.api.service.MessageService;
import com.foxaria.regions.RegionEconomyFailures;
import com.foxaria.regions.RegionFacade;
import com.foxaria.regions.RegionFlags;
import com.foxaria.regions.RegionRecord;
import com.foxaria.regions.RegionSession;
import com.foxaria.regions.gui.RegionCabinetMenu;
import com.foxaria.regions.gui.RegionHubMenu;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Optional;

public final class RegionCommand implements CommandExecutor {

    private final RegionFacade f;

    public RegionCommand(RegionFacade f) {
        this.f = f;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            f.messages().send(sender, "general.players-only", "&cТолько для игроков.");
            return true;
        }
        if (!player.hasPermission("foxaria.region.use")) {
            f.messages().send(sender, "general.no-permission", "&cНет прав.");
            return true;
        }
        if (args.length == 0) {
            f.menus().open(player, new RegionHubMenu(f));
            return true;
        }
        if (args[0].equalsIgnoreCase("add") && args.length >= 2) {
            handleAdd(player, args[1]);
            return true;
        }
        if (args[0].equalsIgnoreCase("kick") && args.length >= 2) {
            handleKick(player, args[1]);
            return true;
        }
        if (args[0].equalsIgnoreCase("rename") && args.length >= 2) {
            handleRename(player, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
            return true;
        }
        f.messages().send(player, "region.usage", "&e/region &7| &e/region add &7| &e/region kick &7| &e/region rename");
        return true;
    }

    private void handleAdd(Player player, String name) {
        Optional<RegionRecord> reg = resolveRegion(player);
        if (reg.isEmpty()) {
            f.messages().send(player, "region.not-in", "&cОткройте ядро привата или стойте в своём регионе.");
            return;
        }
        RegionRecord r = reg.get();
        boolean owner = r.ownerUuid().equals(player.getUniqueId());
        if (!owner && !RegionFlags.allowMemberInvite(r.flags())) {
            f.messages().send(player, "region.invite-disabled", "&cВладелец запретил приглашения участникам.");
            return;
        }
        if (!f.repo().isMember(r.id(), player.getUniqueId()).join()) {
            f.messages().send(player, "region.not-member", "&cВы не участник этого привата.");
            return;
        }
        Player online = Bukkit.getPlayerExact(name);
        OfflinePlayer target = online != null ? online : Bukkit.getOfflinePlayer(name);
        if (target.getUniqueId().equals(player.getUniqueId())) {
            f.messages().send(player, "region.add-self", "&cНельзя добавить себя.");
            return;
        }
        if (f.repo().isMember(r.id(), target.getUniqueId()).join()) {
            f.messages().send(player, "region.already-member", "&cЭтот игрок уже в этом привате.");
            return;
        }
        if (f.repo().findAnyMembershipRegion(target.getUniqueId()).join().isPresent()) {
            f.messages().send(player, "region.invite-target-has-privat", "&cУ игрока уже есть действующий приват.");
            return;
        }
        f.repo().addMember(r.id(), target.getUniqueId(), "member").join();
        f.messages().send(player, "region.member-added", "&aИгрок &f<n>&a добавлен в приват.", new MessageService.Placeholder("n", name));
    }

    private void handleKick(Player player, String name) {
        Optional<RegionRecord> reg = resolveRegion(player);
        if (reg.isEmpty()) {
            f.messages().send(player, "region.not-in", "&cОткройте ядро привата или стойте в своём регионе.");
            return;
        }
        RegionRecord r = reg.get();
        if (!r.ownerUuid().equals(player.getUniqueId())) {
            f.messages().send(player, "region.kick-owner-only", "&cИсключать участников может только владелец.");
            return;
        }
        Player online = Bukkit.getPlayerExact(name);
        OfflinePlayer target = online != null ? online : Bukkit.getOfflinePlayer(name);
        if (target.getUniqueId().equals(r.ownerUuid())) {
            f.messages().send(player, "region.kick-owner", "&cНельзя исключить владельца.");
            return;
        }
        if (!f.repo().isMember(r.id(), target.getUniqueId()).join()) {
            f.messages().send(player, "region.kick-not-member", "&cЭтого игрока нет в привате.");
            return;
        }
        f.repo().removeMember(r.id(), target.getUniqueId()).join();
        f.messages().send(player, "region.member-kicked", "&aИгрок &f<n>&a исключён из привата.", new MessageService.Placeholder("n", name));
    }

    private void handleRename(Player player, String raw) {
        Optional<RegionRecord> reg = resolveRegion(player);
        if (reg.isEmpty()) {
            f.messages().send(player, "region.not-in", "&cОткройте ядро привата или стойте в своём регионе.");
            return;
        }
        RegionRecord r = reg.get();
        if (!r.ownerUuid().equals(player.getUniqueId())) {
            f.messages().send(player, "region.rename-owner-only", "&cПереименовывать может только владелец.");
            return;
        }
        String name = raw.trim();
        if (name.length() < f.config().renameNameMinLen) {
            f.messages().send(player, "region.rename-too-short", "&cИмя слишком короткое.");
            return;
        }
        if (name.length() > f.config().renameNameMaxLen) {
            f.messages().send(player, "region.rename-too-long", "&cИмя слишком длинное.");
            return;
        }
        if (name.indexOf('&') >= 0 || name.indexOf('§') >= 0) {
            f.messages().send(player, "region.rename-no-codes", "&cНельзя использовать цветовые коды в имени.");
            return;
        }
        if (f.repo().isDisplayNameTaken(r.id(), name).join()) {
            f.messages().send(player, "region.rename-taken", "&cТакое имя привата уже занято.");
            return;
        }
        int rid = r.id();
        var cost = f.config().renameCostCoins;
        try {
            var snap = f.economy().balance(player.getUniqueId()).join();
            if (snap.balance().compareTo(cost) < 0) {
                f.messages().send(player, "region.rename-pay-fail", "&c<e>",
                    new MessageService.Placeholder("e", RegionEconomyFailures.insufficientCoins(cost, snap.balance())));
                return;
            }
        } catch (Exception ex) {
            f.messages().send(player, "region.rename-pay-fail", "&c<e>",
                new MessageService.Placeholder("e", RegionEconomyFailures.describe(ex)));
            return;
        }
        f.economy().withdraw(player.getUniqueId(), cost, "region_rename", player.getUniqueId())
            .thenRun(() -> Bukkit.getScheduler().runTask(f.plugin(), () -> {
                f.repo().updateDisplayName(rid, name).join();
                f.manager().patchDisplayName(rid, name);
                f.messages().send(player, "region.rename-done", "&aПриват переименован в &f<n>&a.", new MessageService.Placeholder("n", name));
            }))
            .exceptionally(t -> {
                Bukkit.getScheduler().runTask(f.plugin(), () ->
                    f.messages().send(player, "region.rename-pay-fail", "&c<e>",
                        new MessageService.Placeholder("e", RegionEconomyFailures.describe(t))));
                return null;
            });
    }

    private Optional<RegionRecord> resolveRegion(Player player) {
        int sid = RegionSession.lastRegionId(player);
        if (sid >= 0) {
            Optional<RegionRecord> byId = f.manager().byId(sid);
            if (byId.isPresent() && f.repo().isMember(byId.get().id(), player.getUniqueId()).join()) {
                return byId;
            }
        }
        return f.manager().findContaining(player.getLocation()).filter(r -> f.repo().isMember(r.id(), player.getUniqueId()).join());
    }
}
