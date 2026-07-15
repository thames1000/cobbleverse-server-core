# Configuration

All core config lives under `config/cobbleverse-server-core/`. Files are created with defaults on
first run and validated at startup.

## Rules

- Every config carries a `configVersion`.
- Config is validated at startup; invalid settings produce clear, specific errors.
- A broken config is **never** silently replaced with defaults — it is backed up (`<file>.broken`)
  and startup aborts.
- A valid file is copied to `<file>.bak` before a migration overwrites it.
- Only systems that support it may be reloaded at runtime (`core.json`, `messages.json`).

## `core.json`

```json
{
  "configVersion": 1,
  "debug": false,
  "serverId": "cobbleverse",
  "environment": "production",
  "defaultLocale": "en_us",
  "timezone": "America/New_York",
  "activeSeason": "",
  "enableAuditLog": true,
  "enableMetrics": true
}
```

Validation checks: positive `configVersion` not newer than the build supports, non-blank `serverId`
and `defaultLocale`, and a valid IANA `timezone`.

## `messages.json`

```json
{
  "configVersion": 1,
  "prefix": "<gradient:#52c7ff:#a77cff><bold>Cobbleverse</bold></gradient> <dark_gray>»</dark_gray> ",
  "messages": {
    "no_permission": "<red>You do not have permission to do that.</red>",
    "reload_success": "<green>Core configuration reloaded.</green>",
    "reload_failed": "<red>Reload failed: <reason></red>",
    "season_started": "<green>The <season> season has begun!</green>",
    "reward_claimed": "<yellow>You claimed <reward>.</yellow>"
  }
}
```

Templates use a MiniMessage subset (named + hex colors, decorations, two-stop gradients) and
`<name>` placeholders substituted at send time. Unknown message keys fall back to the key itself.

## `database.json` (0.2.0)

```json
{
  "configVersion": 1,
  "type": "sqlite",
  "fileName": "data/core.db",
  "flushIntervalSeconds": 300,
  "playtimeAccrualSeconds": 60
}
```

`type` must be `sqlite` (the only supported backend); `fileName` is relative to the config directory;
intervals must be positive. Unlike `core.json` / `messages.json`, this file is **not** runtime
reloadable — changing it requires a restart. See [database.md](database.md) for details.

## `rewards.json` (0.3.0)

Reward definitions, the core's internal currency ids, command templates for mod-backed reward types,
and `maxDeliveryAttempts` (queued deliveries dead-letter after this many failures). Created with a
sample definition on first run; **runtime-reloadable** via `/cvcore reload`. See
[rewards.md](rewards.md) for the full reference.

## `seasons.json` (0.4.0)

Season definitions with objectives and points milestones. The active season is named by `core.json`'s
`activeSeason`. Runtime-reloadable via `/cvcore reload`. See [seasons.md](seasons.md).

## Planned files

Later versions add `permissions.json`, `scheduler.json`, `integrations.json` and `web-api.json`, each
following the same versioned, strictly validated pattern.
