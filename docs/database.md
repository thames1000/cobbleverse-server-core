# Database

> **Planned — 0.2.0.** Not implemented in 0.1.0, which keeps all state in memory.

The persistence layer starts with SQLite and later supports PostgreSQL / MariaDB for larger or
multi-server setups.

## Planned rules

- Prepared statements everywhere.
- Never run large queries on the server thread.
- Transactions for reward claims; unique reward claims enforced in the schema.
- Automatic, versioned migrations (`V001…`), with a backup taken before each migration.
- No plaintext API secrets stored in the database.

## Planned tables

```
players                 player_profiles        seasons
season_progress         objectives             objective_progress
events                  event_participation    currencies
currency_transactions   reward_definitions     reward_claims
audit_log               web_links
```
