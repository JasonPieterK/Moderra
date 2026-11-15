# Integrations guide: other plugins

All settings are in `integrations.yml` (every key is explained in [CONFIGURATION.md](CONFIGURATION.md)). Every integration is optional: if the other plugin is missing, nothing happens. If something fails, one log line is written and the rest keeps working. `/abw info` ends with "Integrations detected: ...".

**Verified levels used below**
* **Engine tested**: the logic has unit tests (run with `mvn test`).
* **Real API names**: the code was compiled on GitHub against the real jar (or its names were checked with `verify-apis.yml`: 64 names, 0 failures).
* **Not run**: never started on a real server with that plugin. This is true for EVERY integration in this guide.

Where not stated otherwise: Bukkit, Spigot, Paper, Purpur only. Fabric and Forge ignore these settings (they cannot see other plugins yet).

---
## EssentialsX and EssentialsX Chat
**What it does:** Chat is checked before EssentialsX Chat formats it, so a blocked message never reaches it and a censored message is formatted clean. Blocked `/msg`, `/r`, `/mail`, `/me`, `/broadcast`, `/helpop`, `/nick` text never reaches social spy, mail or helpop. Nicknames are checked when a player joins (warn or reset). Players hidden by `/vanish` are not named in public broadcasts. `/mute` of Essentials can be shared with the built-in mute.
**Settings (`integrations.essentials.*`):** `enabled`, `chat-priority` (`auto` = lowest when EssentialsX Chat exists), `mute-mode` (`own`, `essentials`, `both`), `nickname-in-messages` (`off`, `nickname`, `both`), `hide-vanished`, `scan-commands`, `check-nickname-on-join`, `nickname-action` (`log`, `warn`, `reset`), `nickname-message`.
**Verified:** engine tested. Essentials classes are found by reflection (`getUser`, `isMuted`, `setMuted` ... checked by name on GitHub). Not run.
**Limits:** other plugins that listen to commands at a lower priority may still see a blocked command. `plugin.yml` uses `loadbefore: [EssentialsChat]` so our handler is registered first.

## PlaceholderAPI
**What it does:** Adds placeholders you can use in scoreboards, tab lists and holograms. Optionally reads other plugins' placeholders in AntiBadWords messages.
**Placeholders** (all `%antibadwords_<name>%`): `strikes`, `strikes_next` (strikes at the next ladder step), `next_step` (name of the next step), `muted` (true/false), `mute_remaining`, `mute_reason`, `profile`, `violations_total`, `protection_level`. They never contain chat text, word lists or secrets. `violations_total` is cached for 30 seconds; the first call can show 0.
**Settings:** `integrations.placeholderapi.enabled`, `.parse-in-messages` (default off, because a player could type `%something%`; only messages to one player are parsed, never broadcasts).
**Verified:** engine tested; expansion class compiled against the real PlaceholderAPI names. Not run.
**Limits:** if another jar already registered `antibadwords`, you get a log line.

## LuckPerms
**What it does:** Chooses a rule profile (`worlds.yml`) by group or meta, and knows permissions of offline players (for example to warn that someone you mute has `antibadwords.bypass.mute`).
**Profile order:** permission `antibadwords.profile.<name>`, then LuckPerms meta, then the group map, then the chat channel, then world rules, then the default.
**Settings (`integrations.luckperms.*`):** `enabled`, `use-meta`, `meta-key` (`antibadwords-profile`), `primary-group-only`, `offline-permission-checks`, `group-profiles` (group to profile).
**Examples:** `/lp group vip meta set antibadwords-profile relaxed` or `/lp group vip permission set antibadwords.profile.relaxed true` (the permission way also works on Fabric and Forge).
**Verified:** compiled against the real LuckPerms API 5.4 (Maven Central). Not run.
**Limits on Fabric/Forge:** only the permission nodes work (LuckPerms for Fabric/Forge answers them). No meta, no group map, no offline checks.

## Vault
**What it does:** Without LuckPerms, Vault gives the player's group (for `group-profiles`) and prefix. `{prefix}` and `{group}` work in warning, broadcast and staff messages.
**Settings:** `integrations.vault.enabled`, `.message-placeholders`.
**Verified:** reflection only (Vault is not on Maven Central); method names from memory. If one is wrong that part switches off with one log line. Not run.
**Limits:** online players only; no meta, no offline checks.

## Ban plugins (ladder actions `tempban` and `ipban`)
**What it does:** The ladder actions `tempban <time> [reason]` and `ipban [reason]` run the console command of the ban plugin found on the server. Order when `provider: auto`: LiteBans, AdvancedBan, LibertyBans, EssentialsX, else vanilla. If the plugin cannot do a timed ban, `fallback` (`kick`, `mute`, `none`) is used with a warning.
**Settings (`integrations.bans.*`):** `provider`, `fallback`, `duration-format` (`compound` like `1d2h30m`, `single` like `90m`), and for each provider `<provider>.tempban` and `.ipban` templates. Empty = this provider cannot do it.
**Templates (defaults):**
| Provider | tempban | ipban |
|---|---|---|
| EssentialsX | `tempban {player} {duration} {reason}` | `banip {ip} {reason}` |
| LiteBans | `tempban {player} {duration} {reason} -s` | `ipban {ip} {reason} -s` |
| AdvancedBan | `tempban {player} {duration} {reason}` | `ipban {ip} {reason}` |
| LibertyBans | `tempban {player} {duration} {reason}` | `ipban {ip} {reason}` |
| vanilla | (none, vanilla has no timed ban) | `ban-ip {ip} {reason}` |
Placeholders: `{player} {uuid} {reason} {duration} {seconds} {millis}`; ipban also `{ip}`: the online IP, else the last stored IP, else the name (with a warning).
**Privacy:** the last IP is stored in `data/players.json` only for this. Turn it off with `known-players.store-ip: false`.
**Verified:** engine tested. The command syntax of the four ban plugins is from memory and not checked: confirm that each template works on your server. Not run.
**Limits:** Fabric and Forge always use vanilla.

