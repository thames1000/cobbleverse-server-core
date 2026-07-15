# Changelog

All notable changes to Cobbleverse Server Core are documented here. This project adheres to
[Semantic Versioning](https://semver.org/).

## [0.6.0] - Unreleased

Game-event ingestion layer — the backbone for turning game-world actions into reactions across
seasons, events, statistics, and future modules. This release builds the bus and its contract;
consumers (objective handlers, statistics) arrive in 0.6.1.

### Added
- **Game-event bus** (`game/`): `GameEvent` (immutable-record contract: player, timestamp, type,
  source, metadata), `GameEventListener`, and `GameEventBus` (synchronous, exception-isolated
  dispatch; producers publish, consumers subscribe, neither imports the other).
- **Event types**: `PlayerJoinedGameEvent`, `PlayerLeftGameEvent`, `PokemonCapturedGameEvent`,
  `BattleWonGameEvent`.
- **Player events are live**: the player lifecycle listener publishes `player_joined` / `player_left`
  to the bus.
- **Cobblemon adapter** (`integration/cobblemon/CobblemonGameEventAdapter`): the single class that
  imports Cobblemon. Subscribes to Cobblemon's `POKEMON_CAPTURED` and `BATTLE_VICTORY` events and
  republishes them as `pokemon_captured` (species, shiny) and `battle_won` (battle kind, format, wild
  capture). Compiled against **Cobblemon 1.7.3+1.21.1** (`modCompileOnly`) — **not bundled, not
  required at runtime**; the core runs standalone and the bridge activates only when Cobblemon is
  installed (gated by the Fabric mod list; status via `/cvcore debug`).
- **Debug tooling**: `/cvcore debug events on|off` (log every game event) and
  `/cvcore debug publish capture <player> <species> [shiny]` (inject a synthetic event to test the
  whole pipeline without Cobblemon). `/cvcore debug` reports bus stats + bridge status.
- `CoreServices.gameEvents()` accessor.

### Fixed (from PR review)
- **Atomic config reload**: `reload()` validates the entire candidate set (every file + cross-file
  references) before swapping anything in; a rejected reload leaves the previous config live in full.
- `GameEventBus.publish` documents that it isolates listener `Exception`s (not "never throws") and
  guards against a null event.
- Debug logging is documented as built into the bus (not a listener); the count reads "consumer(s)".

### Notes
- Dispatch is synchronous for now; an async queue can slot in behind `publish()` later without
  changing the producer/listener contracts.
- The Cobblemon subscription is compile-verified against the real 1.7.3 API but must be confirmed
  firing on a live Cobblemon server — see docs/game-events.md.

## [0.5.2] - Unreleased

Season + event hardening (from code review) — transactionality, integrity, and recovery correctness.

### Fixed / Changed — Seasons
- **Atomic objective completion**: marking an objective complete and awarding its points now happen in
  one transaction, so a crash can't leave one without the other.
- **Atomic point changes**: `addPoints` reads and writes in one transaction.
- **Objective progress requires an ACTIVE season** — progress outside the active window returns
  `SEASON_NOT_ACTIVE` and awards nothing.
- **Objective progress clamps to `[0, required]`** (a negative correction can no longer go below zero).
- **Reversed season dates rejected**: `endsAt` must be after `startsAt`.
- **Clearer naming**: `configuredSeasonId()` / `configuredSeason()` (the season named by `core.json`,
  which may not be live) plus `isConfiguredSeasonActive()`.

### Fixed / Changed — Events
- **Distribution isn't marked complete on failure**: a completed event is only marked fully
  distributed when every grant landed in an accepted terminal state (delivered / queued / already
  claimed). Otherwise it stays pending and retries on the next startup.
- **Missing-definition events stay pending**: a `COMPLETED` event with no definition in `events.json`
  is no longer silently marked done — it stays pending with a loud `CV-EVENT-002` error. Reward loss
  now requires an explicit `/cvcore event rewards abandon <id>`.
- **Structured resume**: `resumePendingDistributions()` reports found / completed / still-pending /
  missing-definition counts.

### Added
- **Cross-config integrity**: season milestone rewards and event completion rewards must reference an
  existing, **non-repeatable** reward definition (validated at load and reload) — closing the replay
  double-grant risk the review flagged.
- Command `/cvcore event rewards abandon <id>`.

### Deferred (noted for later, per the review)
- Per-player event reward delivery records (stronger than the whole-event model); transactional state
  transitions (with auto-scheduling); granular `EVENT_REWARD_DISTRIBUTION_*` audit events.

## [0.5.1] - Unreleased

Event hardening (from code review) — transactionality fixes.

### Fixed
- **Resumable event reward distribution**: completing an event now marks its state `COMPLETED` and its
  reward distribution *pending* in one transaction, distributes, then marks it *done*. If the server
  crashes partway through rewarding participants, a startup sweep re-runs distribution for any event
  that is `COMPLETED` but not finished. Safe because `grant()` is idempotent, so already-rewarded
  participants are skipped. Previously a mid-distribution crash could leave some participants
  permanently un-rewarded. (migration `V006`; pre-0.5.1 completed events are treated as already
  distributed.)
- **Atomic score updates**: `EventRepository.addScore` now reads and updates a participant's score in
  a single operation and returns the before/after values, instead of a bare row-count.

### Changed
- `EventService.addScore` reports the score change (`score N -> M`).

## [0.5.0] - Unreleased

Events and leaderboards (first pass) — admin-driven event lifecycle to prove the state machine,
participation, and completion rewards; plus season and event leaderboards.

### Added
- **Event system** (`event/`): `EventDefinition`, `EventService`, `EventRepository`, `EventState`
  (DRAFT → SCHEDULED → OPEN → ACTIVE → COMPLETED, or CANCELLED), `EventParticipant`.
  - **State machine** with validated transitions; illegal moves are rejected.
  - **Participation**: players join open/active events; per-participant **score** for leaderboards.
  - **Completion rewards**: completing an event grants each participant the event's rewards through
    the central `RewardService` (dedup + offline queue inherited).
- **Leaderboards**: `/event leaderboard <id>` and `/season leaderboard`, plus `/cvcore season top [n]`
  (season points, using the V004 index).
- **Commands**: `/events`, `/event info|join|leave|leaderboard`; `/cvcore event
  list|open|start|complete|cancel|schedule <id>`, `/cvcore event addplayer <id> <player>`,
  `/cvcore event score <id> <player> <amount>`.
- **Config**: `events.json` (definitions, type, optional schedule, completion rewards) —
  runtime-reloadable.
- **Schema**: migration `V005` adds `events` and `event_participation` (with a leaderboard index).
- **Permissions**: `cobbleverse.command.events`, `cobbleverse.event.join`, `cobbleverse.event.leave`,
  `cobbleverse.admin.events`.
- Audit types `EVENT_STATE_CHANGED`, `EVENT_JOINED`, `EVENT_LEFT` (plus existing `EVENT_STARTED`,
  `EVENT_ENDED`).

### Not yet included (deliberately)
- Auto-scheduled transitions from `scheduledStart`/`scheduledEnd` (stored but not enforced) and
  event-type-specific logic (safari/tournament/raid) — first pass proves the lifecycle with
  admin-driven transitions.

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
