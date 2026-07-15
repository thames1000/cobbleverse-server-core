package com.thamescape.cobbleverse.core.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validation rules for {@link WebConfig} (API + webhooks). Pure logic, no network. */
class ConfigValidatorWebTest {

    @Test
    void defaultsAreValid() {
        assertTrue(ConfigValidator.validate(WebConfig.defaults()).isEmpty(),
                "disabled-by-default web config must validate cleanly");
    }

    @Test
    void enabledApiWithoutKeyIsRejected() {
        WebConfig config = new WebConfig();
        config.api.enabled = true;
        config.api.apiKey = "";
        List<String> problems = ConfigValidator.validate(config);
        assertFalse(problems.isEmpty(), "an enabled API with no key must be rejected");
    }

    @Test
    void enabledApiWithBadPortIsRejected() {
        WebConfig config = new WebConfig();
        config.api.enabled = true;
        config.api.apiKey = "secret";
        config.api.port = 70000;
        assertFalse(ConfigValidator.validate(config).isEmpty(), "port out of range must be rejected");
    }

    @Test
    void enabledApiWithKeyAndPortIsValid() {
        WebConfig config = new WebConfig();
        config.api.enabled = true;
        config.api.apiKey = "secret";
        config.api.port = 7070;
        assertTrue(ConfigValidator.validate(config).isEmpty(), "a keyed, in-range API config is valid");
    }

    @Test
    void enabledWebhookWithBadUrlIsRejected() {
        WebConfig config = enabledWebhook("ftp://nope", "generic", List.of("SEASON_CHANGED"));
        assertFalse(ConfigValidator.validate(config).isEmpty(), "a non-http(s) webhook url must be rejected");
    }

    @Test
    void unknownAuditTypeIsRejected() {
        WebConfig config = enabledWebhook("https://example.com/hook", "discord", List.of("NOT_A_TYPE"));
        assertFalse(ConfigValidator.validate(config).isEmpty(), "an unknown audit type must be rejected");
    }

    @Test
    void unknownFormatIsRejected() {
        WebConfig config = enabledWebhook("https://example.com/hook", "xml", List.of("*"));
        assertFalse(ConfigValidator.validate(config).isEmpty(), "an unknown payload format must be rejected");
    }

    @Test
    void duplicateWebhookNameIsRejected() {
        WebConfig config = new WebConfig();
        config.webhooks.enabled = true;
        config.webhooks.subscriptions.add(subscription("dup", "https://a.example/x"));
        config.webhooks.subscriptions.add(subscription("dup", "https://b.example/y"));
        assertFalse(ConfigValidator.validate(config).isEmpty(), "duplicate webhook names must be rejected");
    }

    @Test
    void wellFormedWebhookIsValid() {
        WebConfig config = enabledWebhook("https://example.com/hook", "discord",
                List.of("SEASON_CHANGED", "*"));
        assertTrue(ConfigValidator.validate(config).isEmpty(), "a well-formed webhook config is valid");
    }

    @Test
    void disabledSubscriptionFieldsAreNotEnforced() {
        WebConfig config = new WebConfig();
        config.webhooks.enabled = true;
        WebConfig.Subscription sub = subscription("off", "not-a-url");
        sub.enabled = false; // disabled: url/format/events not enforced
        sub.events = List.of();
        config.webhooks.subscriptions.add(sub);
        assertTrue(ConfigValidator.validate(config).isEmpty(),
                "a disabled subscription must not fail validation on its unused fields");
    }

    private static WebConfig enabledWebhook(String url, String format, List<String> events) {
        WebConfig config = new WebConfig();
        config.webhooks.enabled = true;
        WebConfig.Subscription sub = subscription("test", url);
        sub.format = format;
        sub.events = events;
        config.webhooks.subscriptions.add(sub);
        return config;
    }

    private static WebConfig.Subscription subscription(String name, String url) {
        WebConfig.Subscription sub = new WebConfig.Subscription();
        sub.name = name;
        sub.enabled = true;
        sub.url = url;
        sub.format = "generic";
        sub.events = List.of("SEASON_CHANGED");
        return sub;
    }
}
