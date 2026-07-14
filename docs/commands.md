# Commands

All commands live under `/cvcore`. The root requires `cobbleverse.command.cvcore` (operator level 2
fallback) and, with no argument, runs `info`.

| Command                | Permission                   | Fallback | Description                        |
|------------------------|------------------------------|----------|------------------------------------|
| `/cvcore`              | `cobbleverse.command.cvcore` | op 2     | Alias for `info`                   |
| `/cvcore info`         | `cobbleverse.command.cvcore` | op 2     | Version, server id, storage, season, integration count |
| `/cvcore health`       | `cobbleverse.command.cvcore` | op 2     | Runs all health checks; return code 0 if any ERROR |
| `/cvcore integrations` | `cobbleverse.command.cvcore` | op 2     | Lists every integration and its status |
| `/cvcore reload`       | `cobbleverse.admin.reload`   | op 4     | Reloads safe config + re-detects integrations |
| `/cvcore debug`        | `cobbleverse.admin.debug`    | op 4     | Extended diagnostics (config dir, audit buffer, env) |

## Reload scope

`/cvcore reload` re-reads only:

- `core.json`
- `messages.json`
- Integration detection

If the new config fails validation the previous config stays active and the command reports the
first problem. Reload **never** touches registries, Pokémon species, dimensions, datapacks, database
drivers or mixins.

## Design rules

Commands go through `MessageService` (no hard-coded strings), support tab completion via Brigadier,
avoid blocking work on the server thread, return useful errors, and audit administrative actions.
