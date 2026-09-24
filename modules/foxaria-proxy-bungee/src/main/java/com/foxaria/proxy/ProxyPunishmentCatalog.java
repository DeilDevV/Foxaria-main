package com.foxaria.proxy;

import net.md_5.bungee.config.Configuration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class ProxyPunishmentCatalog {

    private final Configuration cfg;

    public ProxyPunishmentCatalog(Configuration cfg) {
        this.cfg = cfg;
    }

    public Entry byCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        Configuration sec = cfg.getSection("moderation.reasons");
        if (sec == null) {
            return null;
        }
        for (String key : sec.getKeys()) {
            if (key.equalsIgnoreCase(code)) {
                return readEntry(key, sec.getSection(key));
            }
        }
        return null;
    }

    public List<Entry> all() {
        Configuration sec = cfg.getSection("moderation.reasons");
        if (sec == null) {
            return List.of();
        }
        List<Entry> out = new ArrayList<>();
        for (String key : sec.getKeys()) {
            out.add(readEntry(key, sec.getSection(key)));
        }
        out.sort(Comparator.comparing(Entry::code, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private Entry readEntry(String code, Configuration sec) {
        if (sec == null) {
            return new Entry(code, "BAN", 0L, code, "Без описания", "Без описания");
        }
        return new Entry(
            code,
            sec.getString("type", "BAN").toUpperCase(Locale.ROOT),
            sec.getLong("duration-seconds", 0L),
            sec.getString("title", code),
            sec.getString("description", "Без описания"),
            sec.getString("rules-text", sec.getString("description", "Без описания"))
        );
    }

    public record Entry(
        String code,
        String type,
        long durationSeconds,
        String title,
        String description,
        String rulesText
    ) {
    }
}
