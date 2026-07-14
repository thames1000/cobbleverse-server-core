# Integrations

Third-party mods are wrapped behind `Integration` classes and detected at runtime from the Fabric
mod list. The core does **not** compile against any of them, so a missing mod reports `unavailable`
instead of crashing.

## Detected mods (0.1.0)

| Integration    | Fabric mod id    | Role (current / future)                              |
|----------------|------------------|------------------------------------------------------|
| LuckPerms      | `luckperms`      | Permission provider (via fabric-permissions-api)     |
| Cobblemon      | `cobblemon`      | Capture / battle / hatch / raid objective sources    |
| Ledger         | `ledger`         | Block & inventory history (core audits its own actions) |
| SkiesCrates    | `skiescrates`    | Crate keys and crate-open tracking (0.3.0)           |
| CobbleDollars  | `cobbledollars`  | Backs the `cobbledollars` currency provider (0.3.0)  |
| HoloDisplays   | `holodisplays`   | Hologram surfaces for placeholders                   |
| PlaceholderAPI | `placeholder-api`| `%cobbleverse_*%` placeholders                        |

## Contract

Each integration must:

1. Detect whether its mod exists.
2. Report its status without ever throwing from `detect()` (errors become an `ERROR` report).
3. Register hooks only when the mod is confirmed present.
4. Surface status through `/cvcore integrations` and the integration health check.

## Adding an integration

Extend `AbstractModIntegration` with an id, display name and Fabric mod id, then register it in
`IntegrationManager.registerDefaults()`. Override `detect()` when you need version compatibility
checks or want to wire up listeners once the mod is confirmed present.

```java
public final class ExampleIntegration extends AbstractModIntegration {
    public ExampleIntegration() {
        super("example", "Example", "example-mod-id");
    }
}
```

Actual API calls into a detected mod (giving keys, transferring currency, listening to captures)
should be isolated to that integration's package and guarded by `isLoaded()` / reflection so the core
still compiles and runs without the mod.
