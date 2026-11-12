# Configuration reference

> This page is **generated** from the settings registry in the code (`GenerateDocs`). Do not edit it by hand:
> change the setting's description in the code and run the generator again (see `5-shared-engine/README.md`).

## The short version

* All settings live in one **folder**: `plugins/AntiBadWords/` (Bukkit, Spigot, Paper, Purpur) or
  `config/antibadwords/` (Fabric, Forge). The folder looks the same on every server type.
* The files are created on the first start, with a comment above every option.
* After you change a file, type `/abw reload`. No restart needed.
* A typo does not break the server: the wrong value is ignored (the default is used), and the console and
  `/abw reload` tell you the **file** and the **key** that is wrong. If a whole file cannot be read, the
  settings from before stay active.

```
config.yml               general: languages on/off, how strict, where to look, messages
wordlists/<code>.yml     one file per language (en.yml, de.yml ...) and custom.yml for your own words
moderation/ladder.yml    what happens to players who keep swearing (punishments)
worlds.yml               different rules per world or per group of players
data/                    the plugin's own memory - do not edit
```

Messages accept `&` colour codes and these placeholders (any capitalisation):
`{player} {word} {source} {count} {limit} {remaining} {punishments}`.

"Per world: yes" means a profile in `worlds.yml` can give that setting another value for some worlds or players.

## `config.yml`

### General

| Key | Type | Default | Per world | What it does |
|-----|------|---------|:---------:|--------------|
| `config-version` | whole number | `2` | no | Layout version of this config folder. Do not change it (it tells the plugin that this is the new folder layout). |
| `logging.console` | true or false | `true` | yes | Print every blocked word to the server console. |
| `commands.list-page-size` | whole number from 1 to 100 | `10` | no | How many entries one page of a list command shows (for example /abw whitelist list). |

### Languages

| Key | Type | Default | Per world | What it does |
|-----|------|---------|:---------:|--------------|
| `filters.enabled-word-lists` | list of texts | `en`, `custom` | yes | Which word-list files from the wordlists folder are switched on. Each language is one file named by its code (en, de, id, pt-br ...). "custom" holds your own words (/abw addword) and is always on.<br>In game: /abw lang list, /abw lang enable <code>, /abw lang disable <code>. |
| `filters.protection-level` | whole number from 1 to 3 | `3` | yes | How strict the filter is. Every list is split into level-1 (mild), level-2 (rude) and level-3 (severe) words.<br>  1 = LOW    -> only level-3 words are blocked<br>  2 = MEDIUM -> level-2 and level-3 words are blocked<br>  3 = HIGH   -> everything is blocked (level-1, 2 and 3) |

### Scanning

| Key | Type | Default | Per world | What it does |
|-----|------|---------|:---------:|--------------|
| `filters.scan.chat` | true or false | `true` | yes | Look for bad words in chat messages. |
| `filters.scan.commands` | true or false | `true` | yes | Look for bad words in commands such as /msg (see scanned-commands below). |
| `filters.scan.signs` | true or false | `true` | yes | Look for bad words on signs. |
| `filters.scan.books` | true or false | `true` | yes | Look for bad words in books (pages and title). |
| `filters.scan.anvil` | true or false | `true` | yes | Look for bad words in item names typed in an anvil. |
| `filters.scanned-commands` | list of texts | `msg`, `tell`, `w`, `whisper`, `r`, `reply`, `me`, `say`, `teammsg`, `tm`, `mail`, `nick`, `broadcast` | yes | Commands whose arguments are scanned (only used when scan.commands is true).<br>Use "*" to scan the arguments of every command. |
| `filters.ignored-commands` | list of texts | `antibadwords`, `abw`, `antiwords` | yes | Commands that are never scanned (so admins can still manage the word list). |
| `filters.check-order` | list of texts | `badword` | yes | The order in which the checks look at a message. Checks that are not listed run afterwards.<br>The first check that blocks a message ends the search. |

### Matching

| Key | Type | Default | Per world | What it does |
|-----|------|---------|:---------:|--------------|
| `filters.matching.collapse-repeated-letters` | true or false | `true` | yes | "fuuuuck" -> "fuck". Letters repeated many times in a row are collapsed. |
| `filters.matching.repeated-letter-threshold` | whole number, at least 2 | `3` | yes | Letters repeated this many times (or more) in a row are collapsed. |
| `filters.matching.detect-spaced-letters` | true or false | `true` | yes | Catch "f u c k" and "f.u.c.k" (3 or more single letters in a row). |
| `filters.matching.join-adjacent-words` | true or false | `false` | yes | Also glue ADJACENT words together before matching (catches "fu ck", but also "pen is").<br>Off by default because it causes false positives. |
| `filters.matching.partial-match` | true or false | `false` | yes | Block a word when it merely CONTAINS a blocked word (e.g. "motherfuckers").<br>Off by default because it flags innocent words such as "class", "closer" or "scrap".<br>If you turn it on, add the innocent words to the whitelist below. |
| `filters.matching.partial-min-length` | whole number, at least 3 | `4` | yes | Blocked words shorter than this are never used for partial matching. |

### Whitelist

