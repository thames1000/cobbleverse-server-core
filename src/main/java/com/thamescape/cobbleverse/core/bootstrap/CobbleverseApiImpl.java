package com.thamescape.cobbleverse.core.bootstrap;

import com.thamescape.cobbleverse.core.api.CobbleverseApi;
import com.thamescape.cobbleverse.core.game.GameEvent;
import com.thamescape.cobbleverse.core.game.GameEventBus;
import com.thamescape.cobbleverse.core.reward.RewardResult;
import com.thamescape.cobbleverse.core.reward.RewardService;
import com.thamescape.cobbleverse.core.season.SeasonService;
import com.thamescape.cobbleverse.core.statistics.StatisticsService;

import java.util.UUID;

/** The core's {@link CobbleverseApi} implementation: thin, read-and-act delegation to the live services. */
final class CobbleverseApiImpl implements CobbleverseApi {

    private final SeasonService seasons;
    private final StatisticsService statistics;
    private final RewardService rewards;
    private final GameEventBus gameEvents;

    CobbleverseApiImpl(SeasonService seasons, StatisticsService statistics, RewardService rewards,
                       GameEventBus gameEvents) {
        this.seasons = seasons;
        this.statistics = statistics;
        this.rewards = rewards;
        this.gameEvents = gameEvents;
    }

    @Override
    public String activeSeasonId() {
        return seasons.configuredSeasonId();
    }

    @Override
    public boolean isSeasonActive() {
        return seasons.isConfiguredSeasonActive();
    }

    @Override
    public int seasonPoints(UUID player, String seasonId) {
        return seasons.points(player, seasonId);
    }

    @Override
    public long statistic(UUID player, String stat) {
        return statistics.value(player, stat);
    }

    @Override
    public RewardResult grantReward(UUID player, String rewardId, String source) {
        return rewards.grant(player, rewardId, source);
    }

    @Override
    public void publishGameEvent(GameEvent event) {
        gameEvents.publish(event);
    }
}
