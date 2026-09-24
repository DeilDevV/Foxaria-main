package com.foxaria.proxy;

import net.md_5.bungee.api.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hex &#RRGGBB → legacy §x, оранжевый градиент для бренда.
 */
public final class ProxyColorUtil {

    private static final Pattern HEX_COLOR = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private ProxyColorUtil() {
    }

    public static String colorize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String legacy = ChatColor.translateAlternateColorCodes('&', input);
        Matcher m = HEX_COLOR.matcher(legacy);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String hex = m.group(1);
            StringBuilder repl = new StringBuilder("§x");
            for (char ch : hex.toCharArray()) {
                repl.append('§').append(ch);
            }
            m.appendReplacement(out, Matcher.quoteReplacement(repl.toString()));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * Градиент тёмно-оранжевого к светлому по символам (латиница/кириллица).
     */
    public static String orangeGradient(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        int n = text.length();
        int r1 = 0xCC;
        int g1 = 0x55;
        int b1 = 0x00;
        int r2 = 0xFF;
        int g2 = 0xCC;
        int b2 = 0x99;
        StringBuilder sb = new StringBuilder();
        int visible = 0;
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                sb.append(c);
                continue;
            }
            float t = n <= 1 ? 0f : (float) visible / (float) Math.max(1, countVisible(text) - 1);
            int r = lerp(r1, r2, t);
            int g = lerp(g1, g2, t);
            int b = lerp(b1, b2, t);
            sb.append(rgbToLegacy(r, g, b));
            sb.append("§l");
            sb.append(c);
            visible++;
        }
        return sb.toString();
    }

    public static String orangeGradientNoBold(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        int n = text.length();
        int r1 = 0xCC;
        int g1 = 0x55;
        int b1 = 0x00;
        int r2 = 0xFF;
        int g2 = 0xCC;
        int b2 = 0x99;
        StringBuilder sb = new StringBuilder();
        int visible = 0;
        int total = countVisible(text);
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                sb.append(c);
                continue;
            }
            float t = total <= 1 ? 0f : (float) visible / (float) Math.max(1, total - 1);
            int r = lerp(r1, r2, t);
            int g = lerp(g1, g2, t);
            int b = lerp(b1, b2, t);
            sb.append(rgbToLegacy(r, g, b));
            sb.append(c);
            visible++;
        }
        return sb.toString();
    }

    private static int countVisible(String text) {
        int c = 0;
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                c++;
            }
        }
        return Math.max(1, c);
    }

    private static int lerp(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }

    private static String rgbToLegacy(int r, int g, int b) {
        String hex = String.format("%02x%02x%02x", r, g, b);
        StringBuilder repl = new StringBuilder("§x");
        for (char ch : hex.toCharArray()) {
            repl.append('§').append(ch);
        }
        return repl.toString();
    }

    /**
     * Берёт часть префикса до &8| (ранг для таба/чата).
     */
    public static String stripRankFromPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return "";
        }
        int idx = prefix.indexOf("&8|");
        if (idx > 0) {
            return prefix.substring(0, idx).trim();
        }
        return prefix.trim();
    }
}
