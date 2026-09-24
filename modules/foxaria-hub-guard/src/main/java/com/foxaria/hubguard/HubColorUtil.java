package com.foxaria.hubguard;

import org.bukkit.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** &#RRGGBB → §x…, затем & → § для чата/GUI. */
public final class HubColorUtil {

    private static final Pattern HEX_COLOR = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private HubColorUtil() {
    }

    public static String colorize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        Matcher m = HEX_COLOR.matcher(input);
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
        return ChatColor.translateAlternateColorCodes('&', out.toString());
    }
}
