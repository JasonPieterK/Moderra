# Task 27: chat-channel plugins (ChatControl, VentureChat, HuskChat, CarbonChat)

Verification: engine unit tests run (ChatChannelsTest, 6 tests; whole suite 377, one unrelated timing-flaky test HistoryStoreTest.sizeCapDeletesOldestThenStopsWriting
failed once under load and passes alone). Plugin glue: read only + stub compile (my two files show no errors; the only error left is the old stub gap
`PluginManager.registerEvent`, owned by task 26's stub fix).

## Added
- Engine: `Message.channel()` / `withChannel(String)` (additive, default "", kept by withText/withDimension/asProbe).
- Engine: `integrations.ChatChannels` (settings + pure logic), registered in `Engine.registerSettings` (one line).
  Settings in integrations.yml: `integrations.chatchannels.enabled` (master), `.chat-priority` (auto|lowest|highest),
  `.chatcontrol|venturechat|huskchat|carbonchat` (detection on/off), sections `.channel-profiles` (channel pattern -> worlds.yml profile)
  and `.channel-commands` (command name -> channel).
- `Engine.settings(player, world, dimension, perms, channel)` overload. Profile order: permission antibadwords.profile.X, LuckPerms meta/group,
  channel profile, world rule, default. In `judge`, a COMMAND message with no channel gets one from `channel-commands`.
  Unknown profile in channel-profiles: warning at start/reload, line ignored.
- `CoreCommands.KNOWN_PLUGINS` gets the four names.
- Plugin: `ChatChannelPlugins` (detect by plugin name only, log, priority). With auto and any of them found, the chat check runs at LOWEST;
  `plugin.yml` softdepend + loadbefore (ChatControl, VentureChat, CarbonChat) so our LOWEST handler is registered first.

## Decisions / not done (never guess API names)
- Their own events and API (channel name of a chat line) were NOT used: names unverified, no jar available. So channel names reach the engine only
  through `channel-commands` (/g hello) or a future loader that calls `Message.withChannel`. Not verified: the priority these plugins use.
- HuskChat works on the proxy (Velocity/Bungee); on a backend server only the detection log applies.
- Add to BUILD-VERIFICATION-TODO if wanted: real channel lookup per plugin (VentureChat/ChatControl/CarbonChat API), actual event priorities.
- Stub members needed: none new (only Bukkit.getPluginManager().getPlugin, EventPriority).

## Docs section (copy)
Chat-channel plugins: set `integrations.chatchannels.channel-profiles` (e.g. `global: strict`, `staff: relaxed`) and
`channel-commands` (`g: global`) to give a channel its own rule profile from worlds.yml. Add the channel commands to
`filters.scan.scanned-commands` to have them scanned. With ChatControl/VentureChat/CarbonChat installed the chat check runs at LOWEST
(`chat-priority`). Everything can be switched off with `integrations.chatchannels.enabled: false`.
