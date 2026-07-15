# Events & Leaderboards

Implemented in **0.5.0** (first pass). A server event is a lifecycle-managed activity players join;
on completion, every participant receives the event's rewards. This first pass is **admin-driven**
(console-testable) — auto-scheduling and event-type-specific logic come later.

## Lifecycle

```
DRAFT ─▶ SCHEDULED ─▶ OPEN ─▶ ACTIVE ─▶ COMPLETED
  └──────────┴─────────┴────────┴──▶ CANCELLED
```

Transitions are validated — an illegal move (e.g. `DRAFT → ACTIVE`, or anything out of a terminal
state) is rejected. Definitions come from `events.json`; the live state and participants are stored
in the database. An event with no state row yet is `DRAFT`.

| Transition (admin) | From → To |
|--------------------|-----------|
| `schedule`         | DRAFT → SCHEDULED |
| `open`             | DRAFT/SCHEDULED → OPEN (players may join) |
| `start`            | OPEN → ACTIVE |
| `complete`         | ACTIVE → COMPLETED (distributes rewards) |
| `cancel`           | any non-terminal → CANCELLED |

## Defining events (`events.json`)

```json
{
  "configVersion": 1,
  "events": {
    "summer_catchathon": {
      "displayName": "Summer Catch-a-thon",
      "description": "Catch as many as you can!",
      "type": "catching",
      "scheduledStart": "2026-07-20T18:00:00-04:00",
      "scheduledEnd": "2026-07-20T20:00:00-04:00",
      "rewards": [ "summer_2026_tier_1" ]
    }
  }
}
```

- `type` is informational for now (safari / tournament / catching / raid / community_goal).
- `scheduledStart` / `scheduledEnd` are stored for future auto-scheduling — **not enforced in 0.5.0**
  (you drive transitions with commands).
- `rewards` are reward definition ids granted to each participant on completion.
- `events.json` is **runtime-reloadable** with `/cvcore reload`.

## Participation & scoring

- Players join with `/event join <id>` while the event is OPEN or ACTIVE; `/event leave <id>` to drop.
- Admins can add a participant from console: `/cvcore event addplayer <id> <player>`.
- Each participant has a **score**; admins adjust it with `/cvcore event score <id> <player> <amount>`
  (another module could drive it from game events later). Score can't go below 0, and only
  participants can be scored.

## Completion rewards

Completing an event grants each participant every reward id in the definition, through the central
`RewardService` — so they **dedup** (claim-once) and **queue** for offline players (delivered on their
next join, with the retry/dead-letter safety net from 0.3.1).

**Crash-safe (0.5.1):** completion marks the event `COMPLETED` and its distribution *pending* in one
transaction, then distributes, then marks it *done*. If the server crashes mid-distribution, a
startup sweep re-runs it for any `COMPLETED` event that didn't finish — and because grants are
idempotent, already-rewarded participants are skipped. No participant is silently missed, and none is
double-rewarded.

## Leaderboards

- `/event leaderboard <id>` — top participants by score.
- `/season leaderboard` and `/cvcore season top [n]` — top players by season points (uses the
  `idx_season_progress_leaderboard` index from V004).

Names come from stored profiles (`last_known_name`); a player never seen shows a short UUID.

## Commands

**Players:** `/events`, `/event info <id>`, `/event join <id>`, `/event leave <id>`,
`/event leaderboard <id>`, `/season leaderboard`.

**Admins:** `/cvcore event list`, `/cvcore event open|start|complete|cancel|schedule <id>`,
`/cvcore event addplayer <id> <player>`, `/cvcore event score <id> <player> <amount>`,
`/cvcore season top [n]`.

## Storage (migration V005)

```
events              (event_id, state, started_at, ended_at, updated_at, rewards_distributed)   PK(event_id)
event_participation (event_id, uuid, joined_at, score)                                         PK(event_id, uuid)
```

`rewards_distributed` (V006) is `0` while a completed event is still handing out rewards, `1` once
done — the flag a startup resume checks.

## Console test flow (no login needed)

```
cvcore event open sample_event
cvcore event addplayer sample_event Notch
cvcore event start sample_event
cvcore event score sample_event Notch 10
event leaderboard sample_event          # Notch — 10
cvcore event complete sample_event      # queues sample_tier_1 for Notch
cvcore reward queue Notch               # shows the queued reward
```
