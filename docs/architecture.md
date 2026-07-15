# Architecture

Cobbleverse Server Core is the shared foundation other feature modules build on. It ships only
reusable systems; gameplay features (crates, cosmetics, safari, gyms, …) live in separate modules
that depend on the core — the core never depends on them.

## Package map

```
com.thamescape.cobbleverse.core
├── CobbleverseServerCore      entrypoint (ModInitializer)
├── CoreConstants              compile-time constants
├── bootstrap/                 startup order, service registry, dependency validation
├── config/                    JSON load / validate / manage (core + database)
├── permission/                fabric-permissions-api wrapper
├── command/                   /cvcore + /profile command trees
├── message/                   MiniMessage-subset formatting → Text
├── persistence/               SQLite manager, migrations, repositories, transactions
├── player/                    player profiles, cache, lifecycle, sessions
├── reward/                    reward service, handlers (type/), currency (currency/)
├── season/                    seasons, objectives (objective/), progress, milestones
├── event/                     event lifecycle, participation, leaderboards
├── game/                      game-event bus + event types (ingestion layer)
├── scheduler/                 tick-based repeating / one-shot tasks
├── integration/               runtime mod detection (per-mod subpackages)
├── audit/                     server-owned action log (+ audit_log table)
├── diagnostics/               health checks
└── util/                      time formatting, error hierarchy (util/error)
```

The `api` package (public API surface for other mods) is added in a later version — see the roadmap
in the README.

## Startup sequence

`CoreBootstrap.run()` runs a fixed order so failures are predictable:

1. Validate required mods (`DependencyValidator`).
2. Load + validate configuration.
3. Build services (permissions, messages, audit).
4. Register and detect integrations.
5. Register health checks.
6. Publish the `ServiceRegistry` into `CoreServices`.
7. Register commands.
8. Print the startup report.

A fatal error (missing dependency, invalid config) aborts initialization with a clear message rather
than leaving the core half-started.

## Service access

Every service is an ordinary, independently testable object. They are wired once into
`ServiceRegistry` and exposed through the static `CoreServices` locator:

```java
CoreServices.messages().send(source, MessageKey.RELOAD_SUCCESS);
CoreServices.integrations().isAvailable("cobblemon");
CoreServices.audit().record(AuditEntry.builder(AuditType.CONFIG_RELOAD));
```

This is a controlled locator, not ambient global state scattered through the codebase — access
before bootstrap completes throws, surfacing ordering bugs immediately.

## Integration philosophy

Third-party mods are detected via the Fabric mod list and wrapped behind `Integration` classes. The
core does **not** compile against Cobblemon, SkiesCrates, Ledger, CobbleDollars, HoloDisplays or
PlaceholderAPI, which keeps the build light and means a missing mod simply reports `unavailable`
instead of crashing. Concrete API hookups (giving crate keys, listening to captures, currency
transfers) are added behind those same wrappers in later versions.
