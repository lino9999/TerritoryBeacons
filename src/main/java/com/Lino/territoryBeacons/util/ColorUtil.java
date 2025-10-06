package com.Lino.territoryBeacons.util;

import net.md_5.bungee.api.ChatColor;
import java.awt.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtil {

    private static final Pattern GRADIENT_PATTERN = Pattern.compile("<gradient:#([A-Fa-f0-9]{6}):#([A-Fa-f0-9]{6})>(.*?)</gradient>");
    private static final Pattern HEX_PATTERN = Pattern.compile("<#([A-Fa-f0-9]{6})>");

    public static String format(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // Process gradients first
        Matcher gradientMatcher = GRADIENT_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (gradientMatcher.find()) {
            Color start = Color.decode("#" + gradientMatcher.group(1));
            Color end = Color.decode("#" + gradientMatcher.group(2));
            String content = gradientMatcher.group(3);
            gradientMatcher.appendReplacement(sb, Matcher.quoteReplacement(applyGradient(content, start, end)));
        }
        gradientMatcher.appendTail(sb);
        text = sb.toString();

        // Process normal hex colors
        Matcher hexMatcher = HEX_PATTERN.matcher(text);
        sb = new StringBuffer();
        while (hexMatcher.find()) {
            String hexColor = "#" + hexMatcher.group(1);
            hexMatcher.appendReplacement(sb, Matcher.quoteReplacement(ChatColor.of(hexColor).toString()));
        }
        hexMatcher.appendTail(sb);

        return sb.toString();
    }

    private static String applyGradient(String text, Color start, Color end) {
        StringBuilder builder = new StringBuilder();
        int length = text.length();

        // To apply gradient correctly, we need to handle existing color codes in the text
        String strippedText = ChatColor.stripColor(text);
        int strippedLength = strippedText.length();
        int charIndex = 0;

        for (int i = 0; i < length; i++) {
            char currentChar = text.charAt(i);
            if (currentChar == ChatColor.COLOR_CHAR) {
                // It's a color code, skip it and the next character
                builder.append(currentChar).append(text.charAt(++i));
            } else {
                // It's a normal character, apply gradient
                double ratio = (strippedLength == 1) ? 0.5 : (double) charIndex / (strippedLength - 1);
                int red = (int) (start.getRed() * (1 - ratio) + end.getRed() * ratio);
                int green = (int) (start.getGreen() * (1 - ratio) + end.getGreen() * ratio);
                int blue = (int) (start.getBlue() * (1 - ratio) + end.getBlue() * ratio);
                builder.append(ChatColor.of(new Color(red, green, blue))).append(currentChar);
                charIndex++;
            }
        }
        return builder.toString();
    }
}