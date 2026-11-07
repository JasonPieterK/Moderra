# Configuration reference

All options live in `plugins/AntiBadWords/config.yml`. Apply changes with `/abw reload`.
Messages accept `&` colour codes and these placeholders (any capitalisation):
`{player} {word} {source} {count} {limit} {remaining} {punishments}`.

## `filters`

| Key | Default | Meaning |
|-----|---------|---------|
| `enabled-word-lists` | all languages + custom | Which `word-lists.*` are active. `custom` is always on. |
| `protection-level` | `3` | `1` LOW = level-3 words only, `2` MEDIUM = levels 2-3, `3` HIGH = everything. |
| `scan.chat/commands/signs/books/anvil` | `true` | Turn a text source on or off. |
| `scanned-commands` | `msg, tell, w, whisper, r, reply, me, say, teammsg, tm, mail, nick, broadcast` | Commands whose arguments are checked. `"*"` = every command. |
| `ignored-commands` | `antibadwords, abw, antiwords` | Never checked. |
| `matching.collapse-repeated-letters` | `true` | `fuuuuck` is read as `fuck`. |
| `matching.repeated-letter-threshold` | `3` | Run length that counts as "repeated". |
| `matching.detect-spaced-letters` | `true` | Catches `f u c k` and `f.u.c.k`. |
| `matching.join-adjacent-words` | `false` | Also glue neighbouring words (`fu ck`). More false positives (`pen is`). |
| `matching.partial-match` | `false` | Block words that merely *contain* a bad word. Use with the whitelist. |
| `matching.partial-min-length` | `4` | Shortest bad word allowed in partial mode. |
| `whitelist` | common innocent words | Words that are never blocked. |

## `messages`

`broadcast-warnings`, `warning-broadcast-format`, `warning-player`, `punishment-broadcast-format`,
`punishment-player`, `punishment-disabled-player`, `notify-staff`, `staff-notify-format`.

## `logging`

`console: true` prints every blocked word to the server console.

## `punishment`

| Key | Default | Meaning |
|-----|---------|---------|
| `enabled` | `true` | Master switch. When off, players only get warnings. |
| `violations-before-punish` | `2` | Strikes before punishment. |
| `violations-expire-minutes` | `0` | Forget strikes after N minutes (`0` = never). |
| `clear-violations-on-quit` | `true` | Reset strikes when the player leaves. |
| `final-command` | disabled | Console command run after the actions (`{player}`, `{word}`, `{source}`). |

Each action has `enabled: true/false`. Enabled actions all run when a player is punished.

- **`effects`** - `effect-name`, `amplifier` (0 = level I), `duration` (seconds).
- **`attributes`** - `attribute-name`, `value`, `duration` (seconds). The attribute is set, then the
  original value is restored automatically (timer end, quit, kick, death/respawn, plugin disable;
  repaired on next join after a crash).
- **`sounds`** - `sound-name` (e.g. `entity.villager.no`), `volume`, `pitch`.
- **`commands`** - `command`, run from the console, `{player}` is replaced.

Old-style `format: "name-value-seconds"` entries still work.

## `word-lists`

Seven built-in lists (`english`, `indonesian`, `portuguese_brazil`, `russian`, `uk_english`, `german`,
`chinese`), each split into `level-1` (mild), `level-2` (rude), `level-3` (severe), plus `custom`
(managed with `/abw addword` / `removeword`). Entries may contain spaces, accents and any script.

## Permissions

| Permission | Default | Use |
|------------|---------|-----|
| `antibadwords.admin` | op | All `/abw` sub-commands |
| `antibadwords.notify` | op | Staff alert for every blocked word |
| `antibadwords.bypass` | nobody | Exempt from all checks |
