# Discord guide: alerts, DiscordSRV, commands from Discord

*Verified: engine unit tests (against a local fake web server and a fake DiscordSRV bridge). The plugin code was compiled against the real DiscordSRV names (every name is in `tools/api-manifest.txt`). It was NOT run on a real server and NOT tested with a real Discord bot.*

## The idea in five sentences
Moderra can tell your staff on Discord when something happens: a bad word, a punishment, a mute, spam, a link.
There are two ways. A **webhook** is a secret web address for one Discord channel; it works on all four server types.
**DiscordSRV** is a separate Bukkit plugin that links your server to a Discord bot; Moderra can use it (Bukkit, Spigot, Paper, Purpur only).
With DiscordSRV you can also filter what people type **in Discord** before it reaches Minecraft, and run a few staff commands from Discord (typed `!moderra` commands or slash commands).
Everything is off until you set `discord.enabled: true` in `discord.yml`.

## 1. Webhook alerts (all server types)
1. In Discord: channel settings, Integrations, Webhooks, New Webhook, Copy Webhook URL.
2. Open `discord.yml`. Set `discord.enabled: true`.
3. Paste the URL into `discord.webhook.alerts-url` (staff alerts). A second channel for a full log: `discord.webhook.log-url`.
4. Run `/moderra reload`, then `/moderra discord test`. A test message should appear in the channel.

**Keep the URL secret.** Anyone who has it can post in your channel. Better than pasting it into the file:
write `${ENV:MODERRA_ALERTS_URL}` as the whole value and set the environment variable `MODERRA_ALERTS_URL` on the server.
The plugin never prints the URL: not in the log, not in `/moderra discord`, not in the GUI, not in history. If the file holds a literal URL and other users on the machine can read it, you get a warning.
Only `https://` URLs are accepted (plain `http://` only for localhost).

| Setting | What it does |
|---|---|
| `discord.mode` | `auto` (use DiscordSRV when found, else webhook), `webhook`, `discordsrv`, `off` |
| `discord.routing.alerts` / `discord.routing.log` | which kinds go to which webhook. Kinds: `violation`, `punishment`, `mute`, `unmute`, `spam`, `link`, `evasion`, `admin-command`. `*` = all |
| `discord.style` | `embed` (coloured card) or `plain` |
| `discord.colors.<kind>` | colour per kind, like `#E74C3C` |
| `discord.templates.<kind>` | the text. Placeholders: `{player} {word} {source} {world} {count} {limit} {action} {kind} {check} {detail} {step} {reason} {duration} {command} {text}`. The defaults are plain sentences (formal style, no colour codes), for example `{player} cannot write for {duration}. Reason: {reason}.` The card title or the `[kind]` tag in plain style names the event. |
| `discord.mention` / `discord.mention-kinds` | ping a role or user for chosen kinds (alerts webhook only) |
| `discord.queue.size`, `discord.rate.*`, `discord.http.*` | queue size, rate limits, timeouts and retries |

Safety built in: text typed by players cannot ping anybody (`@` is defused, only the mention you wrote is allowed). Messages are queued, batched and slowed down so Discord rate limits are respected; the server never waits for Discord.

## 2. DiscordSRV mode (Bukkit family only)
Install DiscordSRV as usual. Moderra finds it by name at start and logs what it connected.
With `discord.mode: auto` or `discordsrv`, alerts go to DiscordSRV channels instead of webhooks:
`discord.discordsrv.alert-channel` (default `alerts`) and `discord.discordsrv.log-channel`. These are the **channel names from DiscordSRV's `channels` setting**, not Discord IDs.
If a channel name does not exist you get a console warning (never the message text).
If DiscordSRV is missing or fails, one log line is written and the rest of the plugin works as before.

### Filter what people type in Discord
`discord.inbound.enabled: true` checks messages from Discord before they appear in Minecraft chat.
* `discord.inbound.action`: `block` (message is not shown in game), `censor` (the words are replaced; if that does not work on your DiscordSRV version it blocks and writes one log line), `log-only`.
* `discord.inbound.channels` and `except-channels`: which Discord channels (id or name, `*` = all).
* `discord.inbound.bypass-roles` and `bypass-users`: people who are never filtered.
* `discord.inbound.notify-sender`: send the person a private message with a fixed text (it never repeats the word).
* No strikes or punishments: there is no Minecraft player to punish. One `violation` event with source `discord-in` goes to the alerts instead.
* The link and bypass-trick filters also look at Discord messages (`discord-in` is in their `sources` lists for new installs).

### Commands from Discord (`!moderra` text commands and slash commands)
Everything is off by default.
1. `discord.commands.channels`: where the bot listens (empty = nowhere).
2. `discord.commands.allowed-roles` and `allowed-users`: who may use them (both empty = nobody). A person on no list gets no answer to a typed `!moderra` line.
3. Switch on each command you want: `discord.commands.mute.enabled`, `unmute`, `history`, `strikes`, `check`, `reload`.
4. Choose the style with `discord.commands.style`: `prefix` (default: typed `!moderra ...` lines), `slash` (only slash commands) or `both`.
5. Typed commands: `!moderra mute Steve 10m spam`, `!moderra unmute Steve`, `!moderra history Steve`, `!moderra strikes Steve`, `!moderra check some text`.
6. Slash commands: `/moderra-mute player [details]`, `/moderra-unmute player`, `/moderra-history player [page]`, `/moderra-strikes player`, `/moderra-check text`, `/moderra-reload`.
   They are registered with DiscordSRV when it starts, so **restart the server** after you change the style. Discord can take up to a minute to show them.

Both ways use the same code: command channel, allow-list, per-command switch, one shared rate limit (`discord.commands.rate.max` per `discord.commands.rate.window`) and one `admin-command` event (who, which Discord channel).
A slash command must always be answered. A refused call (style `prefix`, channel not listed, person not allowed) gets one neutral answer, "[Moderra] Error. You cannot use this command here.", that does not tell which rule refused it.
Other answers start with `[Moderra]` and a key word (`Error.`, `Notice.`, `Info.`, `Done.`). The command runs with only the one permission it needs. Player names are checked against known players. `history purge` and `history recent` are refused. Answers are cleaned of colour codes and nobody can be pinged.

## 3. Limits (honest)
* DiscordSRV features (mode `discordsrv`, inbound filter, `!moderra`) exist only on Bukkit, Spigot, Paper and Purpur. Fabric and Forge have webhooks only. `/moderra info` says so.
* Slash commands need a DiscordSRV 1.x that shades JDA 4.x. The reply path uses `Interaction.reply(String)` by reflection (the shaded JDA is not a build dependency). If a name is missing you get one warning and Discord shows "application did not respond".
* DiscordSRV method names used for sending, slash commands and censoring were read from an API dump, never run with a real bot. If one is missing you get a single warning line.
* You can use both ways: `discord.mode: webhook` keeps DiscordSRV only for commands and filtering.
