## Mini task 28: ProtocolLib / PacketEvents, CoreProtect, Plan (Bukkit family)

Verification: engine unit tests run (`mvn -q -B test` green, 9 new tests in `BlockedEditLogTest`, `integrations/PacketAndLogTest`; `generate-docs.sh` run).
Plugin glue `integrations/ExtraHooks.java` stub-compiled: `tools/stub-compile.sh plugin` still fails only on the 4 known repo-stub gaps
(getMetadata, getAuthors, getRecipients, EssentialsHook:256) that agent 26 fixes; no error in ExtraHooks. No stub members needed beyond the existing ones.
None of ProtocolLib, PacketEvents, CoreProtect, Plan API is on Maven Central (checked: 404), never run against the real plugins.

### Settings (`integrations.yml`, class `engine.integrations.ExtraIntegrationSettings`, registered in `Engine.registerBuiltIns`)
`integrations.protocollib.enabled`, `integrations.packetevents.enabled`, `integrations.packet-scan.only-when-events-missing` (true),
`integrations.coreprotect.enabled` / `.log-as` (chat|command) / `.include-text` / `.sources` (sign, sign-back, book), `integrations.plan.enabled` (reserved).
`CoreCommands.KNOWN_PLUGINS` got ProtocolLib, packetevents, CoreProtect, Plan. plugin.yml softdepend extended.

### Engine
- `PacketScanPlan.choose(...)`: which backend (none / ProtocolLib / PacketEvents) and why; no packet scan while the Bukkit events exist (default).
- `BlockedEditLog.install(engine, sink)`: subscribes to `violation` events with action block/censor for the configured sources and hands a line
  `[AntiBadWords] block sign edit: <text, max 200>` to a `Sink`. Tested with two engines/fixtures.

### Plugin (`ExtraHooks`)
- CoreProtect by reflection: `plugin.getAPI()`, optional `isEnabled()`, then `logChat(Player,String)` / `logCommand(Player,String)` are PROBED (by name+types);
  if neither exists: one warning, hook off. Records only for online players.
- ProtocolLib/PacketEvents: detected and decided (log line) only. The packet listener is NOT implemented (API names and per-version packet layouts of sign/book/anvil
  not verifiable here); logs a warning when a packet scan would have been chosen. Needed later: real compile against ProtocolLib or PacketEvents.
- Plan: not implemented (DataExtension needs the Plan API at compile time); one info line when Plan is present.

### Docs section
Document: the keys above; CoreProtect records show in `/co lookup` as chat/command by the player; packet scan = detection only; Plan = use PlaceholderAPI values
(`%antibadwords_violations_total%`) instead. List in BUILD-VERIFICATION-TODO: CoreProtect `getAPI/logChat/logCommand` names, packet listener, Plan extension.
