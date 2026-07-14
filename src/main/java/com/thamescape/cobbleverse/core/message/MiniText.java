package com.thamescape.cobbleverse.core.message;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Parses a practical subset of MiniMessage syntax into native Minecraft {@link Text}, so the core
 * can format messages without depending on Adventure / adventure-platform.
 *
 * <p>Supported tags:
 * <ul>
 *   <li>Named colors: {@code <red>}, {@code <dark_gray>}, ...</li>
 *   <li>Hex colors: {@code <#a1b2c3>} and {@code <color:#a1b2c3>}</li>
 *   <li>Decorations: {@code <bold>}, {@code <italic>}, {@code <underlined>},
 *       {@code <strikethrough>}, {@code <obfuscated>}</li>
 *   <li>{@code <reset>}</li>
 *   <li>{@code <gradient:#aaaaaa:#bbbbbb> ... </gradient>} (two-stop, linear)</li>
 *   <li>Closing tags {@code </red>}, {@code </bold>}, ...</li>
 * </ul>
 *
 * <p>Unknown tags are emitted as literal text so nothing is silently dropped.
 */
public final class MiniText {

    private MiniText() {
    }

    private static final Map<String, Integer> NAMED_COLORS = new HashMap<>();

    static {
        NAMED_COLORS.put("black", 0x000000);
        NAMED_COLORS.put("dark_blue", 0x0000AA);
        NAMED_COLORS.put("dark_green", 0x00AA00);
        NAMED_COLORS.put("dark_aqua", 0x00AAAA);
        NAMED_COLORS.put("dark_red", 0xAA0000);
        NAMED_COLORS.put("dark_purple", 0xAA00AA);
        NAMED_COLORS.put("gold", 0xFFAA00);
        NAMED_COLORS.put("gray", 0xAAAAAA);
        NAMED_COLORS.put("grey", 0xAAAAAA);
        NAMED_COLORS.put("dark_gray", 0x555555);
        NAMED_COLORS.put("dark_grey", 0x555555);
        NAMED_COLORS.put("blue", 0x5555FF);
        NAMED_COLORS.put("green", 0x55FF55);
        NAMED_COLORS.put("aqua", 0x55FFFF);
        NAMED_COLORS.put("red", 0xFF5555);
        NAMED_COLORS.put("light_purple", 0xFF55FF);
        NAMED_COLORS.put("yellow", 0xFFFF55);
        NAMED_COLORS.put("white", 0xFFFFFF);
    }

    /** Parses {@code input} into a {@link MutableText}. Never returns null. */
    public static MutableText parse(String input) {
        MutableText root = Text.empty();
        if (input == null || input.isEmpty()) {
            return root;
        }

        Deque<Frame> stack = new ArrayDeque<>();
        Gradient gradient = null;

        int i = 0;
        int len = input.length();
        while (i < len) {
            char c = input.charAt(i);
            if (c == '<') {
                int end = input.indexOf('>', i);
                if (end < 0) {
                    // Unterminated '<': treat the rest as literal.
                    emit(root, input.substring(i), stack, gradient);
                    break;
                }
                String tag = input.substring(i + 1, end).trim();
                i = end + 1;

                if (tag.startsWith("/")) {
                    String name = tag.substring(1).trim().toLowerCase(Locale.ROOT);
                    if (name.equals("gradient")) {
                        gradient = null;
                    } else {
                        popMatching(stack, name);
                    }
                    continue;
                }

                String lower = tag.toLowerCase(Locale.ROOT);
                if (lower.startsWith("gradient:")) {
                    gradient = parseGradient(input, i, tag);
                    continue;
                }

                UnaryOperator<Style> op = openTag(lower);
                if (op != null) {
                    stack.push(new Frame(lower, op));
                } else {
                    // Unknown tag: preserve it verbatim.
                    emit(root, "<" + tag + ">", stack, gradient);
                }
            } else {
                int next = input.indexOf('<', i);
                if (next < 0) {
                    next = len;
                }
                emit(root, input.substring(i, next), stack, gradient);
                i = next;
            }
        }
        return root;
    }

    private static void emit(MutableText root, String text, Deque<Frame> stack, Gradient gradient) {
        if (text.isEmpty()) {
            return;
        }
        Style base = effectiveStyle(stack);
        if (gradient == null) {
            root.append(Text.literal(text).setStyle(base));
            return;
        }
        for (int k = 0; k < text.length(); k++) {
            int rgb = gradient.colorAt();
            root.append(Text.literal(String.valueOf(text.charAt(k)))
                    .setStyle(base.withColor(TextColor.fromRgb(rgb))));
        }
    }

    private static Style effectiveStyle(Deque<Frame> stack) {
        Style style = Style.EMPTY;
        // Apply oldest-first so nearer tags win.
        Frame[] frames = stack.toArray(new Frame[0]);
        for (int k = frames.length - 1; k >= 0; k--) {
            style = frames[k].op.apply(style);
        }
        return style;
    }

    private static void popMatching(Deque<Frame> stack, String name) {
        // Remove the nearest frame with this tag name.
        Deque<Frame> temp = new ArrayDeque<>();
        boolean removed = false;
        while (!stack.isEmpty()) {
            Frame f = stack.pop();
            if (!removed && f.name.equals(name)) {
                removed = true;
                break;
            }
            temp.push(f);
        }
        while (!temp.isEmpty()) {
            stack.push(temp.pop());
        }
    }

    private static UnaryOperator<Style> openTag(String tag) {
        switch (tag) {
            case "bold":
            case "b":
                return s -> s.withBold(true);
            case "italic":
            case "i":
            case "em":
                return s -> s.withItalic(true);
            case "underlined":
            case "underline":
            case "u":
                return s -> s.withUnderline(true);
            case "strikethrough":
            case "st":
                return s -> s.withStrikethrough(true);
            case "obfuscated":
            case "obf":
                return s -> s.withObfuscated(true);
            case "reset":
                return s -> Style.EMPTY;
            default:
                break;
        }

        Integer rgb = resolveColor(tag);
        if (rgb != null) {
            int value = rgb;
            return s -> s.withColor(TextColor.fromRgb(value));
        }
        return null;
    }

    /** Resolves a color spec ({@code red}, {@code #a1b2c3}, {@code color:#a1b2c3}) to an rgb int. */
    private static Integer resolveColor(String spec) {
        String s = spec;
        if (s.startsWith("color:")) {
            s = s.substring("color:".length());
        }
        if (s.startsWith("#")) {
            return parseHex(s.substring(1));
        }
        return NAMED_COLORS.get(s);
    }

    private static Integer parseHex(String hex) {
        if (hex.length() != 6) {
            return null;
        }
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Gradient parseGradient(String input, int contentStart, String tag) {
        String[] parts = tag.split(":");
        // parts[0] == "gradient"
        Integer from = parts.length > 1 ? resolveColor(parts[1]) : null;
        Integer to = parts.length > 2 ? resolveColor(parts[2]) : null;
        if (from == null) {
            from = 0xFFFFFF;
        }
        if (to == null) {
            to = from;
        }
        int total = visibleLengthUntilGradientClose(input, contentStart);
        return new Gradient(from, to, Math.max(total, 1));
    }

    /** Counts visible (non-tag) characters from {@code start} up to the matching {@code </gradient>}. */
    private static int visibleLengthUntilGradientClose(String input, int start) {
        int count = 0;
        int i = start;
        int len = input.length();
        while (i < len) {
            char c = input.charAt(i);
            if (c == '<') {
                int end = input.indexOf('>', i);
                if (end < 0) {
                    break;
                }
                String tag = input.substring(i + 1, end).trim().toLowerCase(Locale.ROOT);
                i = end + 1;
                if (tag.equals("/gradient")) {
                    break;
                }
            } else {
                count++;
                i++;
            }
        }
        return count;
    }

    private record Frame(String name, UnaryOperator<Style> op) {
    }

    /** Two-stop linear gradient that advances one step per emitted character. */
    private static final class Gradient {
        private final int from;
        private final int to;
        private final int total;
        private int index;

        Gradient(int from, int to, int total) {
            this.from = from;
            this.to = to;
            this.total = total;
        }

        int colorAt() {
            float t = total <= 1 ? 0f : (float) index / (float) (total - 1);
            index++;
            int fr = (from >> 16) & 0xFF, fg = (from >> 8) & 0xFF, fb = from & 0xFF;
            int tr = (to >> 16) & 0xFF, tg = (to >> 8) & 0xFF, tb = to & 0xFF;
            int r = Math.round(fr + (tr - fr) * t);
            int g = Math.round(fg + (tg - fg) * t);
            int b = Math.round(fb + (tb - fb) * t);
            return (r << 16) | (g << 8) | b;
        }
    }
}
