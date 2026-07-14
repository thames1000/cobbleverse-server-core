# Changelog

All notable changes to Cobbleverse Server Core are documented here. This project adheres to
[Semantic Versioning](https://semver.org/).

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
