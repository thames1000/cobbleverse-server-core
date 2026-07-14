# Rewards & Currencies

Implemented in **0.3.0**. Every reward — whether it's an item, currency, crate key, or a Pokémon —
flows through one central `RewardService` that validates, executes, records the claim, audits, and
(for offline players) queues it.

## Defining rewards (`rewards.json`)

```json
{
  "configVersion": 1,
  "internalCurrencies": ["event_tokens", "battle_points", "cosmetic_shards"],
  "templates": {
    "crateKey": "crates key give {player} {key} {amount}",
    "permission": "lp user {uuid} permission set {node} true",
    "pokemon": "pokegive {player} {value}",
    "cosmetic": "",
    "cobbledollarsDeposit": "cobbledollars add {player} {amount}",
    "cobbledollarsWithdraw": "cobbledollars remove {player} {amount}"
  },
  "definitions": {
    "summer_2026_tier_1": {
      "displayName": "Summer Tier 1 Reward",
      "repeatable": false,
      "rewards": [
        { "type": "item", "item": "cobblemon:rare_candy", "amount": 5 },
        { "type": "currency", "currency": "event_tokens", "amount": 2500 },
        { "type": "crate_key", "key": "summer_2026", "amount": 1 },
        { "type": "command", "command": "title {player} title {\"text\":\"Nice!\"}" }
      ]
    }
  }
}
```

`rewards.json` is **runtime-reloadable** with `/cvcore reload`.

## Reward types

| Type         | Required fields | How it's delivered                                   |
|--------------|-----------------|------------------------------------------------------|
| `item`       | `item`, `amount`| Native — added to inventory (overflow drops), split into stacks. The item id must resolve (its mod must be installed). |
| `command`    | `command`       | Native — run as the server console.                  |
| `currency`   | `currency`, `amount` | Deposited via the currency provider for that id. |
| `crate_key`  | `key`, `amount` | Command template `templates.crateKey`.               |
| `permission` | `node`          | Command template `templates.permission`.             |
| `pokemon`    | `value`         | Command template `templates.pokemon`.                |
| `cosmetic`   | `value`         | Command template `templates.cosmetic`.               |

**Template placeholders:** `{player}` (name), `{uuid}`, `{amount}`, and the type's own field
(`{key}` / `{node}` / `{value}`). A **blank template** means that type is unsupported on this server —
the reward reports "unsupported" rather than silently doing nothing, so configure the templates for
the mods you actually run.

## Claiming, granting, and previewing

- **Players:** `/rewards` (list + state), `/rewards claim <id>`, `/rewards preview <id>`.
- **Admins:** `/cvcore reward list`, `/cvcore reward grant <player> <id>`.

### Claim-once and repeatability

Definitions are **non-repeatable by default**: each player may claim one only once, enforced by the
`reward_claims` table primary key. A non-repeatable reward **reserves its claim before executing**, so
it can never be granted twice — even under concurrency or if execution partially fails. Set
`"repeatable": true` to allow repeat claims (no claim row is kept for repeatable rewards).

### Offline queue

Granting to an offline player queues the whole definition (`reward_queue`) and delivers it on their
next join, with a "Delivered N pending reward(s)" message. Queued non-repeatable rewards still dedup
on delivery.

### Results

Every grant returns a status: `SUCCESS`, `PARTIAL` (some entries failed), `FAILED`, `ALREADY_CLAIMED`,
`QUEUED`, `UNKNOWN` (no such definition), or `UNSUPPORTED` (no template configured). Command output
shows the per-entry detail.

## Currencies

Currencies are abstracted behind `CurrencyProvider`; all movement is routed and **audited** by
`CurrencyService` (`CURRENCY_DEPOSIT` / `CURRENCY_WITHDRAW`).

| Provider                   | Backing                                             |
|----------------------------|-----------------------------------------------------|
| `InternalCurrencyProvider` | Core database — exact decimal balances + a transaction ledger. One per id in `internalCurrencies`. |
| `CommandCurrencyProvider`  | CobbleDollars, via the `cobbledollars*` command templates (registered only when the mod is present). Balance reads unsupported. |

Do not turn currencies into physical items — they're player-attached balances.

## Storage (migration V002)

```
reward_claims          (uuid, definition_id, claimed_at, source)   PRIMARY KEY(uuid, definition_id)
reward_queue           (id, uuid, definition_id, queued_at, source)
currency_balances      (uuid, currency, balance)                   PRIMARY KEY(uuid, currency)
currency_transactions  (id, uuid, currency, amount, type, timestamp, reason)
```

## Auditing

Reward grants and currency movements are recorded (`REWARD_GRANTED`, `CURRENCY_DEPOSIT`,
`CURRENCY_WITHDRAW`) with actor, target, and outcome — visible in the `AUDIT` log and, from 0.2.0, the
`audit_log` table.
