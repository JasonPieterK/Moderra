## Mini task 21: EssentialsX / EssentialsX Chat compatibility (Bukkit family only)

Verification: engine unit tests run (`mvn -q -B test`: 325 tests, 10 new in `engine/integrations/EssentialsLinkTest`, fake bridge). Bukkit glue
stub-compiled only (javac `--release 21` against an extended copy of the bs17 stub; stub additions: `Plugin#isEnabled/getDescription`,
`PluginManager#getPlugin/registerEvent`, `EventExecutor`, `Event`, `MetadataValue`, `Player#getMetadata`). NEVER run against a real EssentialsX
(its repository is blocked). Fabric/Forge: not applicable (EssentialsX is Bukkit-only; the engine part is inert there, `integrations.yml` is ignored).

### Engine (`engine.integrations`)
- `EssentialsBridge` (interface the plugin implements by reflection): `mute(uuid)`, `setMute`, `clearMute`, `nickname`, `resetNickname`, `vanished`.
- `EssentialsLink` (`engine.essentials()`; `attach(bridge)` / `detach()` / `active()`): mute mode logic, `displayName`, `onJoin` nickname check,
  `vanished`, `broadcastAbout(uuid, text)`, `extraCommandText`. Every bridge failure is caught and logged once per call (feature skipped).
- `IntegrationSettings` (file `integrations.yml`, category "Integrations", all `integrations.essentials.*`): `enabled` (true), `chat-priority`
  (auto|lowest|highest), `mute-mode` (own|essentials|both, default own), `nickname-in-messages` (off|nickname|both, default off),
  `hide-vanished` (true), `scan-commands` (list), `check-nickname-on-join` (true), `nickname-action` (log|warn|reset, default warn), `nickname-message`.
- `Engine`: `essentials()`, `inspectName(uuid, name, text)` (word checks on a name-like text, nothing counted), join schedules the nickname check 1 s later,
  `judge` also scans the labels of `scan-commands` while Essentials is connected (`commands.ignored-commands` wins).
- `MuteService`: `mute()` writes to Essentials in modes essentials/both (falls back to the built-in list with a warning when Essentials refuses or the
  player is unknown to it); `unmute()` also lifts the Essentials mute; `enforce()` holds back Essentials-muted players (modes essentials/both) with the
  normal blocked message (reason/remaining from Essentials, `by` = "essentials"), no strikes.
- `ViolationHandler`: staff notice and the three broadcast sites use the display name (never console commands/events) and go through
  `broadcastAbout` (vanished player: only holders of `antibadwords.notify`).

### Bukkit (`integrations/essentials/EssentialsHook`)
Reflection only. API names used: `Essentials#getUser(Player)` / `getUser(UUID)`; `User#isMuted, setMuted(boolean), getMuteTimeout, setMuteTimeout(long)`
(required, else hook off); optional `getMuteReason, setMuteReason(String), getNickname, setNickname(String), setDisplayNick, isVanished`; Bukkit metadata
`"vanished"`. Unverified against a real EssentialsX: whether `getUser(UUID)` exists on every version (online players use `getUser(Player)`), that
`setMuteTimeout(0)` = no end, and that `setMuteReason(null)` is accepted (failure is ignored). Calls `mute(uuid)` from the async chat thread (read only).

### Chat order (exact)
- Our chat handler is registered by hand (`PluginManager#registerEvent`) in `AntiBadWordsPlugin#registerChatListener`: priority LOWEST when the
  plugin `EssentialsChat` exists (setting `auto`), else HIGHEST (unchanged), `ignoreCancelled = true`. EssentialsX Chat has handlers at LOWEST, NORMAL
  and HIGHEST (from memory, not verified): they skip cancelled events.
- `plugin.yml`: `softdepend: [DiscordSRV, Essentials]` and `loadbefore: [EssentialsChat]` (DEVIATION from "softdepend EssentialsChat": softdepend plus
  loadbefore on the same plugin is a cycle; loadbefore makes our LOWEST handler register before EssentialsChat's, and Bukkit runs equal priorities in
  registration order). EssentialsChat is detected by name only.
- BLOCK: event cancelled and `getRecipients().clear()`; captured prompt answers likewise; CENSOR: `setMessage(clean)` so Essentials formats the clean text.
- Commands/social spy: a blocked or censored command is cancelled at HIGHEST before Essentials' command runs, so `/msg`, `/r`, `/mail`, `/me`, `/broadcast`,
  `/helpop`, `/nick` text that is blocked never reaches social spy, mail or helpop. Command-preprocess listeners of other plugins at a lower priority
  could still see it. Not changed: command priority.

### Left / notes for the docs task
- Docs (README, CHANGELOG, a guide section "EssentialsX") not written; copy the facts above. `/abw info` does not show the Essentials status yet
  (`EssentialsLink` has no status method; add one line if wanted).
- Real-server test of everything Bukkit-side is open (listed in `handoffs/BUILD-VERIFICATION-TODO.md`).
