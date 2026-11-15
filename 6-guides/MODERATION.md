# Moderation guide: strikes, ladder, mute, history

*Verified: engine unit tests. The plugin and mod code was compiled on GitHub against the real APIs. It was NOT run on a real server in this round.*

## The idea in five sentences
Every time a player says a bad word they get a **strike** (a point). Strikes are written down per player, so they survive
a restart (if you turn "clear on quit" off). The **ladder** is a list of steps: "at 2 strikes warn, at 4 strikes mute for
10 minutes, at 6 strikes kick". A **mute** stops a player from talking (chat, chosen commands, signs, books) for a time.
The **history** is a diary of everything that happened, for staff to read.

## Where the settings are
| File | What it holds |
|---|---|
| `moderation/ladder.yml` | strikes (decay, cap, saving) and `ladder.steps` |
| `moderation/mute.yml` | mute messages, which commands a mute blocks, default length |
| `moderation/history.yml` | history on/off, retention, size, privacy |
| `data/strikes.json`, `data/mutes.json`, `data/players.json`, `data/history/*.jsonl` | state files; do not edit while the server runs |

Every option has a comment in its file and a row in `CONFIGURATION.md`.

## Commands and permissions
| Command | Permission (default) |
|---|---|
| `/abw strikes <player>`, `/abw ladder [profile]`, `/abw violations <player>`, `/abw resetviolations <player>` | `antibadwords.admin` (op) |
| `/abw mute <player> [10m\|2h\|1d\|perm] [reason]`, `/abw unmute <player>`, `/abw mutes [page]` | `antibadwords.mute` (op) |
| `/abw history <player> [page]`, `/abw history recent [n]` | `antibadwords.history` (op) |
| `/abw history purge <player> [confirm]` | `antibadwords.history` and `antibadwords.admin` |
| Never muted (also not by the ladder) | `antibadwords.bypass.mute` (nobody) |
| `/abw gui` and the in-game menu (mutes page, ladder editor) | `antibadwords.gui` (op); see [GUI.md](GUI.md) |

Durations need a unit: `30s`, `10m`, `1h30m`, `2d`, `perm`. Offline players work for all of these.

## Writing a ladder
```yaml
ladder:
  steps:
    first-warning:
      at: 2                 # strikes
      repeat: once          # or a number: again every N strikes
      message: "&eWatch your language, {player}."
      actions:
        - warn
```
Actions: `warn [text]`, `mute <time|perm> [reason]`, `kick [reason]`, `ban [reason]` (runs `ladder.ban-command`),
`tempban <time> [reason]` and `ipban [reason]` (use your ban plugin, see [INTEGRATIONS.md](INTEGRATIONS.md)),
`command <console command>`, `effect`, `attribute`, `sound`, `punishments` (the classic actions), `reset-strikes`.
Placeholders: `{player} {word} {count} {step} {threshold} {reason} {duration}`. Vanilla has no timed ban: use
the `tempban` action with a ban plugin (or `command tempban {player} 1d {reason}`). With no steps you get the old behaviour (one punishment at
`violations-before-punish`); `/abw ladder` shows it as the step `legacy`. With steps, strikes are NOT reset after a
step; they fade through `strikes.decay` or expiry.

### Family server (gentle, kids around)
```yaml
strikes: { decay: { every: 30m, points: 1 } }
ladder:
  steps:
    reminder: { at: 1, repeat: once, message: "&eLet's keep chat friendly.", actions: [warn] }
    cool-down: { at: 3, repeat: 3, actions: ["mute 5m Cool down"] }
```
### Normal server
```yaml
strikes: { decay: { every: 1h, points: 1 } }
ladder:
  steps:
    warn: { at: 2, repeat: once, actions: [warn] }
    mute: { at: 4, repeat: 2, actions: ["mute 10m Bad language"] }
    kick: { at: 8, repeat: once, actions: ["kick Too much bad language"] }
```
### Strict server
```yaml
strikes: { decay: { every: 0s } }      # strikes never fade by themselves
ladder:
  steps:
    mute: { at: 2, repeat: once, actions: ["mute 30m Bad language"] }
    long-mute: { at: 3, repeat: once, actions: ["mute 1d Repeated bad language"] }
    ban: { at: 5, repeat: once, actions: ["command tempban {player} 7d {reason}"] }
```
Strikes are only kept across restarts when `punishment.clear-violations-on-quit: false` (the old default is `true`).
If strikes are NOT kept (clear on quit is true, or saving is off) and an old `data/strikes.json` exists, it is renamed to
`strikes.json.discarded` at start and at shutdown, so old strikes never come back by surprise. Nothing is deleted.

## More ways to moderate
* **From Discord:** `!abw mute`, `unmute`, `history`, `strikes` with allow-lists: [DISCORD.md](DISCORD.md).
* **EssentialsX mute:** `integrations.essentials.mute-mode` (`own`, `essentials`, `both`).
* **Several servers:** mutes and strikes can be shared, see "Network sync" in [INTEGRATIONS.md](INTEGRATIONS.md).
* **Placeholders** for scoreboards (`%antibadwords_strikes%`, `%antibadwords_muted%`): PlaceholderAPI section there.
* **Message look:** [MESSAGE-STYLE.md](MESSAGE-STYLE.md).

## Privacy
History stores who, when, what kind and the step, not the chat text (`history.store-message-text: false`). If you turn
text on, `history.redact-words` masks the bad word. Old months are deleted after `history.retention-days` (90).
Only `antibadwords.history` can read it; `purge` also needs admin and a confirm.

## Limits
Verified: engine unit tests. The wiring (permissions, join/quit, kick) was compiled on GitHub against the real APIs but never run
on a server; see `handoffs/BUILD-VERIFICATION-TODO.md`. On Fabric/Forge a kick uses the vanilla `/kick` command and `tempban` uses vanilla.
