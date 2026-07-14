# Events

> **Planned — 0.5.0.** Not implemented in 0.1.0.

Custom server events (safari, tournaments, catching events, raids, community goals) — distinct from
Fabric's event bus.

## Planned lifecycle states

```
DRAFT → SCHEDULED → OPEN → ACTIVE → COMPLETED
                                  ↘ CANCELLED
```

## Planned commands

Player: `/events`, `/event info <id>`, `/event join <id>`, `/event leave`, `/event leaderboard <id>`

Admin: `/cvcore event create|start|stop|cancel|reward <id>`

## Planned lifecycle events

```
EventScheduled  EventOpened  EventStarted  EventProgressChanged
EventCompleted  EventCancelled  EventRewardDistributed
```

Events distribute rewards through the central `RewardService` (0.3.0) and record actions through the
`AuditService`.
