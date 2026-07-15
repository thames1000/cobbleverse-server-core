# Seasons & Objectives

Implemented in **0.4.0** (first pass). Seasons are time-boxed sets of objectives that award points;
reaching a points milestone grants a reward. This first pass ships **manual** objectives — progress is
set by admins or other modules — to prove the lifecycle, persistence, and reward wiring. Event-driven
objectives (catches, battles, raids) register handlers in a later version.

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
        { "id": "catch_water_25", "displayName": "Catch 25 Water Pokémon",
          "type": "manual", "required": 25, "points": 20 }
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
   another module calling `SeasonService.addObjectiveProgress`). Progress is capped at `required`.
2. On **completion**, the objective's `points` are awarded and the objective is marked complete
   (further progress is a no-op).
3. Awarding points that **cross a milestone** grants that milestone's reward via the central
   `RewardService` — so it dedups (claim-once) and queues if the player is offline. Milestone grants
   are idempotent, so they're safe to re-reach.

Points can be adjusted directly by admins (`/cvcore season addpoints`, may be negative; clamped at 0).

## Commands

**Players:** `/season` (name, state, time remaining, your points), `/season progress` (objectives +
next milestone).

**Admins:** `/cvcore season info`, `/cvcore season progress <player>`,
`/cvcore season addpoints <player> <amount>`, `/cvcore season objective <player> <objective> <amount>`.

## Storage (migration V004)

```
season_progress    (uuid, season_id, points)                         PK(uuid, season_id)
objective_progress (uuid, season_id, objective_id, progress, completed)  PK(uuid, season_id, objective_id)
season_lifecycle   (season_id, state, updated_at)                    PK(season_id)
```

Milestone reward claims live in the reward tables (`reward_claims`), so a milestone is granted once
per player automatically.

## Extending with event-driven objectives (later)

`ObjectiveType` / `ObjectiveHandler` / `ObjectiveRegistry` are the seam. A future module registers a
handler for, say, `catch_type`, subscribes to the relevant game event, and calls
`SeasonService.addObjectiveProgress(...)` — no change to the season core. Register via
`CoreServices.seasons().objectiveRegistry().register(handler)`.
