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

## Planned files

Later versions add `database.json`, `permissions.json`, `scheduler.json`, `integrations.json`,
`rewards.json`, `seasons.json` and `web-api.json`, each following the same versioned, strictly
validated pattern.
