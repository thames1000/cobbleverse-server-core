# Permissions

Checks run through `fabric-permissions-api`, so LuckPerms (or any compatible provider) can manage
every node. All nodes live under the `cobbleverse.` namespace, so a single wildcard
`cobbleverse.*` grants everything.

Every check carries a fallback operator level: if no permission provider is installed, the fallback
decides access, preserving emergency operator control.

```java
PermissionService perms = CoreServices.permissions();
perms.check(source, CorePermissions.ADMIN_RELOAD, CoreConstants.ADMIN_FALLBACK_LEVEL); // op 4
```

## Nodes in 0.1.0

| Node                          | Fallback | Grants                         |
|-------------------------------|----------|--------------------------------|
| `cobbleverse.command.cvcore`  | op 2     | `/cvcore info \| health \| integrations` |
| `cobbleverse.admin.reload`    | op 4     | `/cvcore reload`               |
| `cobbleverse.admin.debug`     | op 4     | `/cvcore debug`                |

## Reserved namespace

The full plan reserves these node groups for later versions (not yet wired):

```
cobbleverse.command.{profile,season,events,rewards}
cobbleverse.admin.{player,season,events,rewards,database,integrations}
cobbleverse.event.{join,leave,host}
cobbleverse.season.{view,progress}
cobbleverse.reward.{claim,preview}
cobbleverse.profile.{view,view.other}
```

Feature modules should reference constants in `CorePermissions` rather than raw strings so nodes stay
consistent.
