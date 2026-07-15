# Game Events

Implemented in **0.6.0** (ingestion layer). Game-world actions (a capture, a battle win, a player
joining) are published as `GameEvent`s to a central **bus**; subsystems subscribe as listeners. The
producer never imports the consumer and vice-versa — this is what lets seasons, events, statistics,
website sync, and future modules all react to the same action without knowing about each other.

```
Cobblemon capture ─┐
Player join ───────┼─▶ GameEventBus ─┬─▶ Season listener      (0.6.1)
Battle win ────────┘                 ├─▶ Statistics listener  (0.6.1)
                                     ├─▶ Website listener      (later)
                                     └─▶ Debug logger          (now)
```

## The contract

`GameEvent` (immutable records):

| Method | Meaning |
|--------|---------|
| `playerUuid()` | the player, or null for player-agnostic events |
| `timestamp()`  | when it happened |
| `type()`       | stable id, e.g. `pokemon_captured` |
| `source()`     | producing subsystem, e.g. `cobblemon` / `minecraft` |
| `metadata()`   | type-specific details a generic consumer can read (e.g. `species`, `shiny`) |

Event types in 0.6.0: `player_joined`, `player_left`, `pokemon_captured`, `battle_won`.

Dispatch is **synchronous and exception-isolated** — one listener throwing never stops the others or
the producer. (An async queue can slot in behind `publish()` later without changing any contract.)

## Producing events

- **Player events are live**: the player lifecycle publishes `player_joined` / `player_left`.
- **Cobblemon events** go through `CobblemonGameEventAdapter` — the *only* class that knows Cobblemon.
  It detects Cobblemon and exposes `publishCapture(...)` / `publishBattleWon(...)`.

### Cobblemon wiring status (important)

In 0.6.0 the adapter's **detection and publish seam are in place**, but the concrete subscription to
Cobblemon's capture/battle events is **not yet wired**, because it must be compiled against
Cobblemon's (Kotlin) event API for a specific Cobblemon version (which also pulls in
`fabric-language-kotlin` at runtime). That step is a follow-up once the target version is fixed. Until
then, capture/battle events won't fire from real gameplay — but you can exercise the entire pipeline
with the debug command below.

## Debugging & testing (no Cobblemon needed)

```
/cvcore debug events on              # log every game event and the listeners attached
/cvcore debug publish capture <player> <species> [shiny]   # inject a synthetic capture
/cvcore debug events off
```

With `events on`, publishing (real or synthetic) logs a line like:

```
[GameEvent] pokemon_captured source=cobblemon player=<uuid> listeners=1 meta={species=pikachu, shiny=true}
```

`/cvcore debug` also reports `game events: <n> published, <n> listener(s), debug=<bool>`.

## For future subscribers (developer API)

```java
CoreServices.gameEvents().register(event -> {
    if (event.type().equals("pokemon_captured")) {
        // react — update a season objective, a statistic, a website, ...
    }
});
```

This is the seam every later subsystem plugs into. 0.6.1 adds the first real consumers (objective
handlers that turn `type: "manual"` objectives into `capture_species` etc., and player statistics).

## Roadmap within 0.6.x

| Version | Adds |
|---------|------|
| 0.6.0   | Bus, event contract, player events, Cobblemon adapter scaffold, debug tooling |
| 0.6.1   | Objective handlers (event-driven), player statistics |
| 0.6.2   | Raid / evolution / breeding / fishing events |
| 0.6.3   | Event replay, analytics |
