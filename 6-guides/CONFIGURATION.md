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
filters/spam.yml         spam and flood control (off until you switch it on)
filters/links.yml        links, IP addresses and invites (off until you choose a mode)
filters/evasion.yml      bypass tricks: invisible characters, look-alike letters, leetspeak
filters/confusables.yml  the tables for look-alike letters and leetspeak
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
| `filters.scan.sign-back` | true or false | `true` | yes | Look at the back side of signs too (only used when scan.signs is true). Bukkit/Spigot/Paper/Purpur can tell the two<br>sides apart; on Fabric and Forge both sides are always covered by scan.signs and this switch changes nothing. |
| `filters.scan.command-blocks` | true or false | `false` | yes | Look at the commands that command blocks (and command-block minecarts) run, using scanned-commands below. A command with a blocked word is not run. Bukkit/Spigot/Paper/Purpur only; not available on Fabric and Forge yet. Nobody gets strikes for it (the game does not say who wrote the command). |
| `filters.scan.join-names` | true or false | `false` | yes | Check the name of every player who joins against the word lists (see join-names below for what happens). |
| `filters.scanned-commands` | list of texts | `msg`, `tell`, `w`, `whisper`, `r`, `reply`, `me`, `say`, `teammsg`, `tm`, `mail`, `nick`, `broadcast`, `team`, `title`, `tellraw`, `bossbar`, `scoreboard`, `rename` | yes | Commands whose arguments are scanned (only used when scan.commands is true).<br>Use "*" to scan the arguments of every command. |
| `filters.ignored-commands` | list of texts | `antibadwords`, `abw`, `antiwords` | yes | Commands that are never scanned (so admins can still manage the word list). |
| `filters.check-order` | list of texts | `evasion`, `badword`, `link`, `spam` | yes | The order in which the checks look at a message. Checks that are not listed run afterwards.<br>The first check that blocks a message ends the search; a check that cleans a message hands the cleaned text on.<br>Names: evasion (bypass tricks, filters/evasion.yml), badword (the word lists), link (filters/links.yml),<br>spam (all rules of filters/spam.yml) or single spam rules: spam-commands, spam-rate, spam-repeat, spam-caps,<br>spam-flood, spam-words. |
| `filters.command-blocks.report-interval` | time such as 30s, 10m, 2h or 1d, at least 0s, at most 1d | `1m` | no | A repeating command block can run 20 times per second. The same blocked command is reported (console, events) only once in this time; it is stopped every time. |

### Player names

| Key | Type | Default | Per world | What it does |
|-----|------|---------|:---------:|--------------|
| `filters.join-names.action` | one of: log, warn, kick | `log` | yes | What happens when a joining player's name contains a blocked word (only used when scan.join-names is true):<br>  log  = a line in the console (and an event for the history / Discord features)<br>  warn = also tell the player and the staff<br>  kick = remove the player from the server with kick-message, and tell the staff<br>Players with the permission antibadwords.bypass are never checked. Nobody gets strikes for a name. |
| `filters.join-names.match-inside-names` | true or false | `true` | yes | Player names have no spaces, so a blocked word is also found INSIDE a name ("xXbadwordXx").<br>Whitelisted words inside a name are skipped ("Dickens99" is fine while "dickens" is on the whitelist).<br>false = only when the whole name (or a part between _) is the word. |
| `filters.join-names.min-word-length` | whole number from 3 to 32 | `4` | yes | Blocked words shorter than this are not searched inside names (short words are part of many innocent names). |
| `filters.join-names.kick-message` | text | `&cYour player name is not allowed on this server.` | yes | Shown to the kicked player. Placeholders: {player} {word}. Colours: & codes. |
| `filters.join-names.warn-message` | text | `&eYour player name contains a word that is not welcome here.` | yes | Sent to the player when the action is warn. Placeholders: {player} {word}. Empty = no message. |
| `filters.join-names.staff-message` | text | `&8[&cAntiBadWords&8] &7The name of &f{player}&7 contains '&f{word}&7'.` | yes | Sent to staff (permission antibadwords.notify) for warn and kick, when messages.notify-staff is true. |

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

### Strikes

| Key | Type | Default | Per world | What it does |
|-----|------|---------|:---------:|--------------|
| `strikes.save-to-disk` | true or false | `true` | no | Save every player's strikes (violations) in data/strikes.json so they are still there after a server restart.<br>This only has an effect when punishment.clear-violations-on-quit is false: while that is true (the default),<br>a player's strikes are forgotten as soon as they leave, so there is nothing to keep.<br>false = strikes live in memory only and are gone after a restart (how old versions worked). |
| `strikes.decay.every` | time such as 30s, 10m, 2h or 1d, at least 0s, at most 365d | `0s` | no | Strikes can fade away slowly: every time this much time has passed, the player loses some strikes (see<br>points below). The time counts from the player's first strike and also while the player or the server is offline.<br>0s = off. Example: 1h = one strike less every hour.<br>This works next to punishment.violations-expire-minutes, which forgets ALL strikes at once when the last one<br>is old enough. |
| `strikes.decay.points` | whole number from 1 to 1000 | `1` | no | How many strikes are taken away each time. |
| `strikes.max-points` | whole number from 0 to 1000000 | `0` | no | A player never has more strikes than this. 0 = no limit. |
| `strikes.save-delay` | time such as 30s, 10m, 2h or 1d, at least 0s, at most 1h | `5s` | no | After a change the data files (strikes, known players) are written once this much time has passed, so many<br>changes in a row cause one write. They are always written when the server stops. |
| `known-players.forget-after-days` | whole number from 0 to 36500 | `0` | no | The plugin remembers the name of every player who joined (data/players.json), so commands such as<br>/abw violations also work for players who are offline. Players who were not seen for this many days<br>are removed from that list. 0 = never. |

