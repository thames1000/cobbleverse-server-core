package com.thamescape.cobbleverse.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies that a reload is atomic: a cross-config failure leaves the previous config fully intact. */
class ConfigManagerReloadTest {

    @TempDir
    Path tmp;

    @Test
    void rejectedReloadKeepsPreviousConfigInFull() throws IOException {
        ConfigManager config = new ConfigManager(new ConfigLoader(tmp));
        config.load(); // writes defaults; the sample season/event reference reward 'sample_tier_1'
        assertTrue(config.rewards().definitions.containsKey("sample_tier_1"));

        // Rewrite rewards.json (individually valid) so 'sample_tier_1' no longer exists — this breaks
        // the season milestone and event completion references, i.e. cross-config validation fails.
        Files.writeString(tmp.resolve("rewards.json"),
                "{\"configVersion\":1,\"definitions\":{}}", StandardCharsets.UTF_8);

        List<String> problems = config.reload();

        assertFalse(problems.isEmpty(), "cross-config break must be reported");
        assertTrue(config.rewards().definitions.containsKey("sample_tier_1"),
                "a rejected reload must not swap in the new (empty) rewards config");
    }

    @Test
    void reloadRejectsObjectiveTypeWithNoRegisteredHandler() throws IOException {
        ConfigManager config = new ConfigManager(new ConfigLoader(tmp));
        config.load();
        // Simulate the objective registry knowing only the built-in 'manual' handler.
        config.addSemanticValidator(snapshot ->
                ConfigValidator.validateObjectiveTypes(snapshot.seasons(), java.util.Set.of("manual")));

        // Structurally valid, but its objective type has no registered handler — the exact silent-
        // degradation the startup check prevents, now caught on reload too.
        Files.writeString(tmp.resolve("seasons.json"), seasonJson("capture_shiny"), StandardCharsets.UTF_8);
        List<String> problems = config.reload();
        assertFalse(problems.isEmpty(), "an unregistered objective type must fail the reload");
        assertFalse(config.seasons().seasons.containsKey("reload_season"),
                "a semantically rejected reload keeps the previous seasons config in full");

        // Switching to a registered type reloads cleanly.
        Files.writeString(tmp.resolve("seasons.json"), seasonJson("manual"), StandardCharsets.UTF_8);
        assertTrue(config.reload().isEmpty(), "a registered objective type reloads cleanly");
        assertTrue(config.seasons().seasons.containsKey("reload_season"),
                "the accepted generation is now live");
    }

    private static String seasonJson(String objectiveType) {
        return "{\"configVersion\":1,\"seasons\":{\"reload_season\":{"
                + "\"displayName\":\"Reload\",\"enabled\":true,"
                + "\"startsAt\":\"2026-01-01T00:00:00Z\",\"endsAt\":\"2026-12-31T00:00:00Z\","
                + "\"objectives\":[{\"id\":\"obj1\",\"type\":\"" + objectiveType + "\",\"required\":3,\"points\":10}],"
                + "\"milestones\":[]}}}";
    }

    @Test
    void successfulReloadPublishesOneNewSnapshotAndKeepsDatabase() throws IOException {
        ConfigManager config = new ConfigManager(new ConfigLoader(tmp));
        config.load();
        ConfigSnapshot before = config.snapshot();

        // A valid change to a reloadable file.
        Files.writeString(tmp.resolve("core.json"),
                "{\"serverId\":\"changed\",\"activeSeason\":\"\"}", StandardCharsets.UTF_8);

        assertTrue(config.reload().isEmpty(), "a valid reload has no problems");

        ConfigSnapshot after = config.snapshot();
        assertNotSame(before, after, "a successful reload publishes a new snapshot (atomic swap)");
        assertEquals("changed", after.core().serverId);
        assertSame(before.database(), after.database(),
                "database config is carried forward unchanged (not runtime-reloadable)");
    }
}
