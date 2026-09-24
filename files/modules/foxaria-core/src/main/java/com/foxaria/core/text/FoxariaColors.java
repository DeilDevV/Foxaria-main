package com.foxaria.core.text;

import org.bukkit.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Единая раскраска текста Foxaria.
 *
 * Поддерживает:
 *  - легаси-коды  &a &l ...
 *  - hex          &#RRGGBB  и  #RRGGBB
 *  - градиенты    <gradient:#FF8A00:#FFD166>ТЕКСТ</gradient>
 *
 * Зачем: префиксы донат-рангов приходят с прокси в hex-формате, а обычный
 * translateAlternateColorCodes их не понимает — из-за этого цвет доната
 * в сайдбаре и чате не применялся.
 *
 * Hex кодируется вручную в формат §x§R§R§G§G§B§B — он понимается клиентом
 * 1.16+ и не тянет зависимость от net.md_5.bungee.
 */
public final class FoxariaColors {

    private static final char SECTION = '\u00a7';
    private static final Pattern HEX = Pattern.compile("&?#([A-Fa-f0-9]{6})");
    private static final Pattern GRADIENT =
        Pattern.compile("<gradient:#([A-Fa-f0-9]{6}):#([A-Fa-f0-9]{6})>(.*?)</gradient>", Pattern.DOTALL);

    private FoxariaColors() {
    }

    /** Полная раскраска: градиенты → hex → легаси-коды. */
    public static String colorize(String input) {
        if (input == null || input.isEmpty()) {
            return input == null ? "" : "";
        }
        String out = applyGradients(input);
        out = applyHex(out);
        return ChatColor.translateAlternateColorCodes('&', out);
    }

    /** Текст без цветовых кодов — для сравнения, логов и ширины строк. */
    public static String strip(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return ChatColor.stripColor(colorize(input));
    }

    /** Градиент по символам между двумя hex-цветами. */
    public static String gradient(String text, String hexFrom, String hexTo) {
        String plain = strip(text);
        if (plain.isEmpty()) {
            return "";
        }
        int from = parseHex(hexFrom, 0xFFFFFF);
        int to = parseHex(hexTo, 0xFFFFFF);
        StringBuilder sb = new StringBuilder(plain.length() * 14);
        int len = plain.length();
        for (int i = 0; i < len; i++) {
            double ratio = len <= 1 ? 0.0 : (double) i / (len - 1);
            sb.append(encodeHex(blend(from, to, ratio))).append(plain.charAt(i));
        }
        return sb.toString();
    }

    private static String applyGradients(String input) {
        if (!input.contains("<gradient:")) {
            return input;
        }
        Matcher m = GRADIENT.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(gradient(m.group(3), m.group(1), m.group(2))));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String applyHex(String input) {
        if (input.indexOf('#') < 0) {
            return input;
        }
        Matcher m = HEX.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(encodeHex(parseHex(m.group(1), 0xFFFFFF))));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** #RRGGBB → §x§R§R§G§G§B§B (клиент 1.16+). */
    private static String encodeHex(int rgb) {
        String hex = String.format("%06X", rgb & 0xFFFFFF);
        StringBuilder sb = new StringBuilder(14);
        sb.append(SECTION).append('x');
        for (int i = 0; i < 6; i++) {
            sb.append(SECTION).append(Character.toLowerCase(hex.charAt(i)));
        }
        return sb.toString();
    }

    private static int blend(int from, int to, double ratio) {
        int r = interpolate((from >> 16) & 0xFF, (to >> 16) & 0xFF, ratio);
        int g = interpolate((from >> 8) & 0xFF, (to >> 8) & 0xFF, ratio);
        int b = interpolate(from & 0xFF, to & 0xFF, ratio);
        return (r << 16) | (g << 8) | b;
    }

    private static int interpolate(int a, int b, double ratio) {
        int v = (int) Math.round(a + (b - a) * ratio);
        return Math.max(0, Math.min(255, v));
    }

    private static int parseHex(String hex, int fallback) {
        try {
            return Integer.parseInt(hex.replace("#", "").trim(), 16);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