### Ladder

| Key | Type | Default | Per world | What it does |
|-----|------|---------|:---------:|--------------|
| `ladder.steps` | section (your own keys) | see the file | yes | The punishment ladder: what happens at how many strikes. Each step has a name you choose.<br>While this is empty ({}), the simple setup above is used: at punishment.violations-before-punish strikes every<br>enabled action under punishment.effects / attributes / sounds / commands runs and the strikes start again at 0.<br>As soon as there is at least one step, the steps decide instead and strikes are NOT set back to 0 by themselves<br>(they fade with strikes.decay / punishment.violations-expire-minutes, or use the action reset-strikes).<br>A step has:<br>  at:        the number of strikes at which it happens<br>  repeat:    once (only at that number) or a number N (again every N strikes after it)<br>  message:   sent to the player (optional). broadcast: sent to everyone (optional). Colours: & codes.<br>  final-command: a console command run after the messages (optional, placeholders as below)<br>  actions:   a list, done from top to bottom:<br>    warn [text]                      nothing but the message (or one more line of text)<br>    mute <time\|perm> [reason]        built-in mute, time like 30s, 10m, 1h30m, 2d<br>    kick [reason]<br>    ban [reason]                     runs ladder.ban-command (the normal Minecraft ban, no end date)<br>    command <console command>        for example a ban plugin: command tempban {player} 1d {reason}<br>    effect <name> <level> <seconds>  attribute <name> <value> <seconds>  sound <name> <volume> <pitch><br>    punishments                      every enabled action under punishment.effects / attributes / sounds / commands<br>    reset-strikes                    the player starts again at 0 strikes<br>Placeholders in messages and commands: {player} {uuid} {word} {source} {count} {step} {threshold} {reason}<br>{duration}; in messages also {punishments} {limit} {remaining}.<br>The simple setup above is the same as this one step (what you get when you leave steps empty):<br>  steps:<br>    legacy:<br>      at: <violations-before-punish><br>      repeat: 1<br>      message: <punishment-player><br>      broadcast: <punishment-broadcast-format><br>      final-command: <punishment.final-command.command, when enabled><br>      actions: [punishments, reset-strikes]<br>Example:<br>  steps:<br>    first-warning:<br>      at: 2<br>      repeat: once<br>      message: "&eThis is a warning, {player}. Next time you are muted."<br>      actions: [warn]<br>    short-mute:<br>      at: 4<br>      repeat: once<br>      message: "&cYou are muted for {duration}."<br>      actions:<br>        - mute 10m Bad language<br>        - sound minecraft:entity.villager.no 1 1<br>    kick:<br>      at: 6<br>      repeat: 2<br>      actions: ["kick Watch your language"]<br>A profile in worlds.yml can have its own ladder (key ladder.steps).<br>This part cannot be edited in the in-game menu; edit the file. |
| `ladder.when-several-steps-are-reached` | one of: all, highest | `all` | yes | One violation can be worth several strikes and so reach more than one step at the same moment.<br>all = every reached step happens, lowest first. highest = only the highest one. |
| `ladder.default-reason` | text | `Bad language` | yes | The reason used by mute, kick and ban actions that do not give one. Colours: & codes. |
| `ladder.ban-command` | text | `ban {player} {reason}` | yes | The console command the ban action runs. Minecraft itself cannot ban for a limited time; for that use<br>the command action with the command of a ban plugin (example: command tempban {player} 1d {reason}). |

## `worlds.yml`

### Worlds

| Key | Type | Default | Per world | What it does |
|-----|------|---------|:---------:|--------------|
| `default-profile` | text | `default` | no | The profile used when no world rule and no permission picks another one. |
| `use-permission-profiles` | true or false | `true` | no | When true, a player with the permission antibadwords.profile.<name> always gets that profile,<br>whatever world they are in. Nobody has these permissions unless you give them. |
| `profiles` | section (your own keys) | see the file | no | Named sets of rules. A profile lists only what is DIFFERENT from the normal files; everything else stays the same.<br>You can override every setting that CONFIGURATION.md marks with "per world: yes", written with its full key.<br>Example:<br>  profiles:<br>    default: {}<br>    relaxed:<br>      filters.protection-level: 1<br>      punishment.enabled: false<br>    german-lobby:<br>      filters.enabled-word-lists: [de, en]<br>This part cannot be edited in the in-game menu; edit the file. |
| `worlds` | section (your own keys) | see the file | no | Which world uses which profile: world name (or pattern) on the left, profile name on the right. The first match wins.<br>* means "any text". On Bukkit/Spigot/Paper/Purpur use the world folder name (world, world_nether ...) or the<br>dimension name (minecraft:overworld, minecraft:the_nether, minecraft:the_end).<br>Example:<br>  worlds:<br>    "creative_*": relaxed<br>    "minecraft:the_nether": relaxed<br>This part cannot be edited in the in-game menu; edit the file. |

## `filters/spam.yml`

### Spam

