# Task 44: PacketEvents packet scan and outgoing-text scan (Bukkit family)

Verification level: engine unit tests run (OutgoingScanTest, 417 tests green); plugin stub compile PASS (`tools/stub-compile.sh plugin`).
Nothing ran on a real server. Signatures come from plugin-api-dump job 132707156612.

## Done
- E. PacketEvents client scan: `PacketEventsScan` (sign, book pages and title, anvil rename), same behaviour as `ProtocolLibScan`
  (cancel on block/censor, `engine.onViolation`, anvil once per session). Chosen by `PacketScanPlan` (ProtocolLib is preferred).
- I. Outgoing-text scan, default OFF: engine `OutgoingScan` + `OutgoingScanSettings` + `ChatJson`; plugin `PacketEventsOutgoing`
  (system chat: log, mask, cancel) and `ProtocolLibOutgoing` (system chat and player chat: log, cancel; "mask" logs).
  Text is judged with `engine.explain`, so no strikes and no punishment. Texts that contain `[AntiBadWords]` (setting
  `ignore-containing`) are never touched. Log lines are limited per minute.
- Settings (integrations.yml): `integrations.outgoing-scan.enabled|action|sources|ignore-containing|log-limit-per-minute`.
- Shims: `2-plugin-bukkit-purpur/src/protocollib-shim` now also holds the PacketEvents names; pom jar exclude `com/github/retrooper/**` added.
- `tools/api-manifest.txt`: new section "Task 44". `tools/plugin-dump-classes.txt`: classes for the follow-up (below).

## Not possible yet (exact reason)
- Team prefix/suffix (NametagEdit, TAB) and tab-list names (TAB): the inner classes `WrapperPlayServerTeams$ScoreBoardTeamInfo`,
  `WrapperPlayServerPlayerInfoUpdate$PlayerInfo` and the ProtocolLib `InternalStructure`/`PlayerInfoData` are not in the API dump,
  so no member signature is verified. Added to `tools/plugin-dump-classes.txt`; implement after the next dump run.
- PacketEvents player chat (`WrapperPlayServerChatMessage.getMessage()` returns `ChatMessage`, not dumped): not scanned.
- ProtocolLib "mask" (write back): `AbstractStructure.getChatComponents` / `AbstractWrapper.getHandle` not dumped. ProtocolLib logs or cancels only.
- Fallback for tab names: join names are already checked by `filters.scan.join-names`.

## Docs text (for task 48)
Outgoing scan: "Checks chat text that other plugins send (VentureChat, HuskChat, broadcasts). Needs ProtocolLib or PacketEvents. Default off.
Action log (default), mask (PacketEvents), cancel. No strikes are counted. Messages of this plugin are never touched."
Changelog: "PacketEvents packet scan for sign, book and anvil text. New outgoing-text scan (integrations.outgoing-scan.*, default off)."

## Admin test
Install PacketEvents, set `integrations.outgoing-scan.enabled: true` and `action: mask`, restart, then have another plugin broadcast
a blocked word. Expect `***` in chat and one log line "Outgoing scan: a system-chat packet for <player> was changed.".
