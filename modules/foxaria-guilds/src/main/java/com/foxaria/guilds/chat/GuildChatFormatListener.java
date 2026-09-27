package com.foxaria.guilds.chat;

import com.foxaria.core.service.FoxariaPermissionService;
import com.foxaria.guilds.GuildModels.GuildRecord;
import com.foxaria.guilds.GuildService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Optional;

public final class GuildChatFormatListener implements Listener {

    private static final TextColor FOXARIA_COLOR = TextColor.color(0xFFAA55);
    private static final TextColor GUILD_MARKER_COLOR = TextColor.color(0x55FF55);
    private static final TextColor ARROW_COLOR = TextColor.color(0x555555);
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final FoxariaPermissionService permissions;
    private final GuildService guilds;

    public GuildChatFormatListener(FoxariaPermissionService permissions, GuildService guilds) {
        this.permissions = permissions;
        this.guilds = guilds;
    }

    public void sendGuildMessage(Player sender, String message) {
        Optional<GuildRecord> guildOpt = guilds.guildOf(sender.getUniqueId()).join();
        if (guildOpt.isEmpty()) {
            sender.sendMessage(Component.text("Вы не состоите в гильдии.", NamedTextColor.RED));
            return;
        }
        GuildRecord guild = guildOpt.get();
        String prefixRaw = permissions.rankPrefixForChat(sender);
        String cleanRank = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', prefixRaw)).trim();

        // Цвет тега гильдии — сохраняем через legacyColorForGuildTag
        String colorCode = guilds.legacyColorForGuildTag(guild.tagColor());
        String tagText = colorCode + "[" + guild.name() + "]&r";

        Component foxariaPrefix = Component.text("FOXARIA ", FOXARIA_COLOR, TextDecoration.BOLD);
        Component typeMarker = Component.text("<Г> ", GUILD_MARKER_COLOR, TextDecoration.BOLD);
        Component rankComp = cleanRank.isEmpty()
            ? Component.empty()
            : Component.text(cleanRank + " ", NamedTextColor.GRAY);

        Component playerComp = Component.text(sender.getName(), NamedTextColor.WHITE)
            .hoverEvent(HoverEvent.showText(
                Component.text("Написать ЛС → ", NamedTextColor.GRAY)
                    .append(Component.text(sender.getName(), NamedTextColor.GOLD))
            ))
            .clickEvent(ClickEvent.suggestCommand("/msg " + sender.getName() + " "));

        // Тег с цветом гильдии, кликабельный
        Component guildTag = Component.text(" ")
            .append(LEGACY.deserialize(tagText)
                .hoverEvent(HoverEvent.showText(guilds.guildChatHoverText(guild)))
                .clickEvent(ClickEvent.runCommand("/g info " + guild.name()))
            );

        Component arrow = Component.text(" » ", ARROW_COLOR, TextDecoration.BOLD);
        Component msgComp = Component.text(message, NamedTextColor.GREEN);

        Component finalMessage = Component.text()
            .append(foxariaPrefix)
            .append(typeMarker)
            .append(rankComp)
            .append(playerComp)
            .append(guildTag)
            .append(arrow)
            .append(msgComp)
            .build();

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(sender.getUniqueId())
                || guilds.isSameGuild(sender.getUniqueId(), viewer.getUniqueId())) {
                viewer.sendMessage(finalMessage);
            }
        }
        Bukkit.getConsoleSender().sendMessage(finalMessage);
    }
}
