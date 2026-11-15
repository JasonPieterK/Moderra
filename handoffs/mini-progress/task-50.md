# Task 50: outgoing scan of team prefix/suffix, tab-list names, PacketEvents player chat, ProtocolLib write-back

Verification level: engine unit tests run (all green); plugin stub compile PASS (`tools/stub-compile.sh plugin`, 287 files).
Signatures come from plugin-api-dump job 132743385819 (run 43691650981). Nothing ran on a real server.
The plugin-apis job 132743385683 has one FAIL (`placeholderapi-fabric` `registerCommon`); it belongs to task 51, none of my entries fail.
Build run 43691391065: engine-tests, plugin and Fabric builds passed (the Forge build was still running).

## Done
- PacketEvents (`PacketEventsOutgoing`): system chat (as before), player chat (`WrapperPlayServerChatMessage.getMessage()` ->
  `ChatMessage.getChatContentJson/setChatContentJson(ClientVersion, String)`, `event.getClientVersion()`), team display name / prefix /
  suffix (`WrapperPlayServerTeams.getTeamInfo()` -> `ScoreBoardTeamInfo` getters and setters), tab-list display names
  (`WrapperPlayServerPlayerInfoUpdate.getEntries()` -> `PlayerInfo.getDisplayName/setDisplayName`).
  A `Component` is turned into JSON and back with `new WrapperPlayServerSystemChatMessage(false, component).getMessageJson()` and
  `new WrapperPlayServerSystemChatMessage(false, json).getMessage()` (both constructors and both methods are in the dump), so no
  Adventure serializer name is guessed.
- ProtocolLib (`ProtocolLibOutgoing`): "mask" now writes back for system chat and player chat (same field, a String or
  `WrappedChatComponent.fromJson(json).getHandle()`); team packets via `getOptionalStructures().read(0)` and
  `InternalStructure.getChatComponents()` (read/write every component: display name, prefix, suffix); tab-list packets via
  `getPlayerInfoDataLists()` (every list field that reads) and a new `PlayerInfoData(UUID,int,boolean,NativeGameMode,WrappedGameProfile,WrappedChatComponent)`.
- Engine: new sources `team` and `tab-list` in `integrations.outgoing-scan.sources` (the default list contains them; the scan itself stays off by
  default). Team and tab-list packets are never cancelled: action `cancel` acts as `mask` there (a dropped team or tab-list packet breaks the client state).
  Test `teamAndTabListAreMaskedNeverCancelled`.
- Shims (excluded from the jar): new names in `src/protocollib-shim/java`, plus `net/kyori/adventure/text/Component` (pom jar exclude `net/kyori/**` added).
- `tools/api-manifest.txt`: section "task 50" (all names above). `ExtraHooks` log lines now list the active sources.

## Not verified at run time (read this before release)
- PacketEvents on Spigot must provide `net.kyori.adventure` unrelocated (the API dump shows `net.kyori.adventure.text.Component` in the signatures).
  If a server shows `NoSuchMethodError`/`LinkageError` the packet is left unchanged (caught) and the scan does nothing for team and tab-list packets.
- Legacy clients (before 1.13) use `LegacyComponent` team fields; those are not scanned (the getters used here return null then).
- `DISGUISED_CHAT` is scanned only by ProtocolLib (no PacketEvents wrapper was dumped).
- ProtocolLib `getPlayerInfoDataLists()` has different list indexes between server versions; the code tries every list field and skips one that does not read.
- Cost: every team and player-info packet is judged with `engine.explain`; the existing log limit applies to log lines only.

## Docs text (for task 48)
Outgoing scan sources: `system-chat`, `player-chat`, `team` (team name, prefix and suffix, for example from TAB or NametagEdit), `tab-list`
(tab-list display names). Action `mask` works with ProtocolLib and PacketEvents. Team and tab-list packets are masked, never cancelled.
Changelog: "Outgoing scan now checks team prefixes and suffixes, tab-list names and PacketEvents player chat, and can mask with ProtocolLib."

## Admin test
Install TAB or NametagEdit and PacketEvents or ProtocolLib, set `integrations.outgoing-scan.enabled: true` and `action: mask`, restart,
give a team a prefix that contains a blocked word. Expect `***` in the name tag and the log line "Outgoing scan: a team packet for <player> was changed.".
