# Web Integration

Added in **0.7.0**. Two independent, **off-by-default** capabilities that connect the core to the
outside world, configured in `web.json`:

- a **read-only HTTP JSON API** a dashboard can poll, and
- **outbound webhooks** that push audited actions to other systems (Discord, ops tooling, …).

Neither adds a runtime dependency: the API uses the JDK's built-in HTTP server and webhooks use the
JDK HTTP client.

## Configuration (`web.json`)

```json
{
  "configVersion": 1,
  "api": {
    "enabled": false,
    "bindAddress": "127.0.0.1",
    "port": 7070,
    "apiKey": "",
    "leaderboardMaxLimit": 100
  },
  "webhooks": {
    "enabled": false,
    "timeoutSeconds": 10,
    "maxRetries": 3,
    "subscriptions": [
      {
        "name": "discord-example",
        "enabled": false,
        "url": "https://discord.com/api/webhooks/…",
        "format": "discord",
        "events": ["SEASON_CHANGED", "EVENT_STATE_CHANGED", "SEASON_OBJECTIVE_COMPLETED"]
      }
    ]
  }
}
```

`web.json` is **fixed at startup** and *not* runtime-reloadable (like `database.json`) — the API binds
a port and the webhooks attach to the audit stream once, so changing them takes a restart. Validation
is strict (`[CV-CONFIG-022]`): an enabled API needs a non-blank `apiKey` and an in-range `port`; an
enabled webhook needs a valid `http(s)` `url`, a known `format`, and known audit types.

## Read-only HTTP API

Enable it, set a key, and (optionally) keep it on loopback behind a reverse proxy:

```json
"api": { "enabled": true, "bindAddress": "127.0.0.1", "port": 7070, "apiKey": "change-me" }
```

Authenticate every request except `/health` with either header:

```
X-API-Key: change-me
Authorization: Bearer change-me
```

| Method + path | Auth | Returns |
|---|---|---|
| `GET /health` | no | overall status, version, per-check results |
| `GET /api/v1/season?id=<seasonId>` | yes | season state (id omitted → the configured season) |
| `GET /api/v1/leaderboard?season=<id>&limit=N` | yes | season points leaderboard (limit clamped to `leaderboardMaxLimit`) |
| `GET /api/v1/event?id=<eventId>` | yes | event state + participant standings |
| `GET /api/v1/player/<uuid>` | yes | profile + configured-season progress + stats |
| `GET /api/v1/stats/<uuid>` | yes | `captures` / `shinies` / `battles_won` / `sessions` |

Errors are JSON (`{"error": "..."}`) with a matching status: `400` (bad param/uuid), `401` (missing or
wrong key), `404` (unknown route or entity), `405` (non-GET — the API is read-only), `500` (isolated
internal error). Data is read through the DB worker; requests run on a small daemon thread pool.

**Security posture.** Default is loopback + mandatory key. Exposing it off-box is a deliberate
`bindAddress` change and should sit behind a reverse proxy terminating TLS. There is no write surface.

## Outbound webhooks

Webhooks forward **audited actions** to HTTP endpoints. Because the trigger is an audit entry,
auditing must be on (`core.json` `enableAuditLog`).

Each subscription selects audit types by name (see `AuditType`, e.g. `SEASON_CHANGED`,
`EVENT_STATE_CHANGED`, `SEASON_OBJECTIVE_COMPLETED`, `REWARD_GRANTED`) or a single `"*"` for all, and
a payload `format`:

- `generic` — a flat JSON object: `{ event, timestamp, source, actor, target?, context?, success, … }`
- `discord` — a one-embed Discord webhook body (title = event, colour by success/failure)

Delivery is **best-effort and asynchronous**: a POST that fails is retried with exponential backoff up
to `maxRetries`, then dropped with a `[CV-WEB-001]` warning. A missed notification is not persisted the
way a lost reward is — durable webhook delivery is intentionally out of scope for 0.7.0.

## Status

`/cvcore info` shows a **Web** line: whether the API is on (and its bind/port and running state) and
how many webhook subscriptions are active.
