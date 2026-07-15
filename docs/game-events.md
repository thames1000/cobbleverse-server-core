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

Event types in 0.6.0: `player_joined`, `player_left`, `pokemon_captured` (metadata: `species`, `shiny`),
`battle_won` (metadata: `battleKind` ∈ `{pvp, pvn, pvw, other}`, `format` — lowercased, e.g.
`singles`/`doubles`, `wildCapture`).

> **Note for consumers:** a successful **wild capture** fires *both* `pokemon_captured` **and**
> `battle_won` with `wildCapture=true` (Cobblemon ends the encounter as a battle victory). Objective
> handlers that count "battles won" should filter out `wildCapture=true` if they mean trainer battles.
> `battleKind` and `format` are normalized by the core to stable lowercase values; consumers should not
> depend on Cobblemon's raw casing.

Dispatch is **synchronous and exception-isolated** — one listener throwing never stops the others or
the producer. (An async queue can slot in behind `publish()` later without changing any contract.)

## Producing events

- **Player events**: the player lifecycle publishes `player_joined` / `player_left`.
- **Cobblemon events** go through `CobblemonGameEventAdapter` — the *only* class that imports
  Cobblemon. It subscribes to Cobblemon's `POKEMON_CAPTURED` and `BATTLE_VICTORY` events and republishes
  them as `pokemon_captured` / `battle_won`.

### Cobblemon dependency (optional)

The adapter is **compiled against Cobblemon 1.7.3+1.21.1** (`modCompileOnly`) but Cobblemon is **not
bundled and not required at runtime**. The core runs standalone; the adapter is only instantiated when
Cobblemon is actually installed (the bootstrap gates on the Fabric mod list). `/cvcore debug` shows
`cobblemon bridge: active|idle`.

> **Runtime verification:** the subscription is compile-verified against the real Cobblemon 1.7.3 API,
> but firing on an actual capture/battle can only be confirmed on a live Cobblemon server. Turn on
> `/cvcore debug events on` and catch something / win a battle to confirm the events flow.

## Debugging & testing (no Cobblemon needed)

```
/cvcore debug events on              # log every game event and the listeners attached
/cvcore debug publish capture <player> <species> [shiny]   # inject a synthetic capture
/cvcore debug events off
```

Debug logging is built into the bus itself (not a registered listener). With `events on`, publishing
(real or synthetic) logs a line like:

```
[GameEvent] pokemon_captured source=cobblemon player=<uuid> consumers=0 meta={species=pikachu, shiny=true}
```

`consumers=0` is expected in 0.6.0 — no subsystem subscribes yet; the first consumers (objective
handlers, statistics) arrive in 0.6.1, after which the count reflects them. `/cvcore debug` also
reports `game events: <n> published, <n> consumer(s), debug=<bool>`.

## For future subscribers (developer API)

```java
CoreServices.gameEvents().register(event -> {
    if (event.type().equals("pokemon_captured")) {
        // react — update a season objective, a statistic, a website, ...
    }
});
```

This is the seam every subsystem plugs into. As of **0.6.1** two real consumers are registered:

- **`SeasonObjectiveEventListener`** — advances season objectives from game events (see
  [seasons.md](seasons.md#event-driven-objectives-061)). The only class coupling game events to seasons.
- **`StatisticsGameEventListener`** — updates player statistics (`captures`, `shinies`, `battles_won`,
  `sessions`). View with `/stats` or `/cvcore player stats <player>`.

Both are exercisable with `/cvcore debug publish capture <player> <species> [shiny]` — inject a capture
and watch a season objective advance and the stat tick up, all without Cobblemon.

## Roadmap within 0.6.x

| Version | Adds |
|---------|------|
| 0.6.0   | Bus, event contract, player events, real Cobblemon adapter (capture + battle), debug tooling |
| 0.6.1   | Objective handlers (event-driven) + player statistics — **this release** |
| 0.6.2   | Raid / evolution / breeding / fishing events |
| 0.6.3   | Event replay, analytics |
