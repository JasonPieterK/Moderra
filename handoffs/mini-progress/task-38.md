## Task 38: real-API follow-up (ProtocolLib scan, Plan extension; PacketEvents and DiscordSRV slash left off)

Verification: `tools/stub-compile.sh plugin` PASS; `mvn -q -B test` (engine). Never run against real plugins.
1. plugin-apis report (run 43667029230, job 132668587772): 64 entries, 0 FAIL, 0 WARN. Nothing to fix (EssentialsX isMuted/setMuted inherit from UserData, PAPI onRequest inherits from PlaceholderHook, LuckPerms getCachedData inherits from PermissionHolder: all PASS).
2. ProtocolLib packet scan: `integrations/ProtocolLibScan` (PacketAdapter(Plugin, PacketType...), onPacketReceiving, PacketType.Play.Client UPDATE_SIGN/B_EDIT/ITEM_NAME,
   StructureModifier.withType/read/size, all seen in the dump), started by `ExtraHooks` only when `PacketScanPlan` picks ProtocolLib (events missing or
   `integrations.packet-scan.only-when-events-missing` false). Fields are read by type (String[], List, String). Shim: `src/protocollib-shim`, excluded from the jar (`com/comphenix/**`).
   PacketEvents: NOT done; `ProtocolPacketEvent` / `PacketListenerCommon` (getPacketType, getUser) are not in the dump; added to tools/plugin-dump-classes.txt. Logs one line.
3. Plan: `integrations/PlanHook` (DataExtension, @PluginInfo, @NumberProvider violations/strikes, @StringProvider muted, ExtensionService.register, CapabilityService.registerEnableListener),
   shim `src/plan-shim` (excluded `com/djrapitops/**`), registered only if Plan is present and `integrations.plan.enabled`. Unverified: the `UUID playerUUID` parameter convention of provider methods is not visible in javap.
4. DiscordSRV slash commands: NOT done. SlashCommandProvider/PluginSlashCommand/@SlashCommand were seen, but they need shaded JDA types (CommandData, SlashCommandEvent, hooks) that are not in the dump;
   added to tools/plugin-dump-classes.txt. Prefix commands (`!abw`) stay as they are; no fallback setting added since nothing replaces them yet.
Manifest: ProtocolLib and Plan entries are now required (not advisory). Docs: ProtocolLib scan and Plan extension now exist; PacketEvents/slash do not.
