package com.foxaria.core.listener;

import com.foxaria.api.service.GuildProfileService;
import com.foxaria.api.service.ServiceRegistry;
import com.foxaria.core.service.FoxariaPermissionService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

public final class InteractiveChatListener implements Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final TextColor GLOBAL_COLOR = TextColor.color(0x55FF55);
    private static final TextColor LOCAL_COLOR  = TextColor.color(0x55CCFF);
    private static final TextColor STAFF_COLOR  = TextColor.color(0xFF5555);
    private static final TextColor ARROW_COLOR  = TextColor.color(0x555555);

    private final JavaPlugin plugin;
    private final ServiceRegistry services;

    public InteractiveChatListener(JavaPlugin plugin, ServiceRegistry services) {
        this.plugin = plugin;
        this.services = services;
    }

    private static Component foxariaGradient() {
        return Component.text()
            .append(Component.text("F", TextColor.color(0xFF6600), TextDecoration.BOLD))
            .append(Component.text("O", TextColor.color(0xFF8800), TextDecoration.BOLD))
            .append(Component.text("X", TextColor.color(0xFFAA00), TextDecoration.BOLD))
            .append(Component.text("A", TextColor.color(0xFFCC00), TextDecoration.BOLD))
            .append(Component.text("R", TextColor.color(0xFFDD44), TextDecoration.BOLD))
            .append(Component.text("I", TextColor.color(0xFFEE66), TextDecoration.BOLD))
            .append(Component.text("A", TextColor.color(0xFFFF88), TextDecoration.BOLD))
            .append(Component.text(" "))
            .build();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String raw = PlainTextComponentSerializer.plainText().serialize(event.originalMessage());

        ChatType type;
        String message;

        if (raw.startsWith("#")) {
            if (!player.hasPermission("foxaria.staff.chat")) {
                player.sendMessage(Component.text("У вас нет доступа к стафф-чату.", NamedTextColor.RED));
                event.setCancelled(true);
                return;
            }
            type = ChatType.STAFF;
            message = raw.substring(1).trim();
        } else if (raw.startsWith("!")) {
            type = ChatType.GLOBAL;
            message = raw.substring(1).trim();
        } else {
            type = ChatType.LOCAL;
            message = raw.trim();
        }

        if (message.isEmpty()) { event.setCancelled(true); return; }
        event.setCancelled(true);

        FoxariaPermissionService perms = services.optional(FoxariaPermissionService.class);
        String rankPrefix = perms != null ? perms.rankPrefixForChat(player) : "";
        Component rankComp = rankPrefix.isEmpty() ? Component.empty()
            : LEGACY.deserialize(rankPrefix).append(Component.text(" "));

        Component guildComp = Component.empty();
        GuildProfileService guildProfile = services.optional(GuildProfileService.class);
        if (guildProfile != null) {
            try {
                Optional<String> nameOpt = guildProfile.guildNameOf(player.getUniqueId()).join();
                if (nameOpt.isPresent()) {
                    // guildNameOf возвращает "&aНазвание" — уже с цветом
                    Component tag = LEGACY.deserialize("[" + nameOpt.get() + "]");
                    guildComp = Component.text(" ").append(tag)
                        .hoverEvent(HoverEvent.showText(Component.text("Нажми для меню гильдии", NamedTextColor.GREEN)))
                        .clickEvent(ClickEvent.runCommand("/g"));
                }
            } catch (Exception ignored) { }
        }

        Component typeMarker;
        NamedTextColor msgColor;
        switch (type) {
            case GLOBAL -> { typeMarker = Component.text("<G> ", GLOBAL_COLOR, TextDecoration.BOLD); msgColor = NamedTextColor.WHITE; }
            case LOCAL  -> { typeMarker = Component.text("<L> ", LOCAL_COLOR, TextDecoration.BOLD); msgColor = NamedTextColor.GRAY; }
            case STAFF  -> { typeMarker = Component.text("[СТАФФ] ", STAFF_COLOR, TextDecoration.BOLD); msgColor = NamedTextColor.YELLOW; }
            default -> { typeMarker = Component.empty(); msgColor = NamedTextColor.WHITE; }
        }

        Component playerComp = Component.text(player.getName(), NamedTextColor.WHITE)
            .hoverEvent(HoverEvent.showText(
                Component.text("Написать ЛС → ", NamedTextColor.GRAY)
                    .append(Component.text(player.getName(), NamedTextColor.GOLD))))
            .clickEvent(ClickEvent.suggestCommand("/msg " + player.getName() + " "));

        Component finalMsg = Component.text()
            .append(foxariaGradient())
            .append(typeMarker)
            .append(rankComp)
            .append(playerComp)
            .append(guildComp)
            .append(Component.text(" » ", ARROW_COLOR, TextDecoration.BOLD))
            .append(Component.text(message, msgColor))
            .build();

        switch (type) {
            case GLOBAL -> Bukkit.broadcast(finalMsg);
            case LOCAL -> {
                double r2 = 50.0 * 50.0;
                for (Player v : Bukkit.getOnlinePlayers()) {
                    if (v.getWorld().equals(player.getWorld()) && v.getLocation().distanceSquared(player.getLocation()) <= r2) {
                        v.sendMessage(finalMsg);
                    }
                }
                Bukkit.getConsoleSender().sendMessage(finalMsg);
            }
            case STAFF -> {
                for (Player v : Bukkit.getOnlinePlayers()) {
                    if (v.hasPermission("foxaria.staff.chat")) v.sendMessage(finalMsg);
                }
                Bukkit.getConsoleSender().sendMessage(finalMsg);
            }
        }
    }

    private enum ChatType { GLOBAL, LOCAL, STAFF }
}
