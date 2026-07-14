package com.thamescape.cobbleverse.core.reward;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/** Maps each {@link RewardType} to its {@link RewardHandler}. */
public final class RewardRegistry {

    private final Map<RewardType, RewardHandler> handlers = new EnumMap<>(RewardType.class);

    public void register(RewardHandler handler) {
        handlers.put(handler.type(), handler);
    }

    public Optional<RewardHandler> handler(RewardType type) {
        return Optional.ofNullable(handlers.get(type));
    }
}