| Key | Type | Default | Per world | What it does |
|-----|------|---------|:---------:|--------------|
| `filters.spam.enabled` | true or false | `false` | yes | Master switch for everything in this file. Off by default: switch it on, then switch off the rules you do not want. |
| `filters.spam.max-tracked-players` | whole number from 10 to 1000000 | `5000` | no | For how many players message times are remembered at the same time (memory limit). When more players are<br>active, the ones who were quiet the longest are forgotten first. |
| `filters.spam.forget-after` | time such as 30s, 10m, 2h or 1d, at least 10s, at most 1d | `10m` | no | A player who sent nothing for this long is forgotten (players are also forgotten when they leave). |
| `filters.spam.rate.enabled` | true or false | `true` | yes | TOO MANY MESSAGES: A player sends messages faster than allowed.<br>Only used while filters.spam.enabled is true. |
| `filters.spam.rate.action` | one of: block, log-only | `block` | yes | block = the message is not delivered; log-only = it goes through and is only noted. |
| `filters.spam.rate.message` | text | `&cYou are sending messages too fast. Please slow down.` | yes | Sent to the player when the message was blocked by this rule. Empty = no message. Colours: & codes. |
| `filters.spam.rate.strike-weight` | whole number from 0 to 100 | `0` | yes | How many strikes this adds. 0 = none (spam never leads to a punishment unless you raise this). |
| `filters.spam.rate.sources` | list of texts | `chat` | yes | Text sources this rule looks at: chat, command, sign, book, anvil (or "*" for all). |
| `filters.spam.rate.max-messages` | whole number from 1 to 1000 | `5` | yes | How many messages a player may send within the time window. The next one is caught. |
| `filters.spam.rate.window` | time such as 30s, 10m, 2h or 1d, at least 1s, at most 1h | `5s` | yes | Length of the time window for the line above. |
| `filters.spam.repeat.enabled` | true or false | `true` | yes | REPEATED MESSAGES: A player sends the same (or nearly the same) message again and again.<br>Only used while filters.spam.enabled is true. |
| `filters.spam.repeat.action` | one of: block, log-only | `block` | yes | block = the message is not delivered; log-only = it goes through and is only noted. |
| `filters.spam.repeat.message` | text | `&cPlease do not repeat the same message.` | yes | Sent to the player when the message was blocked by this rule. Empty = no message. Colours: & codes. |
| `filters.spam.repeat.strike-weight` | whole number from 0 to 100 | `0` | yes | How many strikes this adds. 0 = none (spam never leads to a punishment unless you raise this). |
| `filters.spam.repeat.sources` | list of texts | `chat` | yes | Text sources this rule looks at: chat, command, sign, book, anvil (or "*" for all). |
| `filters.spam.repeat.max-repeats` | whole number from 1 to 100 | `2` | yes | How many times the same message may be sent within the time window. The next one is caught. |
| `filters.spam.repeat.window` | time such as 30s, 10m, 2h or 1d, at least 1s, at most 1d | `30s` | yes | How long a message is remembered for this comparison. |
| `filters.spam.repeat.similarity` | number from 0.5 to 1 | `0.85` | yes | 1.0 = only exactly the same text counts. 0.85 = messages that differ in up to 15% of their characters also count<br>("hello!!" and "hello!!!"). Upper and lower case and extra spaces never matter. |
| `filters.spam.repeat.min-length` | whole number from 1 to 256 | `4` | yes | Shorter messages ("ok", "gg") are never counted as repeats. |
| `filters.spam.repeat.history-size` | whole number from 1 to 50 | `5` | yes | How many of a player's last messages are kept for the comparison. |
| `filters.spam.caps.enabled` | true or false | `true` | yes | TOO MANY CAPITAL LETTERS: A message is written mostly IN CAPITAL LETTERS.<br>Only used while filters.spam.enabled is true. |
| `filters.spam.caps.action` | one of: block, censor, log-only | `censor` | yes | block = the message is not delivered; censor = a cleaned-up text is delivered instead (chat on Bukkit/Spigot/Paper/Purpur; everywhere else the message is blocked); log-only = it goes through and is only noted. |
| `filters.spam.caps.message` | text | `&cPlease do not write in capital letters.` | yes | Sent to the player when the message was blocked by this rule. Empty = no message. Colours: & codes. |
| `filters.spam.caps.strike-weight` | whole number from 0 to 100 | `0` | yes | How many strikes this adds. 0 = none (spam never leads to a punishment unless you raise this). |
| `filters.spam.caps.sources` | list of texts | `chat` | yes | Text sources this rule looks at: chat, command, sign, book, anvil (or "*" for all). |
| `filters.spam.caps.max-percent` | whole number from 1 to 100 | `70` | yes | A message is caught when more than this share of its letters are capitals. |
| `filters.spam.caps.min-length` | whole number from 1 to 256 | `8` | yes | Messages with fewer letters than this are never caught ("LOL", "GG"). |
| `filters.spam.flood.enabled` | true or false | `true` | yes | CHARACTER FLOOD: The same character many times in a row ("heeeeeeello", "!!!!!!!!"), or a message that is mostly symbols or emoji.<br>Only used while filters.spam.enabled is true. |
| `filters.spam.flood.action` | one of: block, censor, log-only | `censor` | yes | block = the message is not delivered; censor = a cleaned-up text is delivered instead (chat on Bukkit/Spigot/Paper/Purpur; everywhere else the message is blocked); log-only = it goes through and is only noted. |
| `filters.spam.flood.message` | text | `&cPlease do not flood the chat with repeated characters.` | yes | Sent to the player when the message was blocked by this rule. Empty = no message. Colours: & codes. |
| `filters.spam.flood.strike-weight` | whole number from 0 to 100 | `0` | yes | How many strikes this adds. 0 = none (spam never leads to a punishment unless you raise this). |
| `filters.spam.flood.sources` | list of texts | `chat` | yes | Text sources this rule looks at: chat, command, sign, book, anvil (or "*" for all). |
| `filters.spam.flood.max-repeated-characters` | whole number from 2 to 256 | `6` | yes | A message is caught when one character (not a digit) is repeated more often than this in a row. |
| `filters.spam.flood.collapse-to` | whole number from 1 to 256 | `3` | yes | With action censor, a too long run is shortened to this many characters. |
| `filters.spam.flood.max-symbol-percent` | whole number from 1 to 100 | `70` | yes | A message is caught when more than this share of its characters (spaces not counted) are symbols, punctuation<br>or emoji. 100 = never. With action censor such a message is blocked, because there is nothing sensible to keep. |
| `filters.spam.flood.symbol-min-length` | whole number from 1 to 256 | `10` | yes | Shorter messages (":)", "???") are never caught by the symbol rule. |
| `filters.spam.words.enabled` | true or false | `true` | yes | REPEATED WORDS: The same word many times in one message ("buy buy buy buy buy").<br>Only used while filters.spam.enabled is true. |
| `filters.spam.words.action` | one of: block, censor, log-only | `block` | yes | block = the message is not delivered; censor = a cleaned-up text is delivered instead (chat on Bukkit/Spigot/Paper/Purpur; everywhere else the message is blocked); log-only = it goes through and is only noted. |
| `filters.spam.words.message` | text | `&cPlease do not repeat the same word so often.` | yes | Sent to the player when the message was blocked by this rule. Empty = no message. Colours: & codes. |
| `filters.spam.words.strike-weight` | whole number from 0 to 100 | `0` | yes | How many strikes this adds. 0 = none (spam never leads to a punishment unless you raise this). |
| `filters.spam.words.sources` | list of texts | `chat` | yes | Text sources this rule looks at: chat, command, sign, book, anvil (or "*" for all). |
| `filters.spam.words.max-identical-words` | whole number from 1 to 256 | `4` | yes | A message is caught when one word appears more often than this. With action censor the extra copies are removed. |
| `filters.spam.words.min-word-length` | whole number from 1 to 64 | `2` | yes | Shorter words ("a", "I") are not counted. |
| `filters.spam.commands.enabled` | true or false | `true` | yes | TOO MANY COMMANDS: A player runs commands faster than allowed. Every command counts, also the ones that are not scanned for bad words.<br>Only used while filters.spam.enabled is true. |
| `filters.spam.commands.action` | one of: block, log-only | `block` | yes | block = the message is not delivered; log-only = it goes through and is only noted. |
| `filters.spam.commands.message` | text | `&cYou are using commands too fast. Please slow down.` | yes | Sent to the player when the message was blocked by this rule. Empty = no message. Colours: & codes. |
| `filters.spam.commands.strike-weight` | whole number from 0 to 100 | `0` | yes | How many strikes this adds. 0 = none (spam never leads to a punishment unless you raise this). |
| `filters.spam.commands.max-commands` | whole number from 1 to 1000 | `8` | yes | How many commands a player may run within the time window. The next one is caught. |
| `filters.spam.commands.window` | time such as 30s, 10m, 2h or 1d, at least 1s, at most 1h | `5s` | yes | Length of the time window for the line above. |
| `filters.spam.commands.exempt-commands` | list of texts | (empty) | yes | Command names (without /) that are not counted, for example commands a client mod sends automatically. |

