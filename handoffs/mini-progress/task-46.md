# Task 46 (wave 6): DiscordSRV slash commands (F) and network follow-ups (J)

Branch `mini/46`. Verification level: engine unit tests run; plugin compiled with `tools/stub-compile.sh plugin` (PASS) against the
name-only DiscordSRV shims. Nothing ran against a real DiscordSRV, bot or proxy.

## F. DiscordSRV slash commands

Done (Bukkit family, any DiscordSRV 1.x that shades JDA 4.x):
- Setting `discord.commands.style: prefix | slash | both` (default `prefix`, the old behaviour), in `discord.yml`.
- Commands `/abw-mute player [details]`, `/abw-unmute player`, `/abw-history player [page]`, `/abw-strikes player`, `/abw-check text`,
  `/abw-reload`. Top-level commands (no sub-commands) so only `CommandData(String,String)` and `addOption(OptionType,String,String,boolean)` are needed.
- Engine: `DiscordLink.onSlashCommand(Incoming, word, arguments, reply)` reuses the prefix path (`runCommand`): command channel list, allow-list
  (roles and users), per-command switch, rate limit (shared with the prefix commands), argument validation, one `admin-command` audit event with the
  Discord user and channel. A refused call (style prefix, hook off, channel not listed, user not allowed) gets ONE neutral answer
  (`DiscordLink.SLASH_REFUSED`) that does not say why, because a slash command must always be answered. Prefix commands still give no answer to
  an unlisted user (unchanged). With style `slash` a typed `!abw ...` line is an ordinary message.
- Plugin: `DiscordSrvSlashCommands` (implements the real `SlashCommandProvider`, six `@SlashCommand` methods taking `SlashCommandEvent`),
  shims in `2-plugin-bukkit-purpur/src/discordsrv-shim/java` (annotation, priority enum, provider, `PluginSlashCommand(Plugin,CommandData)`,
  `CommandData`, `OptionType.STRING`, `SlashCommandEvent`); the jar exclude `github/**` already covers them, no pom change. `DiscordSrvHook`
  registers the provider only when the style is `slash` or `both`, and reads the event by reflection.
- All six `@SlashCommand` members (`path`, `priority`, `deferReply`, `deferEphemeral`, `ignoreAcknowledged`) are set explicitly, so the code does not
  depend on the defaults of the installed version.
- Tests: `DiscordSlashTest` (6 tests). Manifest section "task 46" added to `tools/api-manifest.txt`.

Open items (exact reason):
1. The `ApiManager` method that registers a `SlashCommandProvider` is NOT in the dump (`ApiManager` was not requested). The hook finds it by
   parameter type (a public one-argument method whose name contains "slash" and starts with "add", parameter type implemented by our class), and calls
   `subscribe(provider)` too (verified API). If none is found: one warning, slash commands off, prefix commands unaffected. Added `ApiManager` and
   the interaction classes to `tools/plugin-dump-classes.txt` for the next dump run.
2. The reply path uses `reply(String)` on the event, `setEphemeral(boolean)` on its result (`ReplyAction`, verified) and `queue()`, plus `getUser()`,
   `getMember()` on the event. `GenericInteractionCreateEvent`, `CommandInteraction`, `Interaction` were not in the dump, so these names are looked up
   by reflection at run time. If `reply` is missing: one warning and the answer goes into the channel (Discord then shows "application did not respond").
3. In-game test an admin must do: set `discord.commands.style: both`, the command switches, allowed roles and a command channel; restart the server
   (slash commands are registered when DiscordSRV starts); run `/abw-check text:hello`. Expect an ephemeral reply. A user not in the allow-list must get
   "This command is not available to you here.".
4. Discord registers slash commands per guild and can take up to a minute to show them; the style setting needs a restart (not a reload).

## J. Network

Done:
- Start-up bridge check (mode `plugin-messaging`): after start the server sends one `hello` record; every other server that gets it live answers
  with an `ack`. If no other server was seen within `integrations.network.check-timeout` (default 90 s) after the hello left the queue (the wait
  starts when a player is online and the message was sent), ONE warning names the causes (proxy BungeeCord plugin message channel off, for Velocity
  `bungee-plugin-message-channel`; proxy does not forward `antibadwords:sync`; no other server runs the plugin). A passing check logs one info line.
  Setting `integrations.network.check-bridge` (default true: a pure diagnostic) and `check-timeout`. Old versions ignore `hello`/`ack` (unknown kind).
  `NetworkMessaging.java` needed no change.
- Shared-folder journal compaction: setting `integrations.network.journal-max-kb` (default 0 = never, old behaviour). When this server's own journal is
  larger, the part that every other recent server has read is replaced by a summary (newest mute/unmute and newest reset per player; expired mutes
  dropped; strikes are not kept because every reader that is past that point has them already). The rewrite goes to `sync-<id>.jsonl.tmp` and replaces
  the journal with one atomic move. Read-position fix-up: positions are LOGICAL; the first line `#abw-journal base=<B> prefix=<S>` maps them, so the
  `read-by-<id>.txt` files of the other servers need no rewrite and stay valid. A reader below `B` reads the summary first. A reader that has no
  entry yet counts as position 0, so nothing is dropped before it read. Read files older than 7 days are not waited for. A journal without the header
  reads as before (old format). A server that was behind the cut (removed for more than 7 days) loses only old strikes, never mutes.
- Tests: `NetworkJournalTest` (7 tests: two and three engines, strikes of a slow reader, old journal without header, expired mutes and unread lines in
  the summary, bridge check warn once / pass / off). Two existing assertions in `NetworkSyncTest` and `NetworkMessagingTest` now count the hello
  record (1 became 2).
- All servers must run this version before `journal-max-kb` is switched on (older readers would misread the logical positions).

Not possible / left: nothing in this task; the bridge check and compaction were only tested with fake platforms and temp folders (not on a real proxy or
a network share; on Windows the atomic move can fail while another server reads the file: one warning, the next write retries).

## Docs text for task 48

- CONFIGURATION.md is generated (run `generate-docs.sh` after merging).
- README, Discord section: slash commands as above, style setting, restart needed.
- README, network section: bridge check and journal size limit.

## Changelog lines (2.1.0)

- DiscordSRV slash commands `/abw-mute`, `/abw-unmute`, `/abw-history`, `/abw-strikes`, `/abw-check`, `/abw-reload` with the same allow-list, rate limit and audit as the `!abw` prefix commands (`discord.commands.style`, default `prefix`).
- Network sync: start-up check of the plugin messaging bridge (`integrations.network.check-bridge`).
- Network sync: size limit and safe compaction of the shared-folder journal (`integrations.network.journal-max-kb`, default off).

## Files

Engine: `engine/discord/DiscordLink.java`, `DiscordSrvSettings.java`, `engine/network/{NetworkSettings,NetworkSync,SharedFolderChannel,SyncRecord}.java`.
Plugin: `DiscordSrvHook.java`, new `DiscordSrvSlashCommands.java`, new shim files under `src/discordsrv-shim/java/github/scarsz/discordsrv/{api/commands,dependencies/jda/api/...}`.
Tools: `tools/api-manifest.txt` and `tools/plugin-dump-classes.txt` (appended). Tests: `DiscordSlashTest`, `NetworkJournalTest`.
