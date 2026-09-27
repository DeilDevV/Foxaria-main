package com.foxaria.guilds.gui;

import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import com.foxaria.core.gui.MenuStyle;
import com.foxaria.guilds.GuildModels.GuildMemberRecord;
import com.foxaria.guilds.GuildModels.GuildRecord;
import com.foxaria.guilds.GuildService;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public final class GuildInfoMenu extends BaseMenu {

    private final GuildService service;
    private final GuildRecord guild;
    private final List<GuildMemberRecord> members;

    public GuildInfoMenu(GuildService service, GuildRecord guild, List<GuildMemberRecord> members) {
        super("&8⟨ &b" + guild.name() + " &8│ &fинформация &8⟩", 45);
        this.service = service;
        this.guild = guild;
        this.members = members;
    }

    @Override
    protected void draw(Player viewer) {
        for (int i = 0; i < 45; i++) {
            setItem(i, MenuItems.filler(), null);
        }

        setItem(4, MenuItems.item(Material.BEACON, "&b&l" + guild.name(),
            "&8───────────────",
            "&7Уровень: &f" + guild.level(),
            "&7Баланс: &e" + guild.bankBalance() + " монет",
            "&7Очки: &d" + guild.guildPoints(),
            "&7Участников: &f" + members.size()
        ), null);

        int slot = 19;
        for (GuildMemberRecord member : members) {
            if (slot > 25) break;
            setItem(slot++, MenuItems.item(Material.PLAYER_HEAD, "&f" + member.playerName(),
                "&7Роль: &a" + member.role()
            ), null);
        }

        setItem(40, MenuStyle.closeButton(), click -> viewer.closeInventory());
    }
}
