package com.thamescape.cobbleverse.core.message;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Message configuration, persisted as {@code config/cobbleverse-server-core/messages.json}.
 *
 * <p>{@link #prefix} is prepended to prefixed messages. {@link #messages} maps message keys to
 * MiniMessage-formatted templates; unknown keys fall back to the key itself so nothing crashes.
 */
public class MessageConfig {

    public int configVersion = 1;

    public String prefix =
            "<gradient:#52c7ff:#a77cff><bold>Cobbleverse</bold></gradient> <dark_gray>»</dark_gray> ";

    public Map<String, String> messages = defaultMessages();

    public static MessageConfig defaults() {
        return new MessageConfig();
    }

    private static Map<String, String> defaultMessages() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("no_permission", "<red>You do not have permission to do that.</red>");
        m.put("reload_success", "<green>Core configuration reloaded.</green>");
        m.put("reload_failed", "<red>Reload failed: <reason></red>");
        m.put("season_started", "<green>The <season> season has begun!</green>");
        m.put("reward_claimed", "<yellow>You claimed <reward>.</yellow>");
        return m;
    }
}
