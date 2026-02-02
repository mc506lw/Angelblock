package top.mistmc.angelblock.util;

import net.md_5.bungee.api.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtils {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern GRADIENT_PATTERN = Pattern
            .compile("<gradient:#([A-Fa-f0-9]{6})-#([A-Fa-f0-9]{6})>([^<]+)</gradient>");

    public static String colorize(String message) {
        if (message == null)
            return null;

        String result = message;

        result = parseHexColors(result);
        result = parseGradientColors(result);
        result = org.bukkit.ChatColor.translateAlternateColorCodes('&', result);

        return result;
    }

    private static String parseHexColors(String message) {
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String hex = matcher.group(1);
            ChatColor color = ChatColor.of("#" + hex);
            matcher.appendReplacement(buffer, color.toString());
        }

        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String parseGradientColors(String message) {
        Matcher matcher = GRADIENT_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String startHex = matcher.group(1);
            String endHex = matcher.group(2);
            String text = matcher.group(3);

            String gradient = applyGradient(text, startHex, endHex);
            matcher.appendReplacement(buffer, gradient);
        }

        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String applyGradient(String text, String startHex, String endHex) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        java.awt.Color startColor = java.awt.Color.decode("#" + startHex);
        java.awt.Color endColor = java.awt.Color.decode("#" + endHex);

        StringBuilder result = new StringBuilder();
        int length = text.length();

        for (int i = 0; i < length; i++) {
            float ratio = (float) i / (length - 1);
            int r = (int) (startColor.getRed() + (endColor.getRed() - startColor.getRed()) * ratio);
            int g = (int) (startColor.getGreen() + (endColor.getGreen() - startColor.getGreen()) * ratio);
            int b = (int) (startColor.getBlue() + (endColor.getBlue() - startColor.getBlue()) * ratio);

            ChatColor color = ChatColor.of(String.format("#%02x%02x%02x", r, g, b));
            result.append(color).append(text.charAt(i));
        }

        return result.toString();
    }

    public static org.bukkit.Color parseBukkitColor(String colorString) {
        if (colorString == null || colorString.isEmpty()) {
            return org.bukkit.Color.WHITE;
        }

        if (colorString.startsWith("#")) {
            try {
                return org.bukkit.Color.fromRGB(
                        Integer.parseInt(colorString.substring(1), 16));
            } catch (NumberFormatException e) {
                return org.bukkit.Color.WHITE;
            }
        }

        String[] rgb = colorString.split(",");
        if (rgb.length == 3) {
            try {
                int r = Math.min(255, Math.max(0, Integer.parseInt(rgb[0].trim())));
                int g = Math.min(255, Math.max(0, Integer.parseInt(rgb[1].trim())));
                int b = Math.min(255, Math.max(0, Integer.parseInt(rgb[2].trim())));
                return org.bukkit.Color.fromRGB(r, g, b);
            } catch (NumberFormatException e) {
                return org.bukkit.Color.WHITE;
            }
        }

        return org.bukkit.Color.WHITE;
    }
}