## `filters/links.yml`

### Links

| Key | Type | Default | Per world | What it does |
|-----|------|---------|:---------:|--------------|
| `filters.links.mode` | one of: off, block-all, allow-list, block-list | `off` | yes | What the link filter does:<br>  off        = links are not checked (default)<br>  block-all  = every link is caught<br>  allow-list = every link is caught, except the domains in allowed-domains<br>  block-list = only the domains in blocked-domains are caught |
| `filters.links.allowed-domains` | list of texts | (empty) | yes | Used by mode allow-list (and for IP addresses you want to allow). One entry per domain:<br>  example.com     = that domain and everything under it (www.example.com, shop.example.com)<br>  *.example.com   = only the names under it<br>  .org            = every domain that ends like this<br>  203.0.113.5     = that address |
| `filters.links.blocked-domains` | list of texts | (empty) | yes | Used by mode block-list. Same forms as allowed-domains. |
| `filters.links.block-ip-addresses` | true or false | `true` | yes | While the filter is on (any mode): catch IPv4 and IPv6 addresses such as 203.0.113.5 (server advertising),<br>unless the address is listed in allowed-domains. false = addresses are ignored. |
| `filters.links.block-invites` | true or false | `true` | yes | While the filter is on (any mode): catch invite links (see invite-links below), even when their domain is<br>allowed, unless the invite is listed in allowed-invites. false = invites are treated like any other link. |
| `filters.links.invite-links` | list of texts | `discord.gg`, `discord.com/invite`, `discordapp.com/invite`, `discord.me`, `discord.io`, `dsc.gg`, `invite.gg`, `t.me`, `telegram.me`, `chat.whatsapp.com`, `guilded.gg` | yes | A domain, or a domain with the start of the path. Everything that begins like one of these is an invite. |
| `filters.links.allowed-invites` | list of texts | (empty) | yes | Invites that are fine, for example your own server: discord.gg/yourcode |
| `filters.links.detect-hidden-dots` | true or false | `true` | yes | Also find links whose dot is hidden: "example dot com", "example(dot)com", "example[.]com",<br>"example .com", "example . com". (Unicode dots and invisible characters are always handled.) |
| `filters.links.dot-words` | list of texts | `dot`, `d0t`, `punkt`, `punto`, `ponto`, `точка` | yes | Used by detect-hidden-dots. Lower case. |
| `filters.links.domain-endings` | list of texts | `com`, `net`, `org`, `gg`, `io`, `co`, `xyz`, `info`, `biz`, `dev`, `app`, `tv`, `cc`, `ly`, `gl`, `link`, `online`, `site`, `store`, `shop`, `club`, `fun`, `pro`, `live`, `top`, `vip`, `games`, `host`, `cloud`, `tech`, `space`, `website`, `network`, `edu`, `gov`, `tk`, `ml`, `ga`, `cf`, `gq`, `uk`, `ru`, `fr`, `nl`, `pl`, `br`, `cz`, `dk`, `fi`, `jp`, `cn`, `kr`, `ua`, `hu`, `gr`, `mx`, `tr`, `ch`, `ro`, `pt`, `su` | yes | A word.ending without http:// or www. only counts as a link when the ending is in this list. Endings that<br>are also everyday words in some language (de, es, it, me, in, at, to, is, us, no, se, eu, ca, au, id ...) are left<br>out on purpose, because "casa.de" or "trust.me" would be caught; add them if your players abuse them.<br>Some endings in the list are normal words too (fun, live, top ...): "that was.fun" without a space is then read<br>as a link; remove them if that bothers you. Links with http://, https:// or www. and the domains named in your<br>own lists are always found. |
| `filters.links.action` | one of: block, censor, log-only | `block` | yes | block = the message is not delivered; censor = the link is replaced by censor-replacement (chat on<br>Bukkit/Spigot/Paper/Purpur; everywhere else the message is blocked); log-only = it goes through and is only noted. |
| `filters.links.censor-replacement` | text | `[link]` | yes | What a caught link is replaced with when the action is censor. |
| `filters.links.message` | text | `&cLinks are not allowed here.` | yes | Sent to the player when the message was blocked for a link. Empty = no message. Colours: & codes. |
| `filters.links.strike-weight` | whole number from 0 to 100 | `0` | yes | How many strikes a caught link adds. 0 = none. |
| `filters.links.sources` | list of texts | `chat`, `command`, `sign`, `book`, `anvil` | yes | Text sources: chat, command, sign, book, anvil, join, command-block (or "*" for all). |