| Key | Type | Default | Per world | What it does |
|-----|------|---------|:---------:|--------------|
| `filters.whitelist` | list of texts | `class`, `classic`, `assassin`, `assume`, `assist`, `bass`, `glass`, `grass`, `pass`, `mass`, `closer`, `scrap`, `cockpit`, `cocktail`, `peacock`, `computer`, `computing`, `dickens`, `analysis`, `therapist` | yes | Words that must never be blocked (mainly useful together with partial-match).<br>In game: /abw whitelist add <word>, /abw whitelist remove <word>, /abw whitelist list. |

### Messages

| Key | Type | Default | Per world | What it does |
|-----|------|---------|:---------:|--------------|
| `messages.broadcast-warnings` | true or false | `true` | yes | Placeholders in all messages (any capitalisation): {player} {word} {source} {count} {limit} {remaining} {punishments}<br>Show the warning to every player (true) or only to the player who swore (false). |
| `messages.warning-broadcast-format` | text | `&C{PLAYER} SAID THE WORD &F{WORD}&C WHICH IS NOT ALLOWED.` | yes | Sent to everyone when a bad word is blocked. Colours: & codes. |
| `messages.warning-player` | text | `&EWATCH YOUR WORDS. YOU ARE PUNISHED IF YOU SAY &F{REMAINING}&E BADWORDS AGAIN.` | yes | Sent only to the player who swore (while they are not punished yet). Colours: & codes. |
| `messages.punishment-broadcast-format` | text | `&C{PLAYER} SAID THE WORD &F{WORD}&C WHICH IS NOT ALLOWED. FOR THEIR ACTION THEY'LL BE PUNISHED WITH &F{PUNISHMENTS}&C.` | yes | Sent to everyone when the punishment is applied. Colours: & codes. |
| `messages.punishment-player` | text | `&CFOR YOUR BADWORDS, YOU ARE PUNISHED WITH &F{PUNISHMENTS}&C.` | yes | Sent to the player when the punishment is applied. Colours: & codes. |
| `messages.punishment-disabled-player` | text | `&EYOUR MESSAGE WAS BLOCKED. PUNISHMENT IS CURRENTLY DISABLED.` | yes | Sent to the player when punishment.enabled is false but they reached the limit. Colours: & codes. |
| `messages.notify-staff` | true or false | `true` | yes | Tell staff (permission antibadwords.notify) about every blocked word. |
| `messages.staff-notify-format` | text | `&8[&cAntiBadWords&8] &7Blocked '&f{word}&7' from &f{player}&7 in &f{source}&7 (&f{count}&7/&f{limit}&7).` | yes | What staff see. Colours: & codes. |

## `moderation/ladder.yml`

### Punishment

| Key | Type | Default | Per world | What it does |
|-----|------|---------|:---------:|--------------|
| `punishment.enabled` | true or false | `true` | yes | Master switch. When false, bad words are still blocked but nobody is punished. |
| `punishment.violations-before-punish` | whole number, at least 1 | `2` | yes | How many violations before punishing the player |
| `punishment.violations-expire-minutes` | whole number, at least 0 | `0` | no | Violations older than this many minutes are forgotten (0 = never expire). |
| `punishment.clear-violations-on-quit` | true or false | `true` | no | Forget a player's violations when they leave the server. |
| `punishment.final-command.enabled` | true or false | `false` | yes | Run the command below after the punishment actions. |
| `punishment.final-command.command` | text | (empty) | yes | Runs from the console after the actions below. Supports {player}, {word}, {source}.<br>Example: "lp user {player} permission settemp antibadwords.muted true 30s" |
| `punishment.effects` | section (your own keys) | see the file | yes | Every action below is applied (when enabled: true) at the moment a player is punished.<br>You may add your own entries; the names (blindness, slowness ...) are free.<br>This part cannot be edited in the in-game menu; edit the file. |
| `punishment.attributes` | section (your own keys) | see the file | yes | Temporarily SETS an attribute to a value, then restores the original value automatically<br>(also when the player leaves, the server stops, or after a crash).<br>This part cannot be edited in the in-game menu; edit the file. |
| `punishment.sounds` | section (your own keys) | see the file | yes | Sounds played to the punished player.<br>This part cannot be edited in the in-game menu; edit the file. |
| `punishment.commands` | section (your own keys) | see the file | yes | Console commands run for the punished player.<br>This part cannot be edited in the in-game menu; edit the file. |

## `worlds.yml`

### Worlds

