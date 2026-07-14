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
`/cvcore` and `/profile` commands, a startup report in the log, and a SQLite database tracking player
identity and playtime. Gameplay (rewards, seasons, events) arrives as later versions and separate
modules.

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
| `/cvcore debug`        | `cobbleverse.admin.debug`    | 4           | Extended diagnostics                      |
| `/cvcore database status` | `cobbleverse.admin.database` | 4        | DB connection, schema version, profile/audit counts |
| `/profile`             | `cobbleverse.command.profile` | all        | Your own profile (UUID, joins, playtime)  |
| `/profile <player>`    | `cobbleverse.profile.view.other` | 2       | Another player's profile (online or offline by name) |

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

Run the tests on their own with:
```bash
./gradlew test
```

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