## `filters/evasion.yml`

### Evasion

| Key | Type | Default | Per world | What it does |
|-----|------|---------|:---------:|--------------|
| `filters.evasion.normalise.strip-invisible-characters` | true or false | `true` | yes | Invisible characters (zero-width spaces and joiners, direction marks, soft hyphens, variation selectors, empty<br>filler letters) are removed before words are compared, so a word with a hidden character inside is still found.<br>When false they split the word in two. |
| `filters.evasion.normalise.fold-compatibility-forms` | true or false | `true` | yes | Full-width letters, circled letters, mathematical bold/italic letters and similar special forms are read as the<br>plain letter (Unicode NFKC folding). |
| `filters.evasion.normalise.fold-diacritics` | true or false | `true` | yes | Accents and other marks on letters are ignored, so "fück" is read as "fuck" and "cabron" matches "cabrón". |
| `filters.evasion.normalise.keep-diacritics-in-scripts` | list of texts | (empty) | yes | Writing systems in which marks are NOT ignored, because there they change the word. Empty = ignore them<br>everywhere (this is how the plugin always worked). Names as in Unicode, for example: latin, cyrillic, greek,<br>arabic, hebrew, devanagari, bengali, thai. Example: [devanagari, arabic] keeps Hindi vowel signs and Arabic vowel marks;<br>[latin] makes "ano" and "año" different words. |
| `filters.evasion.normalise.look-alike-letters` | true or false | `true` | yes | Letters from other alphabets that look like a Latin letter (Cyrillic "с", Greek "ο" ...) are read as that Latin<br>letter when they stand inside a word that also has Latin letters ("fuсk"). The table is in filters/confusables.yml. |
| `filters.evasion.normalise.look-alike-letters-reverse` | true or false | `true` | yes | Also catch the opposite trick: Latin letters inside a word of another alphabet (a Russian word written with a Latin "x").<br>Only words that mix two alphabets are affected. |
| `filters.evasion.normalise.look-alike-whole-words` | true or false | `false` | yes | Also read words that are written COMPLETELY in look-alike letters of another alphabet as Latin words.<br>Off by default: on servers with Russian or Greek players a normal word can then look like a blocked Latin word. |
| `filters.evasion.normalise.leetspeak` | true or false | `true` | yes | Symbols and digits that stand for letters are read as letters ("sh1t", "@ss", "$hit"). The table is in filters/confusables.yml. |
| `filters.evasion.normalise.ignored-separators` | text | (empty) | yes | Characters that are skipped when they stand between two letters, so "fu.ck" or "fu-ck" is read as one word.<br>Example: ".-_". Empty by default because it also glues together things like "e.g" or "well-known".<br>Related settings in config.yml: filters.matching.collapse-repeated-letters, detect-spaced-letters, join-adjacent-words. |
| `filters.evasion.detect.action` | one of: off, log-only, censor, block | `log-only` | yes | What happens with a message that uses a bypass trick (see the two switches below), even when no blocked word<br>was found in it:<br>  off      = nothing<br>  log-only = the message goes through; the console and the event log note it<br>  censor   = the hidden characters are removed and look-alike letters are replaced by normal ones<br>             (chat on Bukkit/Spigot/Paper/Purpur; everywhere else the message is blocked instead)<br>  block    = the message is not delivered |
| `filters.evasion.detect.invisible-characters` | true or false | `true` | yes | Notice an invisible character between two letters of a word. |
| `filters.evasion.detect.mixed-alphabets` | true or false | `true` | yes | Notice a word that mixes Latin letters with look-alike letters of another alphabet. |
| `filters.evasion.detect.scripts` | list of texts | `latin`, `cyrillic`, `greek` | yes | An invisible character only counts as a trick between two letters of these writing systems. Languages such as<br>Persian, Arabic and Hindi (and emoji) need invisible joiners for correct spelling, so they are not in the list. |
| `filters.evasion.detect.sources` | list of texts | `chat`, `command`, `sign`, `book`, `anvil` | yes | Text sources this applies to: chat, command, sign, book, anvil, join, command-block (or "*" for all). |
| `filters.evasion.detect.strike-weight` | whole number from 0 to 100 | `0` | yes | How many strikes a noticed trick adds. 0 = none (the player is never punished for it). |
| `filters.evasion.detect.message` | text | `&cPlease write without invisible or look-alike letters.` | yes | Sent to the player when the message was blocked for a bypass trick. Empty = no message. Colours: & codes. |