## Floodgate and Geyser (Bedrock players)
**What it does:** Bedrock names carry a prefix (default `.`) and may contain spaces. The prefix is removed before the name check, `{player}` in ban commands has spaces replaced, and `/abw` finds players with or without the prefix.
**Settings (`integrations.bedrock.*`):** `mode` (`auto` = on when Floodgate or Geyser is found, `on`, `off`), `prefix`, `strip-prefix-before-filter`, `space-replacement`.
**Verified:** engine tested; detection by plugin name. Not run.

## Chat-channel plugins (ChatControl, VentureChat, HuskChat, CarbonChat, TownyChat)
**What it does:** Gives a channel its own rule profile, and runs our chat check early (lowest priority) when one of these is installed.
**Settings (`integrations.chatchannels.*`):** `enabled`, `chat-priority`, `chatcontrol`, `venturechat`, `huskchat`, `carbonchat` (detection switches), `channel-events`, `channel-profiles` (channel to profile, for example `global: strict`, `staff: relaxed`), `channel-commands` (command to channel, for example `g: global`). Add the channel commands to `filters.scan.scanned-commands` to have them scanned.
**TownyChat:** the real TownyChat chat event is used (channel name, cancel, change text). Switch: `channel-events`.
**Others:** VentureChat's event cannot be cancelled and HuskChat's runs on the proxy, so they are not used; their chat still goes through the normal chat check. ChatControl, CarbonChat: no event hook (their jars were not available to check).
**Verified:** engine tested; TownyChat event compiled against the real jar. Not run.

## TAB and NametagEdit
**What it does:** While installed, their commands (`/tab ...`, `/nte ...`) are scanned like chat.
**Settings:** `integrations.tab.enabled` and `.scan-commands`, `integrations.nametagedit.enabled` and `.scan-commands`.
**Limits:** no events are used (they have none for text a player sets). Text in TAB's own config files is not scanned. Not run.

## ProtocolLib (packet scan)
**What it does:** When Bukkit events are missing for signs, books or anvil names, a ProtocolLib listener scans the packets. By default it is NOT used while the normal events exist (`packet-scan.only-when-events-missing: true`).
**Settings:** `integrations.protocollib.enabled`, `integrations.packet-scan.only-when-events-missing`.
**Verified:** real ProtocolLib API names checked on GitHub. Not run. Fields are read by type, which can differ per Minecraft version.
**Limits:** PacketEvents: only detected, no listener yet (`integrations.packetevents.enabled` does nothing more).

## CoreProtect
**What it does:** Blocked sign, sign-back and book text is recorded in CoreProtect, so `/co lookup` shows it as chat (or command) by that player.
**Settings:** `integrations.coreprotect.enabled`, `.log-as` (`chat`, `command`), `.include-text`, `.sources`.
**Verified:** reflection; the method names `getAPI`, `logChat`, `logCommand` are probed at start. If none exists: one warning, off. Not run.

## Plan
**What it does:** Adds a Plan data extension: violations, strikes and muted state per player.
**Settings:** `integrations.plan.enabled`.
**Verified:** compiled against name-only shims of the Plan API; the parameter convention of provider methods is unverified. Not run. You can also show `%antibadwords_violations_total%` through PlaceholderAPI.

## mcMMO, Towny, Factions, Citizens, DecentHolograms
**What it does:** The text of party, town, nation and faction chat commands, names, boards, NPC names and hologram lines is checked like chat. Needs `filters.scan.commands` on.
**Settings:** per plugin `integrations.<mcmmo|towny|factions|citizens|decentholograms>.enabled` and `.scan-commands`. An entry is a command plus optional sub-words (`npc rename`); a bare label (`tc`) scans every argument. Factions names detected: Factions, FactionsUUID, SaberFactions, FactionsKore.
**Verified:** engine tested. The default command lists are from memory and NOT checked against the plugins: edit them. Not run.
**Limits:** commands only. mcMMO party chat events are not hooked (the player class was not in the API dump), and FactionsUUID has no event hook. Chat sent through `AsyncPlayerChatEvent` is already covered by the normal chat check.

## Network sync (several servers)
**What it does:** Shares mutes, unmutes, strike points and strike resets between servers. Off by default.
**Settings (`integrations.network.*`):** `mode` (`off`, `shared-folder`, `plugin-messaging`), `folder` (default `../abw-network`), `server-id` (unique per server), `poll-interval`, `sync-mutes`, `sync-strikes`. `/abw network` shows peers and the last sync.
* **Shared folder:** every server reads and writes the same folder. No proxy needed. Each server writes its own journal file and reads the others.
* **Plugin messaging:** records travel as `Forward ALL` on channel `antibadwords:sync` through BungeeCord or Velocity (Velocity needs its BungeeCord bridge on). A player must be online to carry messages; otherwise records wait in a queue (500 max).
**Rules:** the newest mute change wins; strikes add up; a reset wins over older strikes. Keep the clocks of all servers in sync (NTP). Journals only grow: delete them when all servers are stopped. A new server does not inherit old strikes. Clearing all strikes is not synced.
**Verified:** engine tested with two engines on temp folders and the message encoding. The plugin glue is compiled against stubs only. Not tested on a real network.
