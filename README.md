# Cobbleverse Server Core

[![CI](https://github.com/thames1000/cobbleverse-server-core/actions/workflows/ci.yml/badge.svg)](https://github.com/thames1000/cobbleverse-server-core/actions/workflows/ci.yml)

The shared server-side foundation for custom Cobbleverse features. It provides reusable systems —
configuration, permissions, commands, integrations, messaging, health checks and auditing — that
future feature modules (seasons, rewards, events, cosmetics, …) build on. The core does **not**
contain crates, shops, cosmetics or Pokémon rewards itself; those live in separate modules that
depend on this one.

- **Minecraft:** 1.21.1
- **Loader:** Fabric (Java 21)
- **License:** MIT

## Status — 0.7.0 (Web Integration)

Exposes the data the core already owns to the outside world, and pushes notable actions out. Two
independent, **off-by-default** capabilities in `web.json`: a **read-only HTTP JSON API** (leaderboards,
season/event state, player stats, health — loopback-bound and API-key protected) and **outbound
webhooks** (selected audited actions forwarded to Discord/ops endpoints, `generic` or `discord` shape).
No new runtime dependencies — the JDK's built-in HTTP server and client. See
[web integration](docs/web-integration.md).

Prior: 0.6.0 shipped the game-event bus + a real Cobblemon adapter; 0.6.1 added event-driven season
objectives and player statistics.

| System            | State                                                            |
|-------------------|-----------------------------------------------------------------|
| Configuration     | JSON loader + validator, strict (never silently defaults)       |
| Service registry  | Single controlled locator (`CoreServices`)                      |
| Permissions       | `fabric-permissions-api` with operator-level fallback           |
| Commands          | `/cvcore …`, `/profile`, `/rewards …`, `/season …`, `/event …`   |
| Messaging         | MiniMessage-subset formatting → native `Text`                   |
| Integrations      | Runtime detection of 7 mods (no compile-time coupling)          |
| Persistence       | SQLite, off-thread worker, versioned auto-migrations            |
| Player profiles   | Identity + playtime, cached, write-behind flush                 |
| Scheduler         | Tick-based repeating / one-shot tasks                           |
| Rewards           | Central service: validate, claim-once, offline queue + retry/dead-letter, preview |
| Currencies        | `CurrencyProvider` abstraction: internal (DB) + CobbleDollars    |
| Seasons           | Objectives, points, milestones (→ rewards), lifecycle detection |
| Events            | Lifecycle state machine, participation, completion rewards       |
| Leaderboards      | Season points + event score (`/…leaderboard`, `/cvcore season top`) |
| Game event bus    | Publish/subscribe ingestion layer; player events + Cobblemon capture/battle (optional dep) |
| Bus consumers     | Event-driven season objectives + player statistics              |
| **Web API**       | **Read-only HTTP JSON (leaderboards, season/event, player, stats, health); key-auth, loopback default** |
| **Webhooks**      | **Selected audited actions pushed to HTTP endpoints (generic / Discord)** |
| Health checks     | Config, permissions, integrations, database, scheduler          |
| Auditing          | Structured log + in-memory ring buffer + `audit_log` table      |

Event-driven objective tracking + statistics (consumers of the bus) and web integration arrive next —
see the [roadmap](#roadmap).

## Running a server?

See the **[Server Owner Guide](docs/server-owner-guide.md)** — install, configure, operate, upgrade
between versions, and (since this is your own project) modify the code and cut your own releases.

## Building

```bash
./gradlew build
```

The remapped jar lands in `build/libs/`. Drop it into a Fabric **server**'s `mods/` folder alongside
Fabric API. `fabric-permissions-api` is bundled (jar-in-jar), so no extra download is needed.

`./gradlew build` also runs the test suite and **Checkstyle** linting (config in
`config/checkstyle/checkstyle.xml`). Lint on its own: `./gradlew checkstyleMain checkstyleTest`.
[CI](.github/workflows/ci.yml) runs lint + build + tests on every push and PR.

## Commands

| Command                | Permission                     | Fallback | Purpose                          |
|------------------------|--------------------------------|----------|----------------------------------|
| `/cvcore info`         | `cobbleverse.command.cvcore`   | op 2     | Version and status summary       |
| `/cvcore health`       | `cobbleverse.command.cvcore`   | op 2     | Run diagnostics                  |
| `/cvcore integrations` | `cobbleverse.command.cvcore`   | op 2     | List detected mods               |
| `/cvcore reload`       | `cobbleverse.admin.reload`     | op 4     | Reload safe configuration        |
| `/cvcore debug`        | `cobbleverse.admin.debug`      | op 4     | Extended diagnostics             |
| `/cvcore database status` | `cobbleverse.admin.database` | op 4   | Database + profile diagnostics   |
| `/cvcore reward grant <player> <id>` | `cobbleverse.admin.rewards` | op 4 | Grant a reward (queues if offline) |
| `/profile [player]`    | `cobbleverse.command.profile`  | all      | View a player profile            |
| `/rewards [claim\|preview] <id>` | `cobbleverse.command.rewards` | all | Claim / preview rewards        |

`reload` only reloads safe configuration (`core.json`, `messages.json`) and re-detects integrations.
It never touches registries, world data, database drivers or mixins.

## Integrations

Detected at runtime via the Fabric mod list — the core never compiles against these mods, so a
missing one degrades gracefully:

LuckPerms · Cobblemon · Ledger · SkiesCrates · CobbleDollars · HoloDisplays · PlaceholderAPI

## Configuration

Lives under `config/cobbleverse-server-core/`. Files are created with defaults on first run and
validated at startup; a malformed file is backed up (`.broken`) and startup aborts with a clear
error rather than being silently replaced.

## Roadmap

| Version | Theme              |
|---------|--------------------|
| 0.1.0   | Foundation         |
| 0.2.0   | Player profiles + SQLite |
| 0.3.0   | Rewards + currency |
| 0.4.0   | Seasons + objectives |
| 0.5.0   | Events + leaderboards |
| 0.6.0   | Game event bus |
| 0.6.1   | Objective handlers + statistics (bus consumers) |
| 0.7.0   | Web integration (HTTP API + webhooks) *(this release)* |
| 1.0.0   | Stable public API |

See `docs/` for the [server owner guide](docs/server-owner-guide.md), architecture, commands,
permissions, configuration, [rewards](docs/rewards.md), [seasons](docs/seasons.md),
[events](docs/events.md), [game events](docs/game-events.md), [web integration](docs/web-integration.md),
[database](docs/database.md) and integration details.
