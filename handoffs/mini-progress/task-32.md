# Task 32: CJK/Thai whitelist, IP API, Discord censor
Verification: engine tests run; Bukkit classes stub-compiled only.
- WordMatcher step 4: whitelist entries in unspaced scripts suppress any hit lying inside them (WordIndex.firstContained with an Occurrence filter). No such entries = identical old path.
- Default whitelist (new installs only; CoreSettings.LEGACY_WHITELIST via LegacyMigration.LEGACY_DEFAULTS keeps the v1 list): Han entries 你妈妈, 我操作, 三小時 ... Restored zh: 妈的 他妈 我操 我日 去死; zh-tw: 媽的 他媽 你媽 豬頭 低能 去死. NOT restored: zh-tw 三小, 廢物 (三小隊, 這些廢物可以回收 still false positives). Kana not touched.
- Platform.playerIp(UUID) (default null); Bukkit implements it. PlayerDirectory stores `ip` (additive), setting known-players.store-ip (default true; off = not stored, not written at next save). BanService ipban: {ip} = online IP, else stored IP, else name + warning. Default ipban templates now use {ip}.
- Discord censor: DiscordSrvHook subscribes DiscordSrvPostListener (shim DiscordGuildMessagePostProcessEvent) when getProcessedMessage/setProcessedMessage(String) exist; canRewrite() true then; the pre-process REPLACE decision is kept 30 s and applied by replacing the raw text inside the processed message. Fallback: block + one log line. Names listed in BUILD-VERIFICATION-TODO.
- Stub: Player#getAddress added to tools/api-stubs.
Docs task: mention store-ip privacy setting and {ip} placeholder.
