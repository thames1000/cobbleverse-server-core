# Changelog

All notable changes to Cobbleverse Server Core are documented here. This project adheres to
[Semantic Versioning](https://semver.org/).

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
