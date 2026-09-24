package com.foxaria.moderation.gui;

import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import com.foxaria.moderation.JdbcModerationService;
import com.foxaria.moderation.PunishmentCatalog;
import com.foxaria.moderation.PunishmentTextCodec;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Выбор причины из каталога {@code punishments.reasons} — тип и длительность из конфига.
 */
public final class PunishReasonMenu extends BaseMenu {

    private static final int[] CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    private final UUID targetUuid;
    private final JdbcModerationService moderationService;
    private final Consumer<Player> navigateBack;

    public PunishReasonMenu(UUID targetUuid, JdbcModerationService moderationService, Consumer<Player> navigateBack) {
        super("&8⟨ &c&lСанкции &8⟩", 54);
        this.targetUuid = targetUuid;
        this.moderationService = moderationService;
        this.navigateBack = navigateBack;
    }

    @Override
    protected void draw(Player viewer) {
        fillStaffDecorFrame();

        OfflinePlayer off = Bukkit.getOfflinePlayer(targetUuid);
        String targetLabel = off.getName() != null ? off.getName() : targetUuid.toString();

        setItem(4, MenuItems.item(
            Material.BOOK,
            "&6&lКаталог причин",
            "&7Цель: &f" + targetLabel,
            "&7Срок и тип берутся из &fmodules/moderation.yml",
            "&8───────────────",
            "&eЛКМ &7выдать  &6ПКМ &7тихий режим"
        ), null);

        PunishmentCatalog catalog = moderationService.punishmentCatalog();
        List<PunishmentCatalog.Entry> entries = catalog.all();
        int limit = Math.min(entries.size(), CONTENT_SLOTS.length);
        for (int i = 0; i < limit; i++) {
            PunishmentCatalog.Entry entry = entries.get(i);
            int slot = CONTENT_SLOTS[i];
            String dur = entry.durationSeconds() > 0
                ? "&7Длительность: &e" + formatDuration(entry.durationSeconds())
                : "&7Срок: &cперманент / до снятия";
            setItem(slot, MenuItems.item(
                materialFor(entry.type()),
                "&f&l" + entry.code() + " &8· &7" + entry.title(),
                "&7Тип: &f" + entry.type(),
                dur,
                "&8───────────────",
                "&7" + trim(entry.description(), 48),
                "&eЛКМ &8· &6ПКМ &7тихо"
            ), click -> {
                long expiresAt = entry.durationSeconds() > 0L
                    ? System.currentTimeMillis() + (entry.durationSeconds() * 1000L)
                    : 0L;
                moderationService.punish(
                    viewer,
                    targetUuid,
                    entry.type(),
                    PunishmentTextCodec.encode(entry),
                    expiresAt,
                    click.isRightClick()
                );
                viewer.closeInventory();
            });
        }

        if (entries.isEmpty()) {
            setItem(22, MenuItems.item(
                Material.LIME_STAINED_GLASS_PANE,
                "&e&lНет кодов",
                "&7Добавьте записи в &fpunishments.reasons",
                "&7в modules/moderation.yml"
            ), null);
        } else if (entries.size() > CONTENT_SLOTS.length) {
            setItem(48, MenuItems.item(
                Material.PAPER,
                "&eПоказаны не все",
                "&7В конфиге &f" + entries.size() + " &7причин, в меню — первые &f" + CONTENT_SLOTS.length,
                "&7Используйте &f/punish &7для остальных кодов."
            ), null);
        }

        setItem(49, MenuItems.item(
            Material.ARROW,
            "&7← &fНазад",
            "&7Предыдущее меню"
        ), click -> navigateBack.accept(viewer));
    }

    private static String trim(String s, int max) {
        if (s == null || s.length() <= max) {
            return s == null ? "" : s;
        }
        return s.substring(0, max - 1) + "…";
    }

    private static String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + " сек";
        }
        if (seconds < 3600) {
            return (seconds / 60) + " мин";
        }
        if (seconds < 86400) {
            return (seconds / 3600) + " ч";
        }
        return (seconds / 86400) + " д";
    }

    private static Material materialFor(String typeRaw) {
        String t = typeRaw == null ? "" : typeRaw.toUpperCase();
        return switch (t) {
            case "BAN", "TEMPBAN" -> Material.BARRIER;
            case "MUTE", "TEMPMUTE" -> Material.IRON_BARS;
            case "KICK" -> Material.LEATHER_BOOTS;
            case "WARN" -> Material.PAPER;
            case "FREEZE" -> Material.BLUE_ICE;
            default -> Material.WRITABLE_BOOK;
        };
    }
}
