package com.foxaria.moderation;

public final class PunishmentTextCodec {

    private static final String PREFIX = "[FXP]";

    private PunishmentTextCodec() {
    }

    public static String encode(PunishmentCatalog.Entry entry) {
        return PREFIX + entry.code() + "\n" + safe(entry.title()) + "\n" + safe(entry.description());
    }

    public static Parsed parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new Parsed("", "Без причины", "Без описания");
        }
        if (!raw.startsWith(PREFIX)) {
            return new Parsed("", raw, raw);
        }
        String body = raw.substring(PREFIX.length());
        String[] parts = body.split("\n", 3);
        String code = parts.length > 0 ? parts[0] : "";
        String title = parts.length > 1 ? parts[1] : "Без причины";
        String description = parts.length > 2 ? parts[2] : title;
        return new Parsed(code, title, description);
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace("\n", " ").trim();
    }

    public record Parsed(String code, String title, String description) {
    }
}
