package com.thamescape.cobbleverse.core.message;

import com.thamescape.cobbleverse.core.config.ConfigLoader;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.Map;

/**
 * Central source of server-facing text. Owns the message templates and turns keys plus placeholders
 * into formatted {@link Text}. Commands should never hard-code strings; they go through here.
 */
public final class MessageService {

    private static final String MESSAGES_FILE = "messages.json";

    private final ConfigLoader loader;
    private volatile MessageConfig config;

    public MessageService(ConfigLoader loader) {
        this.loader = loader;
    }

    public void load() {
        this.config = loader.loadOrCreate(MESSAGES_FILE, MessageConfig.class, MessageConfig::defaults);
    }

    /** Re-reads the message templates from disk. Safe to call at runtime. */
    public void reload() {
        this.config = loader.loadOrCreate(MESSAGES_FILE, MessageConfig.class, MessageConfig::defaults);
    }

    /**
     * Resolves a message key with placeholders and returns formatted text (no prefix).
     *
     * @param key          message key, e.g. {@link MessageKey#NO_PERMISSION}
     * @param placeholders placeholder name to value, substituted for {@code <name>} tokens
     */
    public MutableText message(String key, Map<String, String> placeholders) {
        String template = config().messages.getOrDefault(key, key);
        return MiniText.parse(applyPlaceholders(template, placeholders));
    }

    public MutableText message(String key) {
        return message(key, Map.of());
    }

    /** Same as {@link #message} but with the configured prefix prepended. */
    public MutableText prefixed(String key, Map<String, String> placeholders) {
        return prefix().append(message(key, placeholders));
    }

    public MutableText prefixed(String key) {
        return prefixed(key, Map.of());
    }

    /** Parses an arbitrary MiniMessage string (with placeholders) into text. */
    public MutableText raw(String miniMessage, Map<String, String> placeholders) {
        return MiniText.parse(applyPlaceholders(miniMessage, placeholders));
    }

    /** The configured prefix as text. */
    public MutableText prefix() {
        return MiniText.parse(config().prefix);
    }

    /** Sends a prefixed message to a command source as feedback (not a broadcast). */
    public void send(ServerCommandSource source, String key, Map<String, String> placeholders) {
        source.sendFeedback(() -> prefixed(key, placeholders), false);
    }

    public void send(ServerCommandSource source, String key) {
        send(source, key, Map.of());
    }

    private static String applyPlaceholders(String template, Map<String, String> placeholders) {
        if (placeholders.isEmpty()) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            result = result.replace("<" + e.getKey() + ">", e.getValue());
        }
        return result;
    }

    private MessageConfig config() {
        MessageConfig current = config;
        if (current == null) {
            current = MessageConfig.defaults();
            this.config = current;
        }
        return current;
    }
}
