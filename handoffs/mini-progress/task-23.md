## Mini task 23: ban plugins (tempban / ipban) and Bedrock names - engine tests run, Bukkit part stub-compiled only

Verification: `mvn -q -B test` 328 tests (13 new, `IntegrationsTest`); `BukkitPlatform` stub-compiled with javac against an extended Bukkit stub. Never run against EssentialsX, LiteBans, AdvancedBan, LibertyBans, Floodgate or a server.

### Added
- `Platform.hasPlugin(String)` (default false). `BukkitPlatform` implements it (PluginManager#getPlugin + isEnabled). Fabric/Forge: no existing mod-id lookup in the repo, so they keep false = `auto` means vanilla / Bedrock auto = off there.
- Package `engine.integrations`: `IntegrationsSettings` (file `integrations.yml`, category "Integrations", file header set), `BanService`, `BedrockNames`. `engine.bans()`, `engine.bedrock()`.
- Settings: `integrations.bans.provider` (auto|vanilla|essentials|litebans|advancedban|libertybans|custom), `.fallback` (kick|mute|none), `.duration-format` (auto|compound|single), per provider `.<provider>.tempban` and `.ipban` templates (empty = unsupported; vanilla tempban empty, vanilla ipban `ban-ip {player} {reason}`, Essentials ipban `banip ...`), `integrations.bedrock.mode` (auto|on|off), `.prefix` ("."), `.strip-prefix-before-filter` (true), `.space-replacement` ("_").
- Ladder actions `tempban <time> [reason]` (perm refused, use ban) and `ipban [reason]`; run last in a step like kick/ban. Placeholders in templates: {player} {uuid} {reason} {duration} {seconds} {millis}.
- Auto order: LiteBans, AdvancedBan, LibertyBans, Essentials, else vanilla. Duration: compound `1d2h30m` (d/h/m/s only), AdvancedBan single unit (`90m`) by default because its parser is believed to accept one unit (unverified).
- Bedrock: join-name check uses the name without prefix (when strip on); `{player}` in ban commands has spaces replaced; `engine.resolvePlayer` also tries prefix on/off and space/underscore variants (online and players.json). players.json stores the full name unchanged (tested through a restart).
- plugin.yml softdepend: DiscordSRV, Essentials, LiteBans, AdvancedBan, LibertyBans, floodgate, Geyser-Spigot.
- Startup log line: "Integrations: ban provider X, Bedrock names on/off".

### Decisions / unverified
- Templates and command syntax of the four ban plugins are from memory (not checkable here); all are editable. `-s` for LiteBans = silent.
- IP bans pass the player NAME to the provider command (no IP API in Platform); `ipban` with an offline name depends on the plugin.
- Fallback for tempban without template logs a warning each time. `ipban` without template only warns.
- Not done: Fabric/Forge `hasPlugin`; Floodgate API use (not needed).
- Merge note: SettingsRegistryTest file/category lists got "integrations.yml"/"Integrations" appended; Engine got 2 fields, 1 register line, small edits in onJoin/resolvePlayer/log; other integration agents add to the same file/category.
- Docs task can copy: "tempban/ipban ladder actions use your ban plugin's console commands; see integrations.yml".