## `filters/confusables.yml`

### Look-alike tables

| Key | Type | Default | Per world | What it does |
|-----|------|---------|:---------:|--------------|
| `leet-symbols` | text | `@=a $=s !=i \|=i +=t` | no | Symbols that stand for a letter and may appear inside a word. Form: <symbol>=<letter>, separated by spaces. |
| `leet-symbols-not-at-word-start` | text | `@!\|+` | no | Leetspeak symbols that are NOT read as a letter at the very start of a word ("+1", "!help", "@Steve"). |
| `leet-symbols-not-at-word-end` | text | `!\|+` | no | Leetspeak symbols that are NOT read as a letter at the very end of a word ("wow!"). |
| `leet-digits` | text | `0=o 1=i 3=e 4=a 5=s 7=t 8=b 9=g` | no | Digits that stand for a letter. They are only read as letters inside a word that also has Latin letters. |
| `look-alikes` | section (your own keys) | see the file | no | Letters that look like a plain Latin letter. Each line holds items of the form <letter>=<what it looks like>,<br>separated by spaces. The group names (cyrillic, greek ...) are only for you; add your own lines or groups.<br>Letters are read as the Latin letter only inside a word that also has Latin letters. One group is special:<br>the letters under "always" (small capitals) are read as the plain letter everywhere.<br>The default table was made from the Unicode confusables data (Unicode License v3, see NOTICE).<br>If this part is empty or cannot be read, the built-in table is used.<br>This part cannot be edited in the in-game menu; edit the file. |

## `moderation/history.yml`

### History

| Key | Type | Default | Per world | What it does |
|-----|------|---------|:---------:|--------------|
| `history.enabled` | true or false | `true` | no | Write a line for every moderation event (violation, punishment, mute ...) to data/history/YYYY-MM.jsonl,<br>one file per month. Staff with the permission antibadwords.history read it with /abw history.<br>The history never contains the chat text unless you switch store-message-text on. |
| `history.kinds` | list of texts | `*` | no | The kinds of event that are written. * = all of them. Kinds: violation, punishment, spam, link, evasion,<br>mute, unmute, mute-expired, admin-command. Example: [violation, punishment, mute] |
| `history.store-message-text` | true or false | `false` | no | Also write the text the player typed. OFF by default for privacy: chat can contain personal things.<br>Only turn this on when your rules and your players' privacy expectations allow it. |
| `history.redact-words` | true or false | `true` | no | Only used when store-message-text is on: the bad word is replaced by stars (same length) in the stored text.<br>When the word cannot be found in the text (disguised spelling), no text is stored at all. |
| `history.retention-days` | whole number from 0 to 36500 | `90` | no | Monthly files that are completely older than this many days are deleted. 0 = keep forever.<br>The unit is a whole month: a file goes when its last day is older than this. |
| `history.max-total-size-mb` | whole number from 0 to 100000 | `50` | no | When all history files together are bigger than this, the oldest months are deleted. If only the current<br>month is left and it is still too big, new events are not written until it is below the limit. 0 = no limit. |
| `history.page-size` | whole number from 1 to 50 | `8` | no | How many lines /abw history shows per page. |

## `moderation/mute.yml`

### Mute

| Key | Type | Default | Per world | What it does |
|-----|------|---------|:---------:|--------------|
| `mute.enabled` | true or false | `true` | no | Switches the built-in mute on. When off, /abw mute and the ladder action mute do nothing and nobody is held back.<br>Players who are already muted stay in data/mutes.json and are muted again when you switch this back on. |
| `mute.default-duration` | time such as 30s, 10m, 2h or 1d, at least 1s, at most 365d | `1h` | no | How long /abw mute <player> lasts when no time is given. (Use the word perm for a permanent mute.) |
| `mute.block.chat` | true or false | `true` | no | A muted player's chat messages are stopped. |
| `mute.block.commands` | list of texts | `msg`, `tell`, `w`, `whisper`, `r`, `reply`, `t`, `pm`, `m`, `say`, `me`, `teammsg`, `tm` | no | Commands a muted player may not use (without the slash, upper/lower case does not matter, a prefix such as minecraft: is ignored).<br>A * is a wildcard: "*" = every command, "msg*" = everything that starts with msg. [] = none. |
| `mute.block.signs` | true or false | `false` | no | A muted player cannot write text on signs. |
| `mute.block.books` | true or false | `false` | no | A muted player cannot sign or edit books. |
| `mute.check-every` | time such as 30s, 10m, 2h or 1d, at least 1s, at most 1h | `30s` | no | How often the server looks for mutes that have run out (to tell the player and publish the mute-expired event).<br>A muted player is never held back longer than the mute lasts, whatever this is set to. |
| `mute.notify-on-join` | true or false | `true` | no | A player who is still muted gets the message mute.messages.join when they join. |
| `mute.no-reason-text` | text | `no reason given` | no | Used for {reason} when a mute has no reason. |
| `mute.messages.blocked` | text | `&cYou are muted. Time left: {remaining}. Reason: {reason}` | no | Sent when a muted player tries to chat or use a blocked command. Placeholders: {player} {reason} {remaining} {duration} {by}. Colours: & codes. |
| `mute.messages.join` | text | `&cYou are still muted. Time left: {remaining}. Reason: {reason}` | no | Sent when a muted player joins. Placeholders: {player} {reason} {remaining} {duration} {by}. Colours: & codes. |
| `mute.messages.muted` | text | `&cYou have been muted for {remaining}. Reason: {reason}` | no | Sent to the player (when online) at the moment they are muted. Placeholders: {player} {reason} {remaining} {duration} {by}. Colours: & codes. |
| `mute.messages.unmuted` | text | `&aYou are no longer muted.` | no | Sent to the player (when online) when a staff member lifts the mute. Placeholders: {player} {reason} {remaining} {duration} {by}. Colours: & codes. |
| `mute.messages.expired` | text | `&aYour mute has run out. You can chat again.` | no | Sent to the player (when online) when the mute runs out. Placeholders: {player} {reason} {remaining} {duration} {by}. Colours: & codes. |

