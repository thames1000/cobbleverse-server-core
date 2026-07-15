# Server Owner Guide

How to install, operate, upgrade, and (since this is your own project) modify and release new versions
of Cobbleverse Server Core.

This guide is kept in the repo so it stays in step with the code. **When you change behaviour, update
this file in the same commit.** See [Keeping this guide current](#12-keeping-this-guide-current).

## Contents

1. [What this mod is (and isn't)](#1-what-this-mod-is-and-isnt)
2. [Requirements](#2-requirements)
3. [Installing](#3-installing)
4. [First run — what you should see](#4-first-run--what-you-should-see)
5. [Configuration](#5-configuration)
6. [Commands](#6-commands)
7. [Permissions (LuckPerms)](#7-permissions-luckperms)
8. [Integrations](#8-integrations)
9. [Day-to-day operation](#9-day-to-day-operation)
10. [Upgrading to a new version](#10-upgrading-to-a-new-version)
11. [Modifying and releasing your own versions](#11-modifying-and-releasing-your-own-versions)
12. [Keeping this guide current](#12-keeping-this-guide-current)
13. [Troubleshooting](#13-troubleshooting)

---

## 1. What this mod is (and isn't)

Cobbleverse Server Core is a **server-side foundation**. On its own it does not add crates, shops,
cosmetics, seasons, or Pokémon rewards. It provides the plumbing — configuration, permissions,
commands, integration detection, messaging, health checks, auditing — that *future* feature modules
build on.

If you installed it expecting gameplay features, that's expected: the visible surface so far is the
`/cvcore`, `/profile`, `/rewards`, `/season`, and `/event` commands, a startup report in the log, a
SQLite database tracking player identity and playtime, a config-driven reward + currency system,
seasons with objectives and points milestones, and events with participation and leaderboards.
Event-driven objective tracking (Cobblemon) and auto-scheduling arrive as later versions.

---

## 2. Requirements

| Requirement    | Version                                    |
|----------------|--------------------------------------------|
| Minecraft      | 1.21.1                                      |
| Loader         | Fabric Loader ≥ 0.16.5                       |
| Java           | 21 or newer                                 |
| Fabric API     | Required (matching 1.21.1)                   |
| Side           | **Server** (this is a server-only mod)      |

`fabric-permissions-api` is **bundled inside the jar** (jar-in-jar) — you do not install it
separately. LuckPerms is optional but strongly recommended (see [Permissions](#7-permissions-luckperms)).

The exact versions this build targets are pinned in `gradle.properties`.

---

## 3. Installing

1. Obtain `cobbleverse-server-core-<version>.jar` (from a release, or build it — see
   [§11](#11-modifying-and-releasing-your-own-versions)).
2. Ensure **Fabric API** is in your server's `mods/` folder.
3. Drop the core jar into `mods/`.
4. Start the server.

That's it. Configuration files are created automatically on first run.

---

## 4. First run — what you should see

On boot the core prints a startup report to the server log (logger `CobbleverseCore/CORE`):

```
[CobbleverseCore] Initializing Cobbleverse Server Core...
[CobbleverseCore] Connected: SQLite (.../config/cobbleverse-server-core/data/core.db)
[CobbleverseCore] Schema up to date (version 1)
[CobbleverseCore] Version 0.2.0
[CobbleverseCore] Storage: SQLite (.../data/core.db)
[CobbleverseCore] Active season: <none>
[CobbleverseCore] LuckPerms: available (5.4.x)
[CobbleverseCore] Cobblemon: available (1.6.x)
[CobbleverseCore] Ledger: unavailable
[CobbleverseCore] SkiesCrates: available (...)
[CobbleverseCore] CobbleDollars: available (...)
[CobbleverseCore] HoloDisplays: available (...)
[CobbleverseCore] PlaceholderAPI: available (...)
[CobbleverseCore] API server: disabled
[CobbleverseCore] Loaded successfully
```

`unavailable` next to an integration is **normal** — it just means that mod isn't installed. It is
not an error.

If startup **aborts** with a `CV-CONFIG-xxx` error, your config is malformed — see
[Troubleshooting](#13-troubleshooting).

---

## 5. Configuration

All files live under:

```
<server>/config/cobbleverse-server-core/
```

Files created automatically:

### `core.json`

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

| Field            | Meaning                                                              |
|------------------|---------------------------------------------------------------------|
| `configVersion`  | Schema version. **Do not edit by hand** — the mod manages it.       |
| `debug`          | Extra diagnostic detail in `/cvcore debug`.                         |
| `serverId`       | A short id for this server (used in reports; useful once multiple servers exist). |
| `environment`    | Free-form label, e.g. `production` / `staging`.                     |
| `defaultLocale`  | Default message locale.                                             |
| `timezone`       | IANA zone id (e.g. `America/New_York`). Validated at startup.       |
| `activeSeason`   | Reserved for the season system (0.4.0). Leave blank for now.        |
| `enableAuditLog` | Record admin/core actions to the audit log.                        |
| `enableMetrics`  | Reserved for future metrics.                                        |

### `messages.json`

Holds the message prefix and templates. Text uses a **MiniMessage subset** (named colours, `<#rrggbb>`
hex, `<bold>`/`<italic>`/etc., and two-stop `<gradient:#a:#b>…</gradient>`), plus `<placeholder>`
tokens substituted at send time.

### `database.json` (0.2.0)

```json
{
  "configVersion": 1,
  "type": "sqlite",
  "fileName": "data/core.db",
  "flushIntervalSeconds": 300,
  "playtimeAccrualSeconds": 60
}
```

Controls the SQLite database (default `config/cobbleverse-server-core/data/core.db`), how often
playtime is credited, and how often changed profiles are written back. **Not** runtime reloadable —
changing it needs a restart. Full details in [database.md](database.md).

### `rewards.json` (0.3.0)

Defines reward bundles, the currencies the core owns, and command templates for reward types backed
by other mods. Created with a sample definition on first run:

```json
{
  "configVersion": 1,
  "internalCurrencies": ["event_tokens", "battle_points", "cosmetic_shards"],
  "templates": {
    "crateKey": "",
    "permission": "lp user {uuid} permission set {node} true",
    "pokemon": "",
    "cosmetic": "",
    "cobbledollarsDeposit": "",
    "cobbledollarsWithdraw": ""
  },
  "definitions": {
    "sample_tier_1": {
      "displayName": "Sample Tier 1 Reward",
      "repeatable": false,
      "rewards": [
        { "type": "item", "item": "minecraft:diamond", "amount": 3 },
        { "type": "currency", "currency": "event_tokens", "amount": 500 }
      ]
    }
  }
}
```

Runtime-reloadable with `/cvcore reload`. Full details (all reward types, template placeholders,
offline queue behaviour) in [rewards.md](rewards.md).

### `seasons.json` (0.4.0)

Defines seasons, their objectives, and points milestones. Which season is current is set by
`core.json`'s `activeSeason`; a season is created disabled by default on first run.

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
        { "id": "catch_water_25", "type": "manual", "required": 25, "points": 20 }
      ],
      "milestones": [ { "points": 20, "reward": "summer_2026_tier_1" } ]
    }
  }
}
```

Runtime-reloadable with `/cvcore reload`. Full details in [seasons.md](seasons.md).

### `events.json` (0.5.0)

Defines events (lifecycle-managed activities players join; completion grants the listed rewards to
every participant). A sample event is created on first run.

```json
{
  "configVersion": 1,
  "events": {
    "summer_catchathon": {
      "displayName": "Summer Catch-a-thon", "type": "catching",
      "rewards": [ "summer_2026_tier_1" ]
    }
  }
}
```

Runtime-reloadable with `/cvcore reload`. Full details in [events.md](events.md).

### Configuration rules (important)

- **A broken config is never silently replaced.** If a file has invalid JSON, the mod backs it up as
  `<file>.broken` and **aborts startup** with a clear `CV-CONFIG-xxx` message. Fix the file (or delete
  it to regenerate defaults) and restart.
- **Invalid values fail loudly.** e.g. a bad `timezone` or blank `serverId` stops startup with the
  reason.
- Only `core.json` and `messages.json` are reloadable at runtime (see below). Everything structural
  requires a restart.

---

## 6. Commands

Root command: `/cvcore`. With no argument it runs `info`.

| Command                | Permission                   | Op fallback | What it does                              |
|------------------------|------------------------------|-------------|-------------------------------------------|
| `/cvcore info`         | `cobbleverse.command.cvcore` | 2           | Version, server id, storage, season, integration count |
| `/cvcore health`       | `cobbleverse.command.cvcore` | 2           | Runs all health checks                    |
| `/cvcore integrations` | `cobbleverse.command.cvcore` | 2           | Lists each integration and its status     |
| `/cvcore reload`       | `cobbleverse.admin.reload`   | 4           | Reloads **safe** config + re-detects integrations |
| `/cvcore debug`        | `cobbleverse.admin.debug`    | 4           | Extended diagnostics + game-event bus stats |
| `/cvcore debug events on\|off` | `cobbleverse.admin.debug` | 4       | Log every game event (capture, join, ...) |
| `/cvcore debug publish capture <player> <species> [shiny]` | `cobbleverse.admin.debug` | 4 | Inject a synthetic capture (test the pipeline) |
| `/cvcore database status` | `cobbleverse.admin.database` | 4        | DB connection, schema version, profile/audit counts |
| `/cvcore player create <name>` | `cobbleverse.admin.player` | 4      | Pre-create a profile for a player who hasn't joined |
| `/cvcore reward list`  | `cobbleverse.admin.rewards`   | 4          | List configured reward definitions        |
| `/cvcore reward grant <player> <id>` | `cobbleverse.admin.rewards` | 4 | Grant a reward (queues if player offline) |
| `/cvcore reward retry <player> [id]` | `cobbleverse.admin.rewards` | 4 | Revive dead-lettered rewards and re-deliver |
| `/cvcore reward queue <player>` | `cobbleverse.admin.rewards` | 4      | Inspect a player's reward queue (status, attempts) |
| `/profile`             | `cobbleverse.command.profile` | all        | Your own profile (UUID, joins, playtime)  |
| `/profile <player>`    | `cobbleverse.profile.view.other` | 2       | Another player's profile (online or offline by name) |
| `/rewards`             | `cobbleverse.command.rewards` | all        | List rewards and their claim state        |
| `/rewards claim <id>`  | `cobbleverse.reward.claim`    | all        | Claim a reward for yourself                |
| `/rewards preview <id>` | `cobbleverse.reward.preview` | all        | See what a reward would grant (dry run)   |
| `/season`              | `cobbleverse.command.season`  | all        | Active season name, state, your points    |
| `/season progress`     | `cobbleverse.season.progress` | all        | Your objectives and next milestone        |
| `/cvcore season info`  | `cobbleverse.admin.season`    | 4          | Active season details + lifecycle state   |
| `/cvcore season progress <player>` | `cobbleverse.admin.season` | 4  | View a player's season progress           |
| `/cvcore season addpoints <player> <amount>` | `cobbleverse.admin.season` | 4 | Adjust a player's points (may be negative) |
| `/cvcore season objective <player> <objective> <amount>` | `cobbleverse.admin.season` | 4 | Add objective progress |
| `/cvcore season top [n]` | `cobbleverse.admin.season`   | 4          | Season points leaderboard                 |
| `/cvcore event list`   | `cobbleverse.admin.events`    | 4          | List events + state + participant count   |
| `/cvcore event open\|start\|complete\|cancel\|schedule <id>` | `cobbleverse.admin.events` | 4 | Drive event lifecycle |
| `/cvcore event addplayer <id> <player>` | `cobbleverse.admin.events` | 4 | Add a participant from console        |
| `/cvcore event score <id> <player> <amount>` | `cobbleverse.admin.events` | 4 | Adjust a participant's score      |
| `/cvcore event rewards abandon <id>` | `cobbleverse.admin.events` | 4 | Drop a stuck pending reward distribution |
| `/events`              | `cobbleverse.command.events`  | all        | List events and their state               |
| `/event info\|join\|leave\|leaderboard <id>` | `cobbleverse.command.events` (join/leave gated) | all | Event info / join / leave / leaderboard |
| `/season leaderboard`  | `cobbleverse.command.season`  | all        | Season points leaderboard                 |

### What `/cvcore reload` does — and does not — do

**Reloads:** `core.json`, `messages.json`, and re-runs integration detection. If the new config is
invalid, the **previous** config stays active and the command reports the first problem (nothing
breaks).

**Does NOT touch:** registries, Pokémon species, dimensions, datapacks, the database driver, or
mixins. Changes to those always require a **full server restart**. When in doubt, restart.

---

## 7. Permissions (LuckPerms)

Every permission check runs through `fabric-permissions-api`, which LuckPerms implements. All nodes
live under the `cobbleverse.` namespace, so you can grant everything with one wildcard.

Nodes:

```
cobbleverse.command.cvcore     # /cvcore info | health | integrations   (op-2 fallback)
cobbleverse.admin.reload       # /cvcore reload                          (op-4 fallback)
cobbleverse.admin.debug        # /cvcore debug                           (op-4 fallback)
cobbleverse.admin.database     # /cvcore database status                 (op-4 fallback)
cobbleverse.admin.player       # /cvcore player create <name>            (op-4 fallback)
cobbleverse.admin.rewards      # /cvcore reward list | grant             (op-4 fallback)
cobbleverse.admin.season       # /cvcore season ...                      (op-4 fallback)
cobbleverse.command.rewards    # /rewards                                (available to all)
cobbleverse.reward.claim       # /rewards claim <id>                     (available to all)
cobbleverse.reward.preview     # /rewards preview <id>                   (available to all)
cobbleverse.admin.events       # /cvcore event ...                      (op-4 fallback)
cobbleverse.command.season     # /season                                (available to all)
cobbleverse.season.progress    # /season progress                       (available to all)
cobbleverse.command.events     # /events, /event info | leaderboard     (available to all)
cobbleverse.event.join         # /event join <id>                       (available to all)
cobbleverse.event.leave        # /event leave <id>                      (available to all)
cobbleverse.command.profile    # /profile (own)                          (available to all)
cobbleverse.profile.view.other # /profile <player>                       (op-2 fallback)
```

Example LuckPerms setup for a staff group:

```
/lp group admin permission set cobbleverse.command.cvcore true
/lp group admin permission set cobbleverse.admin.reload true
/lp group admin permission set cobbleverse.admin.debug true
```

**No permission provider installed?** The **op-level fallback** still applies — a server operator at
the listed level keeps access. This means admin commands work out of the box for ops even before you
configure LuckPerms.

---

## 8. Integrations

The core detects these mods at runtime from the Fabric mod list — it does **not** bundle or depend on
them, so a missing one is harmless:

| Integration    | Fabric mod id     |
|----------------|-------------------|
| LuckPerms      | `luckperms`       |
| Cobblemon      | `cobblemon`       |
| Ledger         | `ledger`          |
| SkiesCrates    | `skiescrates`     |
| CobbleDollars  | `cobbledollars`   |
| HoloDisplays   | `holodisplays`    |
| PlaceholderAPI | `placeholder-api` |

Check status any time with `/cvcore integrations`.

> **If a mod you have installed shows as `unavailable`:** its actual Fabric mod id may differ from the
> one above. Confirm the real id (open the mod jar's `fabric.mod.json`, read the `id` field) and, if
> it differs, the id in that integration class needs updating (see [§11](#11-modifying-and-releasing-your-own-versions)).
> This is the most likely thing to need adjusting for your specific mod set.

---

## 9. Day-to-day operation

- **Health:** `/cvcore health` gives an at-a-glance OK / WARN / ERROR per subsystem. `WARN` on
  Permissions just means no LuckPerms (fallback in use); that's fine on a small server.
- **After editing `core.json` / `messages.json`:** run `/cvcore reload`. For any other change, restart.
- **Auditing:** admin actions (e.g. reloads) are logged to the `CobbleverseCore/AUDIT` logger and, as
  of `0.2.0`, persisted to the `audit_log` table.
- **Player data:** identity and playtime are stored in SQLite and survive restarts. Playtime accrues
  every `playtimeAccrualSeconds` and is flushed every `flushIntervalSeconds` (see `database.json`);
  it's also flushed on player leave and on server shutdown.

### Pre-creating a profile before a player joins

Normally a profile is created automatically the first time a player joins. To create one ahead of
time (for example to seed data for a player who hasn't joined yet), use:

```
/cvcore player create <name>
```

This resolves the UUID the **same way the server would** — deterministically in offline-mode, or via
a Mojang lookup in online-mode — so when the player later joins, their existing profile is matched
and updated rather than duplicated. It never overwrites an existing profile, and the action is
audited. This is the supported alternative to editing the SQLite database by hand.

### Rewards and currencies

Rewards are defined in `rewards.json` and granted through one central service that validates, dedups,
audits, and (for offline players) queues them.

- **Grant a reward:** `/cvcore reward grant <player> <id>`. If the player is offline it's queued and
  delivered on their next join.
- **Players claim their own:** `/rewards`, `/rewards claim <id>`, `/rewards preview <id>`.
- **Claim once:** definitions are non-repeatable by default — a player can claim each only once
  (enforced in the database). Set `"repeatable": true` to allow repeat claims.
- **Reward types:** `item` and `command` work out of the box. `currency` uses the internal
  currencies you list in `rewards.json` (stored by the core) or CobbleDollars. `crate_key`,
  `permission`, `pokemon`, and `cosmetic` run a **command template** you configure under `templates`
  — a blank template means that type reports "unsupported" instead of silently doing nothing, so fill
  in the ones you use (e.g. a SkiesCrates give-key command for `crateKey`).
- **Currencies:** internal currencies (e.g. `event_tokens`) are stored and audited by the core.
  CobbleDollars is driven through the `cobbledollarsDeposit` / `cobbledollarsWithdraw` templates when
  the mod is present.
- **Recovery (0.3.1):** if part of a reward fails (say an integration is briefly down), that reward
  becomes `PARTIAL` and retrying re-runs only the missing parts — already-granted items/currency are
  never duplicated. Queued deliveries that keep failing dead-letter after `maxDeliveryAttempts`
  (default 5) rather than vanishing. Inspect with `/cvcore reward queue <player>` and recover with
  `/cvcore reward retry <player> [id]`.

See [rewards.md](rewards.md) for the full reference.

### Seasons

A season (`seasons.json`, active one named by `core.json` `activeSeason`) is a set of objectives that
award points; reaching a points milestone grants a reward.

- **0.4.0 objectives are manual** — progress is set by admins (`/cvcore season objective …`) or other
  modules, not auto-tracked yet. Cobblemon capture/battle tracking comes later.
- Completing an objective awards its points; crossing a milestone grants that milestone's reward
  through the reward system (so it dedups and queues if the player is offline).
- A season's state (upcoming / active / ended) is derived from its window; transitions are detected
  once a minute and on startup.
- Check with `/season`, `/season progress`, `/season leaderboard`, and `/cvcore season info`.

See [seasons.md](seasons.md) for the full reference.

### Events

An event (`events.json`) is a lifecycle-managed activity players join; on completion, every
participant receives the event's rewards.

- **0.5.0 lifecycle is admin-driven** (`/cvcore event open|start|complete|cancel`); auto-scheduling
  from configured times comes later. The state machine rejects illegal transitions.
- Players join with `/event join <id>` (OPEN/ACTIVE); admins can add participants from console
  (`/cvcore event addplayer`). Scores drive `/event leaderboard <id>`.
- **Completing** an event grants each participant the event's rewards through the reward system — so
  they dedup and queue for offline players.
- **If distribution can't finish** (a grant fails, or an event's definition was removed) it stays
  **pending** and retries on startup rather than losing rewards — surfaced by a `CV-EVENT` error in
  the log. Drop a genuinely stuck one with `/cvcore event rewards abandon <id>`.
- **Config integrity:** season milestone rewards and event completion rewards must point at an
  existing, **non-repeatable** reward definition — the server refuses to start (or rejects a reload)
  otherwise, with a clear message. This is what makes crash-recovery safe.

See [events.md](events.md) for the full reference and a console test flow.

### Game events (0.6.0)

Game-world actions are published to a central **event bus** that subsystems subscribe to. In 0.6.0 the
bus, the event contract, and **player join/leave events** are live; the first real consumers
(objective handlers, statistics) arrive in 0.6.1.

- **Cobblemon capture/battle events are not yet firing from gameplay** — the adapter is detected and
  scaffolded, but its subscription must be compiled against a specific Cobblemon version (a follow-up).
- **Test the pipeline now** without Cobblemon: `/cvcore debug events on`, then
  `/cvcore debug publish capture <player> <species> [shiny]` — you'll see the event logged with its
  attached listeners.

See [game-events.md](game-events.md) for the full reference.
- **Logs:** each subsystem logs under `CobbleverseCore/<AREA>` (CORE, CONFIG, INTEGRATION, AUDIT).
  Ordinary player activity is not logged at INFO by design.

---

## 10. Upgrading to a new version

Follow this whenever you drop in a newer `cobbleverse-server-core` jar.

### Before you upgrade
1. **Read the [CHANGELOG](../CHANGELOG.md)** for the target version — especially any **breaking
   changes** or **migration** notes.
2. **Check compatibility.** Confirm the new version targets your Minecraft version and Fabric API.
   A core built for a different Minecraft version will not load.
3. **Back up:**
   - the whole `config/cobbleverse-server-core/` folder, and
   - (from 0.2.0 onward) the core's **database file**, and
   - ideally the world, as with any mod change.

### Doing the upgrade
4. Stop the server.
5. Remove the **old** core jar from `mods/` and add the **new** one. (Don't leave two versions in
   `mods/` — that causes load conflicts.)
6. Update any dependency mods the CHANGELOG calls for (e.g. a newer Fabric API).
7. Start the server and **watch the log**.

### What happens automatically
- **Config migrations:** if a config's `configVersion` is older than the new build expects, the core
  migrates it — taking a `<file>.bak` backup first. You do not edit `configVersion` yourself.
- **Database migrations (0.2.0+):** schema upgrades run automatically and in order, with a backup taken
  first.

### Verify after upgrade
8. `/cvcore info` — confirm the new version number.
9. `/cvcore health` — everything OK.
10. `/cvcore integrations` — all your mods still detected.

### Rolling back
If something's wrong: stop the server, swap the **old** jar back in, and **restore the config (and DB)
backups you took in step 3**. This is why the pre-upgrade backup is not optional — a newer config or
schema version generally cannot be read by an older build.

> **Never skip across a documented breaking change without reading its notes.** If you jump several
> versions at once (e.g. 0.1.0 → 0.5.0), read every intervening CHANGELOG entry, not just the latest.

---

## 11. Modifying and releasing your own versions

This is your project, so you'll be changing the code and cutting your own builds. Here's the workflow.

### Build from source
```bash
./gradlew build
```
Output: `build/libs/cobbleverse-server-core-<version>.jar`. Deploy that to your server's `mods/`.
`./gradlew build` also runs the tests and Checkstyle linting.

Run them on their own with:
```bash
./gradlew test                          # tests only
./gradlew checkstyleMain checkstyleTest # lint only
```
CI (`.github/workflows/ci.yml`) runs lint + build + tests on every push and pull request.

### Where version numbers live
Versioning uses [Semantic Versioning](https://semver.org/): `MAJOR.MINOR.PATCH`.

- **PATCH** (0.1.0 → 0.1.1): bug fixes, no behaviour or config changes.
- **MINOR** (0.1.0 → 0.2.0): new features, backwards-compatible.
- **MAJOR** (0.x → 1.0): stable API; after 1.0, a MAJOR bump signals breaking changes.

The single source of truth is `gradle.properties`:

```properties
mod_version=0.1.0        # <-- bump this for a release
minecraft_version=1.21.1 # bump when targeting a new Minecraft
loader_version=0.16.5
fabric_version=0.115.0+1.21.1
```

`fabric.mod.json` reads `${version}` from here at build time, so you only change it in one place.

### Releasing a new version — checklist
1. Make your code changes on a branch.
2. If you changed a config's shape, **bump its `configVersion`** and add a migration (from 0.2.0 the
   migration framework exists; until then, additive fields with defaults are backwards-compatible and
   need no migration).
3. Update the **[CHANGELOG](../CHANGELOG.md)**: move items into a new version section, and call out any
   breaking changes or migration steps prominently.
4. Update **this guide** and `docs/` if behaviour, commands, config, or permissions changed
   (see [§12](#12-keeping-this-guide-current)).
5. Bump `mod_version` in `gradle.properties`.
6. `./gradlew build` and confirm `BUILD SUCCESSFUL` + tests pass.
7. Commit, then **tag**:
   ```bash
   git commit -am "Release 0.2.0"
   git tag -a v0.2.0 -m "0.2.0 — Player profiles + persistence"
   git push && git push --tags
   ```
8. Attach the built jar from `build/libs/` to a GitHub release (optional but recommended, so you can
   redownload exact past builds).

### Changing an integration's mod id
If one of your installed mods uses a different Fabric id than the core expects, edit the matching
class under `src/main/java/.../integration/<mod>/` — the id is the third argument to `super(...)`:

```java
public CobbleDollarsIntegration() {
    super("cobbledollars", "CobbleDollars", "cobbledollars"); // <-- last arg is the Fabric mod id
}
```

Rebuild and redeploy. Confirm with `/cvcore integrations`.

### Bumping the Minecraft version
Changing `minecraft_version` (and matching `yarn_mappings` / `fabric_version`) is a real port, not a
config tweak: Minecraft/Yarn APIs change between versions and code may need updating. Do it on a
branch, get `./gradlew build` green, test on a throwaway server, then release as at least a MINOR bump
and note the new Minecraft version at the top of the CHANGELOG entry.

---

## 12. Keeping this guide current

Treat docs as part of the change, not an afterthought. In the **same commit** that changes behaviour,
update whichever of these applies:

| If you change…                        | Update…                                             |
|---------------------------------------|-----------------------------------------------------|
| A command or its permission           | [§6](#6-commands), [§7](#7-permissions-luckperms), `docs/commands.md`, `docs/permissions.md` |
| A config field or file                | [§5](#5-configuration), `docs/configuration.md`     |
| A reward type, currency, or template   | [§9](#9-day-to-day-operation), `docs/rewards.md`    |
| A season, objective, or milestone      | [§9](#9-day-to-day-operation), `docs/seasons.md`    |
| An event, its lifecycle, or scoring    | [§9](#9-day-to-day-operation), `docs/events.md`     |
| A game event type or the bus           | [§9](#9-day-to-day-operation), `docs/game-events.md`|
| An integration (added/removed/mod id) | [§8](#8-integrations), `docs/integrations.md`       |
| Startup behaviour or the report       | [§4](#4-first-run--what-you-should-see), `docs/architecture.md` |
| Anything user-visible                 | `CHANGELOG.md`                                      |
| The version                           | `gradle.properties` (`mod_version`)                 |

A quick self-check before you tag a release: *"If I read only the README, CHANGELOG, and this guide,
would they match what the mod actually does now?"* If not, fix the docs first.

---

## 13. Troubleshooting

| Symptom | Cause / fix |
|---------|-------------|
| Startup aborts with `CV-CONFIG-003` | Malformed JSON in a config. The bad file was saved as `<file>.broken`. Fix the JSON (or delete the file to regenerate defaults) and restart. |
| Startup aborts with `CV-CONFIG-010` | A config value failed validation (e.g. bad timezone, blank serverId). The message lists the exact problem. |
| `Missing required dependencies: fabric-api` | Fabric API isn't installed. Add it to `mods/`. |
| Mod doesn't load at all | Wrong Minecraft version, or Java below 21. Check the requirements in [§2](#2-requirements). |
| An installed integration shows `unavailable` | Its real Fabric mod id differs from the expected one — see [§8](#8-integrations). |
| Admin command says no permission | Grant the node in LuckPerms, or run as an operator at the fallback level ([§7](#7-permissions-luckperms)). |
| Config edit didn't take effect | Only `core.json` / `messages.json` reload via `/cvcore reload`; everything else needs a restart. |
| Two versions of the core in `mods/` | Remove the old jar — only one may be present. |

For anything else, `/cvcore health` and `/cvcore debug` are the first things to check, followed by the
`CobbleverseCore/*` lines in the server log.
