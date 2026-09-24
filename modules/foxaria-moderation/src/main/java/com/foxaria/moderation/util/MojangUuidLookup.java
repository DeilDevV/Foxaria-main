package com.foxaria.moderation.util;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Разрешение UUID по нику для online-mode (Mojang API). Без успешного ответа — пусто.
 */
public final class MojangUuidLookup {

    private static final Pattern ID_JSON = Pattern.compile("\"id\"\\s*:\\s*\"([a-fA-F0-9]{32})\"");

    private MojangUuidLookup() {
    }

    public static Optional<UUID> fromPlayerName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String trimmed = name.trim();
        if (trimmed.length() > 16) {
            return Optional.empty();
        }
        try {
            String enc = URLEncoder.encode(trimmed, StandardCharsets.UTF_8);
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + enc);
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(4500);
            c.setReadTimeout(4500);
            c.setRequestMethod("GET");
            c.setRequestProperty("Accept", "application/json");
            int code = c.getResponseCode();
            if (code != 200) {
                return Optional.empty();
            }
            String json = new String(c.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            Matcher m = ID_JSON.matcher(json);
            if (!m.find()) {
                return Optional.empty();
            }
            String hex = m.group(1).toLowerCase(Locale.ROOT);
            return Optional.of(uuidFromUndashedHex(hex));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static UUID uuidFromUndashedHex(String hex32) {
        return UUID.fromString(hex32.replaceFirst(
            "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
            "$1-$2-$3-$4-$5"));
    }
}
