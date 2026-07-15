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
    "leaderboardMaxLimit": 100,
    "maxConcurrentRequests": 6,
    "rateLimitPerMinute": 120,
    "trustForwardedFor": false
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
| `GET /health` | no | **liveness only** — `{"status":"OK"}` if the process is up (no DB, no diagnostics) |
| `GET /api/v1/health` | yes | full diagnostics: overall status, version, per-check details |
| `GET /api/v1/season?id=<seasonId>` | yes | season state (id omitted → the configured season) |
| `GET /api/v1/leaderboard?season=<id>&limit=N` | yes | season points leaderboard (limit clamped to `leaderboardMaxLimit`) |
| `GET /api/v1/event?id=<eventId>` | yes | event state + participant standings |
| `GET /api/v1/player/<uuid>` | yes | profile + configured-season progress + stats (`404` if unknown) |
| `GET /api/v1/stats/<uuid>` | yes | `captures` / `shinies` / `battles_won` / `sessions` (`404` if the player is unknown) |

Public `/health` is a **liveness probe** only — it never runs the check suite or touches the database,
so it is safe to expose and can't leak internal detail. The full, authenticated diagnostics live at
`/api/v1/health`.

Errors are JSON (`{"error": "..."}`) with a matching status: `400` (bad param/uuid/malformed query),
`401` (missing or wrong key), `404` (unknown route or entity), `405` (non-GET — the API is read-only),
`429` (rate limited), `503` (at the concurrency cap), `500` (isolated internal error).

**Protecting the shared database worker.** API reads go through the same single DB worker gameplay
uses, so the API is throttled two ways: `maxConcurrentRequests` (1–64) caps how many requests do DB
work at once (excess → `503` immediately, never queued onto the worker), and `rateLimitPerMinute` caps
requests per client per minute (`0` disables it; over → `429`). Requests run on a bounded daemon thread
pool with a bounded queue. Public `/health` is exempt from rate limiting, so an uptime monitor is never
throttled. (A dedicated read-only DB connection for the API is a candidate future enhancement; today
reads share the worker but are bounded.)

**Rate-limit identity behind a proxy.** By default the limiter keys on the socket's remote IP — behind
a loopback reverse proxy that is the proxy's address, so *every* dashboard user shares one budget. Set
`trustForwardedFor: true` to key on the first `X-Forwarded-For` hop instead. Enable this **only** when a
trusted proxy sets/overwrites that header, since a directly-reachable server would let clients spoof it
to evade the limit. Either way, prefer doing per-client rate limiting at the proxy too.

**Security posture.** Default is loopback + mandatory key + read-only (no write surface). Exposing it
off-box is a deliberate `bindAddress` change and should sit behind a reverse proxy terminating TLS and
doing its own rate limiting.

## Outbound webhooks

Webhooks forward **audited actions** to HTTP endpoints. Because the trigger is an audit entry,
auditing must be on (`core.json` `enableAuditLog`) — this is **enforced at startup** (`[CV-CONFIG-024]`):
enabling webhooks with auditing off is rejected loudly rather than silently delivering nothing.

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
