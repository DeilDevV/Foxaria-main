package com.foxaria.moderation;

import com.foxaria.api.model.PunishmentRecord;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Публичное объявление о наказании (не тихом) с подсказкой при наведении.
 */
public final class PunishmentBroadcasts {

    private static final Set<String> PUBLIC_TYPES = Set.of(
        "MUTE", "TEMPMUTE", "BAN", "TEMPBAN", "FREEZE"
    );

    private PunishmentBroadcasts() {
    }

    public static boolean shouldAnnounce(String type) {
        return type != null && PUBLIC_TYPES.contains(type.toUpperCase(Locale.ROOT));
    }

    /**
     * Публичное снятие санкций (/unpunish без -s): зелёная строка + hover с типом, причиной и кем снято.
     */
    public static Component buildUnpunishAnnouncement(UUID targetUuid, List<PunishmentRecord> removedActive, String removerName) {
        OfflinePlayer off = Bukkit.getOfflinePlayer(targetUuid);
        String nick = off.getName() != null && !off.getName().isBlank() ? off.getName() : "—";
        Set<String> typeKeys = removedActive.stream()
            .map(r -> r.type().toUpperCase(Locale.ROOT))
            .collect(Collectors.toCollection(LinkedHashSet::new));
        String wasLine = wasStatusLine(typeKeys);
        String reasonLine = removedActive.stream()
            .map(PunishmentBroadcasts::reasonForHover)
            .filter(s -> s != null && !s.isBlank())
            .distinct()
            .collect(Collectors.joining("; "));
        if (reasonLine.isBlank()) {
            reasonLine = "—";
        }
        String remover = removerName == null || removerName.isBlank() ? "Консоль" : removerName;

        Component hover = Component.text()
            .append(Component.text(wasLine, NamedTextColor.YELLOW))
            .append(Component.newline())
            .append(Component.text("По причине: ", NamedTextColor.DARK_GRAY))
            .append(Component.text(reasonLine, NamedTextColor.GRAY))
            .append(Component.newline())
            .append(Component.text("Снял: ", NamedTextColor.DARK_GRAY))
            .append(Component.text(remover, NamedTextColor.GREEN))
            .build();

        return Component.text()
            .append(Component.text("[", NamedTextColor.DARK_GRAY))
            .append(Component.text("FOXARIA", NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(Component.text("] ", NamedTextColor.DARK_GRAY))
            .append(Component.text("С игрока ", NamedTextColor.GREEN))
            .append(Component.text(nick, NamedTextColor.WHITE))
            .append(Component.text(" было снято наказание.", NamedTextColor.GREEN))
            .hoverEvent(HoverEvent.showText(hover))
            .build();
    }

    public static NetworkAnnouncement buildPunishNetworkAnnouncement(PunishmentRecord record, String actorDisplayName, DurationFormat durationFormat) {
        OfflinePlayer off = Bukkit.getOfflinePlayer(record.targetUuid());
        String nick = off.getName() != null && !off.getName().isBlank() ? off.getName() : "—";
        PunishmentTextCodec.Parsed parsed = PunishmentTextCodec.parse(record.reason());
        String type = record.type() == null ? "" : record.type().toUpperCase(Locale.ROOT);
        String durationText = durationFormat.format(record.expiresAt());
        String actor = actorDisplayName == null || actorDisplayName.isBlank() ? "Администрация" : actorDisplayName;

        String main = "&8[&6&lFOXARIA&8] &7Игрок &f" + nick + "&7 был наказан.";
        String hover = statusLineForHover(type) + "\n"
            + "&8Тип: &6" + nominativeKind(type) + "\n"
            + "&8Кем выдано: &b" + actor + "\n"
            + "&8По причине: &7" + safeReasonLine(parsed) + "\n"
            + "&8Срок: &e" + (durationText == null || durationText.isBlank() ? "—" : durationText);

        return new NetworkAnnouncement(main, hover);
    }

    public static NetworkAnnouncement buildUnpunishNetworkAnnouncement(UUID targetUuid, List<PunishmentRecord> removedActive, String removerName) {
        OfflinePlayer off = Bukkit.getOfflinePlayer(targetUuid);
        String nick = off.getName() != null && !off.getName().isBlank() ? off.getName() : "—";
        Set<String> typeKeys = removedActive.stream()
            .map(r -> r.type().toUpperCase(Locale.ROOT))
            .collect(Collectors.toCollection(LinkedHashSet::new));
        String wasLine = wasStatusLine(typeKeys);
        String reasonLine = removedActive.stream()
            .map(PunishmentBroadcasts::reasonForHover)
            .filter(s -> s != null && !s.isBlank())
            .distinct()
            .collect(Collectors.joining("; "));
        if (reasonLine.isBlank()) {
            reasonLine = "—";
        }
        String remover = removerName == null || removerName.isBlank() ? "Консоль" : removerName;

        String main = "&8[&6&lFOXARIA&8] &aС игрока &f" + nick + " &aбыло снято наказание.";
        String hover = "&e" + wasLine + "\n"
            + "&8По причине: &7" + reasonLine + "\n"
            + "&8Снял: &a" + remover;
        return new NetworkAnnouncement(main, hover);
    }

    private static String reasonForHover(PunishmentRecord record) {
        PunishmentTextCodec.Parsed p = PunishmentTextCodec.parse(record.reason());
        String title = p.title() == null ? "" : p.title().trim();
        String desc = p.description() == null ? "" : p.description().trim();
        if (title.isEmpty() && desc.isEmpty()) {
            return "—";
        }
        if (desc.isEmpty() || desc.equals(title)) {
            return title;
        }
        return title + " — " + trimHover(desc);
    }

    /**
     * Кратко описывает, что с игрока сняли (для hover).
     */
    private static String wasStatusLine(Set<String> types) {
        boolean mute = types.stream().anyMatch(t -> t.equals("MUTE") || t.equals("TEMPMUTE"));
        boolean ban = types.stream().anyMatch(t -> t.equals("BAN") || t.equals("TEMPBAN"));
        boolean freeze = types.contains("FREEZE");
        List<String> parts = new ArrayList<>();
        if (mute) {
            parts.add("замучен");
        }
        if (ban) {
            parts.add("заблокирован");
        }
        if (freeze) {
            parts.add("заморожен");
        }
        if (parts.isEmpty()) {
            return "Снято наказание";
        }
        if (parts.size() == 1) {
            return "Был " + parts.get(0);
        }
        if (parts.size() == 2) {
            return "Был " + parts.get(0) + " и " + parts.get(1);
        }
        StringBuilder sb = new StringBuilder("Был ");
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append(i == parts.size() - 1 ? " и " : ", ");
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    public static Component buildPublicLine(PunishmentRecord record, String actorDisplayName, DurationFormat durationFormat) {
        OfflinePlayer off = Bukkit.getOfflinePlayer(record.targetUuid());
        String nick = off.getName() != null && !off.getName().isBlank() ? off.getName() : "—";
        PunishmentTextCodec.Parsed parsed = PunishmentTextCodec.parse(record.reason());
        String type = record.type().toUpperCase(Locale.ROOT);
        String durationText = durationFormat.format(record.expiresAt());

        Component hover = Component.text()
            .append(Component.text(statusLineForHover(type), NamedTextColor.YELLOW))
            .append(Component.newline())
            .append(Component.text("Тип: ", NamedTextColor.DARK_GRAY))
            .append(Component.text(nominativeKind(type), NamedTextColor.GOLD))
            .append(Component.newline())
            .append(Component.text("Кем выдано: ", NamedTextColor.DARK_GRAY))
            .append(Component.text(actorDisplayName == null || actorDisplayName.isBlank() ? "Администрация" : actorDisplayName, NamedTextColor.AQUA))
            .append(Component.newline())
            .append(Component.text("Описание: ", NamedTextColor.DARK_GRAY))
            .append(Component.text(trimHover(parsed.description()), NamedTextColor.GRAY))
            .append(Component.newline())
            .append(Component.text("Срок: ", NamedTextColor.DARK_GRAY))
            .append(Component.text(durationText, NamedTextColor.YELLOW))
            .build();

        Component main = Component.text()
            .append(Component.text("[", NamedTextColor.DARK_GRAY))
            .append(Component.text("FOXARIA", NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(Component.text("] ", NamedTextColor.DARK_GRAY))
            .append(Component.text("Игрок ", NamedTextColor.GRAY))
            .append(Component.text(nick, NamedTextColor.WHITE))
            .append(Component.text(" был наказан.", NamedTextColor.GRAY))
            .hoverEvent(HoverEvent.showText(hover))
            .build();

        return main;
    }

    private static String statusLineForHover(String type) {
        if (type == null) {
            return "Выдано наказание";
        }
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "MUTE", "TEMPMUTE" -> "Был замучен";
            case "BAN", "TEMPBAN" -> "Был заблокирован";
            case "FREEZE" -> "Был заморожен";
            default -> "Выдано наказание";
        };
    }

    private static String safeReasonLine(PunishmentTextCodec.Parsed parsed) {
        if (parsed == null) {
            return "—";
        }
        String title = parsed.title() == null ? "" : parsed.title().trim();
        String desc = parsed.description() == null ? "" : parsed.description().trim();
        if (!title.isEmpty() && (desc.isEmpty() || desc.equals(title))) {
            return title;
        }
        if (!title.isEmpty() && !desc.isEmpty()) {
            return title + " — " + trimHover(desc);
        }
        if (!desc.isEmpty()) {
            return trimHover(desc);
        }
        return "—";
    }

    public record NetworkAnnouncement(String mainLegacy, String hoverLegacyMultiline) {
    }

    private static String nominativeKind(String type) {
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "MUTE", "TEMPMUTE" -> "Мут";
            case "BAN", "TEMPBAN" -> "Бан";
            case "FREEZE" -> "Заморозка";
            default -> type;
        };
    }

    private static String accusativeKind(String type) {
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "MUTE", "TEMPMUTE" -> "мут";
            case "BAN", "TEMPBAN" -> "бан";
            case "FREEZE" -> "заморозку";
            default -> type.toLowerCase(Locale.ROOT);
        };
    }

    private static String trimHover(String s) {
        if (s == null) {
            return "—";
        }
        String t = s.replace('\n', ' ').trim();
        return t.length() > 120 ? t.substring(0, 117) + "…" : t;
    }

    @FunctionalInterface
    public interface DurationFormat {
        String format(long expiresAtMillis);
    }
}
