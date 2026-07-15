# Developer API

Added in **0.8.0**. The `api/` package is the supported surface for other mods to build on Cobbleverse
Server Core. Two things live here:

- **Extend** the core — add objective types, event listeners, currencies, health checks — by
  implementing `CobbleverseExtension`.
- **Consume** the core — read season/stat data, grant rewards, publish game events — through the
  `CobbleverseApi` facade.

> **Experimental until 1.0.** These types may change before they are frozen in 1.0.0 (Stable public
> API). Everything outside `api/` is internal and may change without notice.

## Depending on the core

Your mod compiles against the core and declares it as a dependency in `fabric.mod.json`
(`"depends": { "cobbleverse-server-core": ">=0.8.0" }`). The core is server-side; extensions load on
the (dedicated or integrated) server.

## Extending the core

Implement `CobbleverseExtension` and declare it as a Fabric entrypoint under the **`cobbleverse`** key:

```java
public final class MyExtension implements CobbleverseExtension {
    @Override
    public void registerCobbleverse(CobbleverseRegistrar registrar) {
        registrar
            .objectiveHandler(new WeeklyLoginObjectiveHandler())   // a new season objective type
            .gameEventListener(new MyAnalyticsListener())          // consume capture/battle/join events
            .currencyProvider(new MyCurrencyProvider())            // a reward-addressable currency
            .healthCheck(new MyServiceHealthCheck());              // surfaced by /cvcore health + web API
    }
}
```

```json
// fabric.mod.json
"entrypoints": {
  "cobbleverse": [ "com.example.MyExtension" ]
}
```

**Timing matters, and the core handles it for you.** Every extension runs **once during startup,
before configuration is validated** — so a custom objective `type` you register is recognised by
config validation (and by `/cvcore reload`) instead of being rejected as unknown. Keep
`registerCobbleverse` fast and side-effect-free: register handlers, don't touch the world (the server
is still coming up). An extension that throws — or a jar that fails to load — is **isolated and
logged**, never fatal to the server.

### What you can register

| Method | You implement | Effect |
|---|---|---|
| `objectiveHandler` | `ObjectiveHandler` | Adds a season objective `type` (its `type()` becomes valid in `seasons.json`) |
| `gameEventListener` | `GameEventListener` | Subscribes to the game-event bus (captures, battles, joins, custom events) |
| `currencyProvider` | `CurrencyProvider` | Adds a currency addressable by `id()` in reward configs |
| `healthCheck` | `HealthCheck` | Adds a diagnostic to `/cvcore health` and the web API health endpoint |

## Consuming the core

Once the server has started, get the facade and use it:

```java
if (CobbleverseApi.isReady()) {
    CobbleverseApi api = CobbleverseApi.get();
    int points = api.seasonPoints(playerUuid, api.activeSeasonId());
    long captures = api.statistic(playerUuid, "captures");
    api.grantReward(playerUuid, "weekly_bonus", "my-mod");
    api.publishGameEvent(myCustomEvent);
}
```

`CobbleverseApi.get()` throws if called before the core has published its services (before
`SERVER_STARTED`); guard with `isReady()` or call from server-start onward. The facade is read-and-act
only — there is no way to reach into internal services through it.

## Versioning

The `api/` package follows the project's semantic versioning. Until **1.0.0**, treat it as
experimental: additive changes are likely and a breaking change is possible on a minor bump. From 1.0
onward it is a stable contract. Program against `api/` types only; anything you reach for outside that
package is unsupported.