## `discord.yml`

### Discord

| Key | Type | Default | Per world | What it does |
|-----|------|---------|:---------:|--------------|
| `discord.enabled` | true or false | `false` | no | Master switch. false = nothing is ever sent to Discord (default). |
| `discord.mode` | one of: auto, webhook, discordsrv, off | `auto` | no | How alerts reach Discord:<br>  auto       = DiscordSRV when it is installed (Bukkit family only), otherwise the webhooks below<br>  webhook    = always the webhooks below (works on every server type)<br>  discordsrv = only through DiscordSRV (Bukkit family only; ignored by Fabric and Forge)<br>  off        = nothing is sent |
| `discord.webhook.alerts-url` | secret text (never shown) | `(not set)` | no | SECRET. The webhook URL of your alerts channel (Discord: channel settings > Integrations > Webhooks).<br>Anyone with this URL can post in your channel, so keep this file private. Better: write ${ENV:ABW_ALERTS_URL}<br>and set that environment variable on the server. Empty = no alerts channel. |
| `discord.webhook.log-url` | secret text (never shown) | `(not set)` | no | SECRET. Optional second webhook for a quiet log channel (see routing.log). Same rules as the alerts<br>webhook, ${ENV:NAME} works too. Empty = no log channel. |
| `discord.webhook.username` | text | `AntiBadWords` | no | The name shown for the messages in Discord. Empty = the name set in the webhook. |
| `discord.routing.alerts` | list of texts | `punishment`, `mute`, `unmute`, `evasion`, `admin-command` | no | Event kinds: violation, punishment, mute, unmute, spam, link, evasion, admin-command. "*" = every kind. |
| `discord.routing.log` | list of texts | `*` | no | Same kinds as above; the default "*" logs everything. Only used when log-url is set. A kind can go to both. |
| `discord.style` | one of: embed, plain | `embed` | no | embed = a coloured card per event, plain = one line of text per event. |
| `discord.mention` | text | (empty) | no | Written exactly as Discord wants it, for example <@&123456789> (a role) or <@123456789> (a person); empty = nobody.<br>Only @everyone, @here, roles and users that appear here can ever be pinged; names typed by players never can. |
| `discord.mention-kinds` | list of texts | `punishment` | no | Which kinds add the mention (the severe ones). Same kinds as above, "*" = all. |
| `discord.queue.size` | whole number from 10 to 10000 | `500` | no | How many messages may wait per webhook. When it is full the OLDEST waiting message is dropped (with a warning).<br>The server never waits for Discord. |
| `discord.rate.min-interval` | time such as 30s, 10m, 2h or 1d, at least 0s, at most 1h | `1s` | no | Shortest time between two requests to the same webhook (Discord allows about 5 per 2 seconds; 429 answers are<br>also honoured). |
| `discord.rate.batch-size` | whole number from 1 to 10 | `5` | no | Up to this many events are sent in one request (embed style: up to 10 cards; plain style: lines). |
| `discord.rate.batch-wait` | time such as 30s, 10m, 2h or 1d, at least 0s, at most 10m | `2s` | no | After the first waiting event the sender waits this long for more before sending. 0s = send at once. |
| `discord.http.timeout` | time such as 30s, 10m, 2h or 1d, at least 1s, at most 2m | `10s` | no | How long one request may take. |
| `discord.http.retries` | whole number from 0 to 10 | `3` | no | How often a failed request (no connection, 5xx, 429) is repeated before the batch is given up. |
| `discord.http.backoff` | time such as 30s, 10m, 2h or 1d, at least 0s, at most 10m | `2s` | no | Wait before the first retry; it doubles every time (2s, 4s, 8s ...). |
| `discord.http.shutdown-wait` | time such as 30s, 10m, 2h or 1d, at least 0s, at most 1m | `5s` | no | When the server stops, waiting messages are sent right away; this is the longest the shutdown waits for that. |
| `discord.colors.violation` | text | `#F1C40F` | no | Colour of the violation card (embed style) as #RRGGBB. |
| `discord.colors.punishment` | text | `#E74C3C` | no | Colour of the punishment card (embed style) as #RRGGBB. |
| `discord.colors.mute` | text | `#E67E22` | no | Colour of the mute card (embed style) as #RRGGBB. |
| `discord.colors.unmute` | text | `#2ECC71` | no | Colour of the unmute card (embed style) as #RRGGBB. |
| `discord.colors.spam` | text | `#3498DB` | no | Colour of the spam card (embed style) as #RRGGBB. |
| `discord.colors.link` | text | `#9B59B6` | no | Colour of the link card (embed style) as #RRGGBB. |
| `discord.colors.evasion` | text | `#1ABC9C` | no | Colour of the evasion card (embed style) as #RRGGBB. |
| `discord.colors.admin-command` | text | `#95A5A6` | no | Colour of the admin-command card (embed style) as #RRGGBB. |
| `discord.templates.violation` | text | `{player} used a blocked word in {source}: {word} ({count}/{limit}, {action})` | no | Text for violation events. Placeholders: {player} {word} {source} {world} {count} {limit} {action}<br>and {kind} {check} {detail} {step} {reason} {duration} {command} {text} (empty when it does not apply). |
| `discord.templates.punishment` | text | `{player} was punished ({action}) at {count} strikes, step {step}` | no | Text for punishment events. Placeholders: {player} {word} {source} {world} {count} {limit} {action}<br>and {kind} {check} {detail} {step} {reason} {duration} {command} {text} (empty when it does not apply). |
| `discord.templates.mute` | text | `{player} was muted for {duration}: {reason}` | no | Text for mute events. Placeholders: {player} {word} {source} {world} {count} {limit} {action}<br>and {kind} {check} {detail} {step} {reason} {duration} {command} {text} (empty when it does not apply). |
| `discord.templates.unmute` | text | `{player} was unmuted` | no | Text for unmute events. Placeholders: {player} {word} {source} {world} {count} {limit} {action}<br>and {kind} {check} {detail} {step} {reason} {duration} {command} {text} (empty when it does not apply). |
| `discord.templates.spam` | text | `{player} triggered the spam filter ({check}: {detail}) in {source}, action {action}` | no | Text for spam events. Placeholders: {player} {word} {source} {world} {count} {limit} {action}<br>and {kind} {check} {detail} {step} {reason} {duration} {command} {text} (empty when it does not apply). |
| `discord.templates.link` | text | `{player} posted a link in {source}: {detail} (action {action})` | no | Text for link events. Placeholders: {player} {word} {source} {world} {count} {limit} {action}<br>and {kind} {check} {detail} {step} {reason} {duration} {command} {text} (empty when it does not apply). |
| `discord.templates.evasion` | text | `{player} tried to dodge the filter in {source}: {detail} (action {action})` | no | Text for evasion events. Placeholders: {player} {word} {source} {world} {count} {limit} {action}<br>and {kind} {check} {detail} {step} {reason} {duration} {command} {text} (empty when it does not apply). |
| `discord.templates.admin-command` | text | `{player} ran /{command}` | no | Text for admin-command events. Placeholders: {player} {word} {source} {world} {count} {limit} {action}<br>and {kind} {check} {detail} {step} {reason} {duration} {command} {text} (empty when it does not apply). |

