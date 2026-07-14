# Database

Implemented in **0.2.0**. SQLite only; the provider abstraction leaves room for PostgreSQL / MariaDB
later.

## Location & config

The database file defaults to `config/cobbleverse-server-core/data/core.db` (server-global, not
per-world). Configure it in `database.json`:

```json
{
  "configVersion": 1,
  "type": "sqlite",
  "fileName": "data/core.db",
  "flushIntervalSeconds": 300,
  "playtimeAccrualSeconds": 60
}
```

`database.json` is loaded once at startup and is **not** runtime-reloadable — changing the backend or
file requires a full restart.

## Design rules (enforced)

- **All SQL runs off the server thread** on a single dedicated worker thread, so the connection is
  never touched concurrently (the simplest correct model for SQLite).
- **Prepared statements** everywhere (see the repositories).
- **Transactions** via `TransactionManager` for multi-statement writes (e.g. the batched profile
  flush; reward claims from 0.3.0).
- **Automatic, versioned migrations** in ascending order, each in its own transaction, recorded in
  `schema_version`. Idempotent — safe to run every startup.
- **Backup before migrating** an existing database: the WAL is checkpointed and the file is copied to
  `core.db.v<N>.bak` before pending migrations run.
- WAL journal mode, foreign keys on, 5s busy timeout.

## Threading model

| Operation                        | Path                                            |
|----------------------------------|-------------------------------------------------|
| Open connection, migrations      | synchronous, on the worker thread (startup)     |
| Join profile load, command reads | synchronous point queries on the worker thread  |
| Periodic flush, leave write-back | asynchronous (`runAsync` / `runInTransactionAsync`) |
| Shutdown flush                   | synchronous, before the connection closes       |

## Tables (V001)

```
schema_version    (version, name, applied_at)
player_profiles   (uuid, last_known_name, first_joined_at, last_joined_at, playtime_seconds)
audit_log         (id, timestamp, action, actor, actor_name, target, source,
                   context, before_value, after_value, success, failure_reason)
```

Later systems (seasons, rewards, events, currencies) add their own tables in their own migrations —
`player_profiles` stays thin and grows by migration rather than pre-created empty columns.

## Inspecting it

`/cvcore database status` reports connection state, schema version, stored profiles, cached/online
counts, profiles pending flush, queued writes, and audit rows.
