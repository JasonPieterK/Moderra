## Mini tasks 11 and 12: DiscordSRV hook (session 5, A2) - engine logic tested, Bukkit class stub-compiled only

Verification level: engine unit tests run (`mvn -q -B test`, 22 new tests in `engine/discord/DiscordLinkTest` against a fake `DiscordBridge`).
The Bukkit classes were compiled with `javac` against the hand-written Bukkit stub (extended with `Plugin#isEnabled/getDescription`,
`PluginManager#getPlugin`) plus the two DiscordSRV shim classes. NEVER run against a real DiscordSRV or Discord (its repository is blocked).
The `pom.xml` change was not run either (Spigot repo blocked).

### Engine (package `engine.discord`)
- `DiscordBridge` (interface the Bukkit plugin implements): `name()`, `sendToChannel(channelName, text)` (false = no such channel),
  `notifyUser(discordUserId, text)`, `canRewrite()`.
- `DiscordService.attachBridge(bridge)` returns a `DiscordLink`; `detachBridge()`, `link()`. While a link exists and `discord.mode` is `auto` or
  `discordsrv` (and `discord.enabled`), alerts go through the bridge and `suppressWebhooks` is applied automatically (re-evaluated on reload).
  Mode `webhook` = bridge sends no alerts. `hookEnabled()` = `discord.enabled` and mode not `off`; it gates the filter and the commands too.
  The `status` first line now also says whether DiscordSRV is connected (kept in line 1 on purpose: `DiscordWebhookTest` counts lines).
- `DiscordLink`: `onMessage(Incoming, reply)` is the one entry point (command first, else filter) returning `Decision` ALLOW / BLOCK / REPLACE.
  - Alerts (`onEvent`): routing by `discord.routing.alerts` to `discord.discordsrv.alert-channel` and `routing.log` to `log-channel`; same text as the
    webhook template; a missing channel gives a console warning (never the text).
  - Filter (source `discord-in`): `engine.inspect` with a stable fake UUID per Discord user (for spam counters only). NO strikes, NO `onViolation`
    (no player to punish); instead one `violation` event (source `discord-in`, player name `Discord:<name>`, data action block|censor|log-only|none,
    channel, discord-user, text). Only a verdict that blocks/censors is overridden by `discord.inbound.action`; hits that are just notes are delivered.
    `censor` becomes `block` unless the bridge `canRewrite()` (the Bukkit hook says false). Per-channel list + exception list (id or name, `*`),
    bypass role ids / user ids, `notify-sender` DM with a fixed text (never contains the word).
  - Commands: text prefix `discord.commands.prefix` (default `!abw`), NOT slash commands. Order of checks: prefix in a channel of `commands.channels`
    (empty = nowhere) -> user id in `allowed-users` or role in `allowed-roles` (both empty = nobody; unlisted people get no answer at all and nothing
    runs) -> rate limit (`rate.max` per `rate.window`) -> the command's own switch (`commands.<name>.enabled`, all false) -> argument check.
    Names: `[.]A-Za-z0-9_` max 16, must resolve through `engine.resolvePlayer`, then the exact known spelling is passed on; `history purge|recent` refused;
    free text has colour codes/control chars removed (max 256). The real `/abw` sub-command is run through `engine.commands()` on the server thread with
    a capturing sender that has ONLY the permission of that one command; answers are stripped of colour codes, `@` is neutralised, max 1800 chars.
    Audit: one `admin-command` event (source `discord`, player `Discord:<name>(<id>)`, data command, sender, discord-user, channel) BEFORE running.
    Known side effect: commands that call `c.audit()` themselves (mute/unmute) publish a second `admin-command` event with the same sender name.
- `DiscordSrvSettings` (`discord.yml`, category Discord): `discord.discordsrv.alert-channel` (alerts) / `log-channel`, `discord.inbound.enabled` (false),
  `.action`, `.channels` (`*`), `.except-channels`, `.bypass-roles`, `.bypass-users`, `.notify-sender`, `.notify-text`, `discord.commands.prefix`,
  `.mute|unmute|history|strikes|check|reload.enabled` (false), `.allowed-roles`, `.allowed-users`, `.channels`, `.rate.max` (5), `.rate.window` (30s).
  `link`/`evasion` filters only look at `discord-in` when it is added to their `sources` lists (documented in the setting).

### Bukkit (`2-plugin-bukkit-purpur`)
- `DiscordSrvHook` (reflection only): detects the plugin by name via the PluginManager, loads `github.scarsz.discordsrv.DiscordSRV` /
  `util.DiscordUtil` through DiscordSRV's own class loader, implements `DiscordBridge`, subscribes with `DiscordSRV.api.subscribe`, reads the pre-process event
  by reflection, cancels it for BLOCK. Any failure = log line and feature off; the plugin never fails because of DiscordSRV. Startup prints what is on/off.
  `AntiBadWordsPlugin` starts it at the end of `onEnable` (try/catch Throwable) and stops it first in `onDisable`.
- `DiscordSrvListener`: needed because DiscordSRV finds listeners through ITS `@Subscribe` annotation, which reflection cannot attach. It is compiled against
  two name-only shim classes in `src/discordsrv-shim/java` (`Subscribe`, `DiscordGuildMessagePreProcessEvent`) added via build-helper in `pom.xml` and EXCLUDED
  from the jar by `maven-jar-plugin` (`github/**`), so the real DiscordSRV classes are used at runtime. This deviates from "reflection only": the listener class
  is loaded only after DiscordSRV was found. `plugin.yml`: `softdepend: [DiscordSRV]` and permission `antibadwords.discord`.

### Not done / unsure (disabled with a log line, not guessed)
- DiscordSRV slash commands: need annotation-based provider classes and shaded JDA types; replaced by the prefix commands.
- Rewriting the text of a Discord message (censor): no confident API (`DiscordGuildMessagePostProcessEvent` setters differ between 1.x versions); blocks instead.
- Unverified API names used by reflection (all in the `DiscordSrvHook` javadoc): `DiscordUtil.sendMessage(TextChannel,String)`, `DiscordUtil.privateMessage(User,String)`,
  `getDestinationTextChannelForGameChannelName`, event getters, `ApiManager#unsubscribe`. If one is missing a single warning is logged.
- Left for the docs task: `6-guides/DISCORD.md`, README/CHANGELOG, `/abw info` line that DiscordSRV features are Bukkit-only (Fabric/Forge show nothing), bot/real-server test.