## The extra filters in plain words

A message goes through the checks in the order of `filters.check-order` (config.yml):
`evasion`, `badword`, `link`, `spam`. The first check that **blocks** ends the search. A check that **cleans**
(action `censor`) hands the cleaned text to the next check. `log-only` only notes it.

| Check | What it catches | Default |
|-------|-----------------|---------|
| `badword` | words from the word lists - also when written with invisible characters, look-alike letters, full-width letters, accents or leetspeak (the "normalise" part of `filters/evasion.yml`) | on, as always |
| `evasion` | the trick itself: an invisible character inside a word, or a word that mixes two alphabets | `log-only` |
| `link` | links, hidden links (`example dot com`, `example[.]com`), IP addresses, invite links | off (`mode: off`) |
| `spam-rate`, `spam-repeat`, `spam-caps`, `spam-flood`, `spam-words`, `spam-commands` | too many messages, repeated messages, CAPITALS, floods, repeated words, too many commands | off (`filters.spam.enabled: false`) |

* Each of them has its own action, message, strike weight (0 = never punishes) and list of sources.
* **"censor" needs a server that can change the text.** That is chat on Bukkit, Spigot, Paper and Purpur.
  Everywhere else (signs, books, anvils, commands, and everything on Fabric and Forge) a message that would be
  cleaned is blocked instead, and the player gets the rule's message.
* `/abw check <text>` shows which check would fire and why. The three rules that count messages over time
  cannot be tested that way.
* Updating from an older version changes nothing by itself: spam and links are off, `evasion` only notes, and
  your `scanned-commands` list is kept exactly as it was (new installs scan a few more commands).

More places that can be scanned (switches under `filters.scan` in config.yml):

| Source | Switch | Bukkit / Spigot / Paper / Purpur | Fabric / Forge |
|--------|--------|----------------------------------|----------------|
| Player name on join | `join-names` (off) + `filters.join-names.action` log / warn / kick | yes | yes (a kick uses the `/kick` command) |
| Back side of signs | `sign-back` (on) | yes, own switch | both sides are always scanned with `signs`; the extra switch does nothing yet |
| Command blocks | `command-blocks` (off) | yes: checked when the block **runs** the command (Bukkit has no event for editing one); nobody gets strikes | **not available yet** |

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
| `/abw check <text>` | would this text be blocked? Names the check that fires (badword, link, spam-caps ...) and why |
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
| `antibadwords.bypass.spam` | nobody | Exempt from the spam and flood rules |
| `antibadwords.bypass.links` | nobody | Exempt from the link filter |
| `antibadwords.bypass.evasion` | nobody | Exempt from the bypass-trick detection (blocked words are still found) |
| `antibadwords.profile.<name>` | nobody | Puts the player on that `worlds.yml` profile |
