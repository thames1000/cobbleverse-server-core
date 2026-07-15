package com.thamescape.cobbleverse.core.api;

import com.thamescape.cobbleverse.core.game.GameEvent;
import com.thamescape.cobbleverse.core.reward.RewardResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The API holder: {@link CobbleverseApi#get()} throws before install, returns the instance after. */
class CobbleverseApiHolderTest {

    @AfterEach
    void reset() {
        CobbleverseApiHolder.install(null); // don't leak install state into other tests
    }

    @Test
    void getThrowsBeforeInstall() {
        CobbleverseApiHolder.install(null);
        assertFalse(CobbleverseApi.isReady());
        assertThrows(IllegalStateException.class, CobbleverseApi::get);
    }

    @Test
    void getReturnsTheInstalledInstance() {
        CobbleverseApi api = stub();
        CobbleverseApiHolder.install(api);
        assertTrue(CobbleverseApi.isReady());
        assertSame(api, CobbleverseApi.get());
    }

    private static CobbleverseApi stub() {
        return new CobbleverseApi() {
            @Override
            public String activeSeasonId() {
                return "season";
            }

            @Override
            public boolean isSeasonActive() {
                return false;
            }

            @Override
            public int seasonPoints(UUID player, String seasonId) {
                return 0;
            }

            @Override
            public long statistic(UUID player, String stat) {
                return 0;
            }

            @Override
            public RewardResult grantReward(UUID player, String rewardId, String source) {
                return null;
            }

            @Override
            public void publishGameEvent(GameEvent event) {
                // no-op stub
            }
        };
    }
}
