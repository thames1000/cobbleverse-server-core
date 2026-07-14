# Changelog

All notable changes to Cobbleverse Server Core are documented here. This project adheres to
[Semantic Versioning](https://semver.org/).

## [0.4.0] - Unreleased

Seasons and objectives (first pass) — manual/generic objectives to prove the season lifecycle,
persistence, and reward milestones before wiring event-driven (Cobblemon) tracking in a later version.

### Added
- **Season system** (`season/`): `SeasonDefinition`, `SeasonService`, `SeasonRepository`,
  `SeasonState` (upcoming / active / ended / disabled, derived from the configured window),
  `SeasonProgress`, `ObjectiveDefinition`, `ObjectiveProgress`, `Milestone`.
- **Objectives**: `ObjectiveType`, `ObjectiveRegistry`, `ObjectiveHandler` and a `ManualObjectiveHandler`.
  0.4.0 ships only the `manual` type (progress set by admins / other modules); event-driven types
  register handlers here later — no central switch. The registry is exposed via
  `SeasonService.objectiveRegistry()` for future modules.
- **Season points + milestones**: completing an objective awards its points; crossing a points
  milestone grants a reward through the central `RewardService` (so milestones inherit claim dedup and
  offline queueing).
- **Lifecycle detection**: season start/end transitions are detected on startup and once a minute,
  audited (`SEASON_CHANGED`) and recorded so each transition fires once.
- **Commands**: `/season`, `/season progress`; `/cvcore season info | progress <player> |
  addpoints <player> <amount> | objective <player> <objective> <amount>`.
- **Config**: `seasons.json` (definitions, objectives, milestones) — runtime-reloadable. Which season
  is current is named by `core.json`'s `activeSeason`.
- **Schema**: migration `V004` adds `season_progress`, `objective_progress`, `season_lifecycle`.
- **Permissions**: `cobbleverse.command.season`, `cobbleverse.season.view`,
  `cobbleverse.season.progress`, `cobbleverse.admin.season`.
- Audit types `SEASON_POINTS_CHANGED`, `SEASON_OBJECTIVE_COMPLETED`.

### Not yet included (deliberately)
- Cobblemon capture/battle/raid objective tracking — first pass proves lifecycle with manual
  objectives; event-driven handlers come with 0.6.0.

## [0.3.1] - Unreleased

Reward recovery hardening (from code review) — makes rewards recoverable when an integration
temporarily fails, before seasons build on them.

### Added
- **Per-entry reward results** (`reward_entry_results`, migration `V003`): each reward entry's outcome
  is tracked, so retrying a partial grant re-runs **only** the entries that haven't succeeded — items
  and currency already granted are never re-granted.
- **Claim completion status**: a non-repeatable claim is `PARTIAL` until every entry succeeds, then
  `COMPLETE`. "Already claimed" now means genuinely complete, so partial grants remain retryable by
  re-running the grant.
- **Queue durability**: a failing queued delivery is **retained**, counts attempts, and dead-letters
  after `maxDeliveryAttempts` (default 5) instead of being deleted. Offline re-grants reuse a single
  queue row per definition.
- **Admin recovery commands**: `/cvcore reward retry <player> [id]` (revive dead-lettered rewards and
  deliver if online) and `/cvcore reward queue <player>` (inspect queue status/attempts).
- **Slow-query logging**: database operations log WARN ≥ 50 ms, ERROR ≥ 250 ms.
- `rewards.json`: `maxDeliveryAttempts`.

### Changed
- `PlayerProfileService.createIfAbsent` now uses an atomic `INSERT OR IGNORE` (was check-then-upsert),
  so exactly one concurrent caller sees `CREATED`.
- Auto-retry: queued/partial rewards retry on the player's next join (skipping succeeded entries),
  dead-lettering after the attempt limit.

### Fixed
- Failed/unsupported queued rewards were being deleted after a delivery attempt (silent data loss);
  they are now retained and retried, or dead-lettered for admin review.

## [0.3.0] - Unreleased

### Added
- **Reward system** — one central `RewardService` all rewards flow through: validates, executes each
  entry, records a claim, audits, and returns a detailed result.
  - **Reward types**: `item` and `command` (native), `currency` (via the currency abstraction), and
    `crate_key` / `permission` / `pokemon` / `cosmetic` via configurable command templates.
  - **Duplicate-claim prevention**: non-repeatable definitions reserve their claim (DB primary key)
    before executing, so a reward can never be granted twice — even under concurrency or partial
    failure.
  - **Offline reward queue**: granting to an offline player queues the reward; it's delivered on their
    next join.
  - **Dry-run preview** (`/rewards preview`) describing what a definition would grant.
- **Currency abstraction** — `CurrencyProvider` interface with a DB-backed `InternalCurrencyProvider`
  (exact decimal balances + a transaction ledger) and a command-template `CommandCurrencyProvider`
  for CobbleDollars. Routed and audited centrally by `CurrencyService`.
- **Commands**: `/rewards`, `/rewards claim <id>`, `/rewards preview <id>`, `/cvcore reward list`,
  `/cvcore reward grant <player> <id>` (queues if the player is offline).
- **Config**: `rewards.json` — reward definitions, internal currency ids, and command templates.
  Runtime-reloadable via `/cvcore reload`.
- **Schema**: migration `V002` adds `reward_claims`, `reward_queue`, `currency_balances`,
  `currency_transactions`.
- **Permissions**: `cobbleverse.command.rewards`, `cobbleverse.reward.claim`,
  `cobbleverse.reward.preview`, `cobbleverse.admin.rewards`.
- Utilities: `ServerHolder`, `CommandRunner`.

### Notes
- Currency deposits/withdrawals and reward grants are audited (`CURRENCY_DEPOSIT`,
  `CURRENCY_WITHDRAW`, `REWARD_GRANTED`).
- Reward types backed by other mods use command templates (blank template = clearly reported as
  unsupported) rather than compiling against those mods.

## [0.2.1] - Unreleased

### Added
- `/cvcore player create <name>` — pre-creates a player profile without the player joining. Resolves
  the UUID exactly as the server would: deterministically in offline-mode, or via an async Mojang
  lookup in online-mode (never blocking the server thread). Never overwrites an existing profile.
- `PlayerProfileService.createIfAbsent(...)` backing the command.
- Permission node `cobbleverse.admin.player`.

## [0.2.0] - Unreleased

### Added
- **Persistence layer** (SQLite): `DatabaseManager` with a dedicated single worker thread (all SQL
  off the server thread), `DatabaseProvider` / `SqliteDatabaseProvider`, `TransactionManager`, and a
  versioned, automatic `MigrationManager` (`V001InitialSchema` → `player_profiles`, `audit_log`,
  `schema_version`). A file backup is taken before migrating an existing database.
- **Player profiles**: `PlayerProfile` (thin — identity + playtime), `PlayerProfileRepository`,
  `PlayerProfileCache`, `PlayerProfileService`, `PlayerSession`, and a `PlayerLifecycleListener` that
  loads on join and writes back on leave.
- **Scheduler**: `CoreScheduler` (tick-based repeating / one-shot tasks) with `PlaytimeUpdateTask`
  (playtime accrual) and `DatabaseFlushTask` (periodic write-behind).
- **Audit persistence**: audit entries now also write to the `audit_log` table (async).
- **Commands**: `/profile` and `/profile <player>`; `/cvcore database status`.
- **Health checks**: `DatabaseHealthCheck`, `SchedulerHealthCheck`.
- **Config**: `database.json` (backend, file path, flush/accrual intervals), loaded at startup and
  intentionally not runtime-reloadable.
- **Dependency**: `sqlite-jdbc` bundled jar-in-jar (first runtime dependency beyond Fabric).

### Changed
- `/cvcore info` now reports the real storage backend and file path.
- Shutdown flushes all cached profiles synchronously before closing the database.

### Permissions
- Added `cobbleverse.command.profile`, `cobbleverse.profile.view`,
  `cobbleverse.profile.view.other`, `cobbleverse.admin.database`.

## [0.1.0] - Unreleased

### Added
- Bootstrap layer with deterministic startup order and a startup report.
- Strict JSON configuration system (`ConfigLoader`, `ConfigValidator`, `ConfigManager`) that never
  silently replaces a broken config with defaults.
- Controlled service registry (`ServiceRegistry` / `CoreServices`).
- Permission service over `fabric-permissions-api` with operator-level fallback.
- `/cvcore` command tree: `info`, `health`, `integrations`, `reload`, `debug`.
- MiniMessage-subset message service rendering to native Minecraft `Text`.
- Runtime integration detection for LuckPerms, Cobblemon, Ledger, SkiesCrates, CobbleDollars,
  HoloDisplays and PlaceholderAPI (no compile-time coupling).
- Diagnostics / health checks (configuration, permissions, integrations).
- Audit service with structured logging and a bounded in-memory buffer.
- Error hierarchy with stable `CV-<AREA>-<NNN>` codes.