| Key | Type | Default | Per world | What it does |
|-----|------|---------|:---------:|--------------|
| `default-profile` | text | `default` | no | The profile used when no world rule and no permission picks another one. |
| `use-permission-profiles` | true or false | `true` | no | When true, a player with the permission antibadwords.profile.<name> always gets that profile,<br>whatever world they are in. Nobody has these permissions unless you give them. |
| `profiles` | section (your own keys) | see the file | no | Named sets of rules. A profile lists only what is DIFFERENT from the normal files; everything else stays the same.<br>You can override every setting that CONFIGURATION.md marks with "per world: yes", written with its full key.<br>Example:<br>  profiles:<br>    default: {}<br>    relaxed:<br>      filters.protection-level: 1<br>      punishment.enabled: false<br>    german-lobby:<br>      filters.enabled-word-lists: [de, en]<br>This part cannot be edited in the in-game menu; edit the file. |
| `worlds` | section (your own keys) | see the file | no | Which world uses which profile: world name (or pattern) on the left, profile name on the right. The first match wins.<br>* means "any text". On Bukkit/Spigot/Paper/Purpur use the world folder name (world, world_nether ...) or the<br>dimension name (minecraft:overworld, minecraft:the_nether, minecraft:the_end).<br>Example:<br>  worlds:<br>    "creative_*": relaxed<br>    "minecraft:the_nether": relaxed<br>This part cannot be edited in the in-game menu; edit the file. |

## Punishment actions (`moderation/ladder.yml`)

Each entry under `punishment.effects`, `.attributes`, `.sounds` and `.commands` has `enabled: true/false`.
Every enabled action runs when a player is punished. You may add your own entries.

- **effects** - `effect-name`, `amplifier` (0 = level I), `duration` (seconds).
- **attributes** - `attribute-name`, `value`, `duration` (seconds). The attribute is set, then the original value
  is put back automatically (timer end, quit, kick, death/respawn, server stop; repaired on the next join after a crash).
- **sounds** - `sound-name` (for example `entity.villager.no`), `volume`, `pitch`.
- **commands** - `command`, run from the console; `{player}` is replaced.

Old-style `format: "name-value-seconds"` entries still work.

## Word lists (`wordlists/`)

One file per language, named by its code (`en.yml`, `de.yml`, `pt-br.yml` ...). Every file has three tiers:
`level-1` (mild), `level-2` (rude), `level-3` (severe). The protection level decides which tiers count:
1 = only level-3, 2 = level-2 and level-3, 3 = all.

* New installs start with **English (`en`) and your custom list** switched on.
* `/abw lang list` shows all languages, `/abw lang enable de` switches German on, `/abw lang disable de` off.
* `custom.yml` holds your own words. `/abw addword <word>` puts a word into its `level-3` list (so it counts at
  every protection level); `/abw removeword <word>` takes it out. You can also edit the file.
* A language file that you delete is copied back from the jar at the next start or reload (switched off unless
  you enabled it). The file format is explained in `7-wordlists/README.md`.

## Different rules per world or group (`worlds.yml`)

A **profile** is a named list of settings that are different from the normal files. Which profile a player gets:

1. a profile whose permission `antibadwords.profile.<name>` the player has (first one in file order), if
   `use-permission-profiles` is true;
2. otherwise the first line under `worlds:` whose pattern matches the player's world (`*` = any text);
3. otherwise the `default-profile`.

```yaml
default-profile: default
use-permission-profiles: true
profiles:
  default: {}
  relaxed:
    filters.protection-level: 1
    punishment.enabled: false
  german-lobby:
    filters.enabled-word-lists: [de, en]
worlds:
  "creative_*": relaxed
  "minecraft:the_nether": relaxed
```

Limits: on Fabric and Forge the world name is not known to the mod yet, so `worlds:` rules do not match there
(profiles by permission and the default profile work). `/abw check`, `/abw lang`, `/abw whitelist` and
`/abw listwords` always work on the normal files, not on a profile. Strike expiry and "clear on quit" are global.

## Coming from an older version (one big `config.yml`)

On the first start the old file is converted automatically:

* the old file is copied to `config.yml.bak-<date>` and is never deleted;
* every setting keeps its value; your old word lists become `wordlists/<code>.yml` files with exactly your old
  words (`english` -> `en`, `uk_english` -> `en-gb`, `indonesian` -> `id`, `portuguese_brazil` -> `pt-br`,
  `russian` -> `ru`, `german` -> `de`, `chinese` -> `zh`), and the same languages stay switched on;
* custom words go to `wordlists/custom.yml`; the whitelist stays in `config.yml`;
* the console lists what was converted.

The filter then blocks exactly what it blocked before. To get the bigger built-in list of a language, delete its
file in `wordlists/` and run `/abw reload`.

## Commands

All commands need the permission `antibadwords.admin` (operators have it by default).

| Command | What it does |
|---------|--------------|
| `/abw reload` | read the config folder again; shows config problems |
| `/abw addword <word>` / `removeword <word>` | add or remove your own blocked word |
| `/abw listwords [code\|custom\|active]` | show a word list |
| `/abw check <text>` | would this text be blocked? |
| `/abw violations <player>` / `resetviolations <player>` | see or clear someone's strikes |
| `/abw info` | short status |
| `/abw lang list` / `enable <code>` / `disable <code>` | switch language word lists on and off |
| `/abw whitelist add <word>` / `remove <word>` / `list [page]` | words that are never blocked |

## Permissions

| Permission | Default | Use |
|------------|---------|-----|
| `antibadwords.admin` | op | All `/abw` sub-commands |
| `antibadwords.notify` | op | Staff alert for every blocked word |
| `antibadwords.bypass` | nobody | Exempt from all checks |
| `antibadwords.profile.<name>` | nobody | Puts the player on that `worlds.yml` profile |
