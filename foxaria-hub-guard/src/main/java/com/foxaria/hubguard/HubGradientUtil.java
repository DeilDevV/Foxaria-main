package com.foxaria.hubguard;

/**
 * Лёгкий красный градиент для названий в GUI (ampersand + hex, без внешних зависимостей).
 */
public final class HubGradientUtil {

    private HubGradientUtil() {
    }

    /** Светлый красный градиент: от #FF6B6B к #FFB4B4. */
    public static String lightRedGradient(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        int r1 = 0xFF;
        int g1 = 0x6B;
        int b1 = 0x6B;
        int r2 = 0xFF;
        int g2 = 0xB4;
        int b2 = 0xB4;
        int visible = 0;
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                total++;
            }
        }
        if (total == 0) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                sb.append(c);
                continue;
            }
            float t = total <= 1 ? 0f : (float) visible / (float) (total - 1);
            int r = lerp(r1, r2, t);
            int g = lerp(g1, g2, t);
            int b = lerp(b1, b2, t);
            sb.append("&#").append(String.format("%02X%02X%02X", r, g, b)).append("&l").append(c);
            visible++;
        }
        return sb.toString();
    }

    private static int lerp(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }
}
