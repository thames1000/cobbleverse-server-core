# Seasons & Objectives

Implemented in **0.4.0**, with **event-driven objectives added in 0.6.1**. Seasons are time-boxed sets
of objectives that award points; reaching a points milestone grants a reward. Objectives can be
**manual** (set by admins or other modules) or **game-event-driven** (captures, battle wins — see
[Event-driven objectives](#event-driven-objectives-061)).

## Which season is active

`core.json`'s `activeSeason` names the current season by id; the definitions live in `seasons.json`.
A season's live state is **derived from its window**, not stored:

| State      | Meaning                                   |
|------------|-------------------------------------------|
| `DISABLED` | `enabled: false` in config                |
| `UPCOMING` | enabled, `startsAt` is in the future      |
| `ACTIVE`   | enabled, now within `[startsAt, endsAt)`  |
| `ENDED`    | enabled, `endsAt` has passed              |

Start/end transitions are detected on startup and once a minute, recorded (so each fires once), and
audited (`SEASON_CHANGED`).

## Defining seasons (`seasons.json`)

```json
{
  "configVersion": 1,
  "seasons": {
    "summer_2026": {
      "displayName": "Summer Splash",
      "startsAt": "2026-07-01T00:00:00-04:00",
      "endsAt": "2026-08-01T00:00:00-04:00",
      "enabled": true,
      "objectives": [
        { "id": "catch_25_shiny", "displayName": "Catch 25 shinies",
          "type": "capture_shiny", "required": 25, "points": 20 }
      ],
      "milestones": [
        { "points": 20, "reward": "summer_2026_tier_1" },
        { "points": 50, "reward": "summer_2026_tier_2" }
      ]
    }
  }
}
```

- `startsAt` / `endsAt` are ISO-8601 offset date-times (validated at load).
- `required` is the progress needed; `points` are awarded on completion.
- `milestones[].reward` is a reward definition id from `rewards.json`.
- `seasons.json` is **runtime-reloadable** with `/cvcore reload`.

## How progress works

1. **Objective progress** is added (`/cvcore season objective <player> <objective> <amount>`, or by
   another module calling `SeasonService.addObjectiveProgress`). Progress only counts while the season
   is **ACTIVE** (otherwise the call returns `SEASON_NOT_ACTIVE` and awards nothing), and is clamped to
   `[0, required]`.
2. On **completion**, the objective is marked complete, its `points` are awarded, **and** an owed
   record for every milestone the points cross is written to a durable outbox
   (`pending_milestone_rewards`) — all in a single transaction, so a crash can't leave any of them out
   of step. Further progress is a no-op.
3. Those owed rewards are then **delivered from the outbox**: each is granted via the central
   `RewardService` (dedups claim-once, queues if the player is offline) and its outbox row is deleted
   once the grant is accepted. Anything left pending by a crash is re-delivered on the next startup
   (`SeasonService.resumePendingMilestones`), exactly once. Async completions (event-driven) marshal
   delivery back onto the server thread. Milestone grants are idempotent, so they're safe to re-reach.

Points can be adjusted directly by admins (`/cvcore season addpoints`, may be negative; clamped at 0);
the read-and-write is atomic.

> **Integrity:** a milestone's `reward` must reference an existing, **non-repeatable** reward
> definition — validated when config loads. This keeps milestone re-crossing from ever granting a
> reward twice.

> **"Configured" vs "active":** `core.json`'s `activeSeason` names the *configured* current season,
> which may be upcoming, ended, or disabled. Its live state is derived from the window; only an
> `ACTIVE` season accrues objective progress.

## Commands

**Players:** `/season` (name, state, time remaining, your points), `/season progress` (objectives +
next milestone).

**Admins:** `/cvcore season info`, `/cvcore season progress <player>`,
`/cvcore season addpoints <player> <amount>`, `/cvcore season objective <player> <objective> <amount>`,
`/cvcore season top [count]`.

**Milestone-reward recovery** (the durable outbox, see below):

- `/cvcore season rewards pending` — list every milestone reward still owed (with its `#id`).
- `/cvcore season rewards retry` — re-attempt delivery of all pending rewards (same path as startup).
- `/cvcore season rewards abandon <id>` — drop a permanently-undeliverable entry (e.g. a reward id that
  was removed from config) so it stops retrying every startup. The player does **not** receive it; the
  action is audited.

## Storage (migration V004)

```
season_progress          (uuid, season_id, points)                              PK(uuid, season_id)
objective_progress       (uuid, season_id, objective_id, progress, completed)   PK(uuid, season_id, objective_id)
season_lifecycle         (season_id, state, updated_at)                         PK(season_id)
pending_milestone_rewards (id, uuid, season_id, reward_id, created_at)          UNIQUE(uuid, season_id, reward_id)  -- V008 outbox
```

Milestone reward claims live in the reward tables (`reward_claims`), so a milestone is granted once
per player automatically. The `pending_milestone_rewards` outbox (migration `V008`) holds owed grants
between the transaction that crosses a milestone and their delivery, so a crash can't lose one.

## Event-driven objectives (0.6.1)

Objectives can be **automatically advanced by game events** (via the game-event bus), not just by
admins. Set the objective `type` and its matcher fields:

| `type`             | Advances when…                          | Fields |
|--------------------|-----------------------------------------|--------|
| `manual`           | an admin/module adds progress           | — |
| `capture_species`  | a matching species is captured          | `species` (case-insensitive) |
| `capture_shiny`    | any shiny is captured                   | — |
| `capture_any`      | any Pokémon is captured                 | — |
| `battle_won`       | a (non-wild) battle is won              | `battleKind` (`pvp`/`pvn`/`pvw`; blank = any) |

```json
{ "id": "catch_magikarp_25", "displayName": "Catch 25 Magikarp",
  "type": "capture_species", "species": "magikarp", "required": 25, "points": 20 }
```

Auto-progress only counts while the season is **ACTIVE**. Wild captures do **not** count toward
`battle_won` (they'd double-count with capture objectives). Types are data-driven and matched by
registered `ObjectiveHandler`s in the `ObjectiveRegistry` — there is no central switch. Config
validation confirms each objective `type` against the **live registry** (not a closed built-in list),
both at startup and on every `/cvcore reload`, so an unhandled type fails fast instead of silently
never matching.

> **Custom handlers (developer API — planned for 1.0):** the registry is the intended extension point
> for third-party objective types, and its validation is already registry-driven. However, a public
> registration surface that lets an external mod register a handler *before* startup validation runs
> (a service-loader or init entrypoint) is **not exposed yet** — today the registry holds only the
> core's built-in handlers. If you're building on this, track the 1.0 developer API rather than relying
> on registration ordering now.

Since capture events aren't wired to real gameplay until you verify Cobblemon (0.6.0), test objective
auto-progress now with `/cvcore debug publish capture <player> <species> [shiny]`.
