# Mini tasks 4 and 5: built-in mute (session 4, item 3) - engine only

Verification level: engine unit tests run (`mvn -q -B test`, 23 new tests in `MuteServiceTest`). Adapters not changed.

## Added (package `engine.moderation`)
- `MuteService implements MuteHandler`: `mute(...)`, `unmute(uuid, actor)`, `get(uuid)`, `isMuted`, `list()`, `findByName`, `sweep()`,
  `enforce(Message)`, `onJoin`, `load`, `flush`, `size`, `values(mute, now)`. Public constants `BYPASS_PERMISSION`
  (`antibadwords.bypass.mute`) and `PERMISSION` (`antibadwords.mute`). `engine.mutes()` returns it; it is the default `muteHandler()`
  (the logging stand-in is only used after `setMuteHandler(null)`).
- `MuteSettings` (`moderation/mute.yml`, category "Mute"): `mute.enabled`, `mute.default-duration` (1h), `mute.block.chat/commands/signs/books`
  (commands default `msg tell w whisper r reply t pm m say me teammsg tm`, `*` wildcard), `mute.check-every` (30s), `mute.notify-on-join`,
  `mute.no-reason-text`, `mute.messages.blocked/join/muted/unmuted/expired` (placeholders `{player} {reason} {remaining} {duration} {by}`).
- `MuteCommands`: `/abw mute <player> [duration] [reason...]`, `/abw unmute <player>`, `/abw mutes [page]` (8 per page), permission
  `antibadwords.mute`, tab completion (online + offline names, duration hints, currently muted names). Registered in `Engine.registerBuiltIns`.
- State `data/mutes.json`: `{"version":1,"mutes":{"<uuid>":{"name","since","until"(-1=perm),"reason","by"}}}` through `DataFile`.
- `ModerationEvent.MUTE / UNMUTE / MUTE_EXPIRED` (data: reason, by, duration, until, permanent; source `ladder` or `command`).

## Engine changes (small)
`inspect()` first asks `mutes.enforce(message)`; when muted it returns a BLOCK verdict WITHOUT hits (loaders only call `onViolation` for
verdicts with hits, so no strikes) and the service itself tells the player (throttled to 1/s, sent on the server thread). `onJoin` -> `mutes.onJoin`,
`loadData` -> `mutes.load`, `shutdown` -> `mutes.flush`. `SettingsRegistryTest` list of files/categories got `moderation/mute.yml` / "Mute".

## Decisions
- Mute check runs before the general `antibadwords.bypass` shortcut; only `antibadwords.bypass.mute` exempts.
- Duration argument must have a unit (`90` is a reason word); no duration = `mute.default-duration`.
- A muted player stays findable for `/abw unmute` through `mutes.json` even when `known-players.forget-after-days` removed them.
- Periodic task: `runSyncLater` re-arms itself while timed mutes exist; guarded so a platform that runs tasks inline (FakePlatform) does not loop.
  Expiry found lazily by `inspect`/`get` or by the task emits `mute-expired` and tells the player; mutes that ended while the server was off are
  ended on load without message to anybody offline.
- Mute events and expiry messages are published on the server thread (`runSync`).

## Left for later
Ladder already uses `engine.muteHandler()`, nothing to wire. Adapters: permission registration for `antibadwords.mute` and
`antibadwords.bypass.mute` (plugin.yml, ForgePermissions, FabricPermissions); sign/book enforcement needs the adapters to call `inspect` (they do).
History subscriber, docs/guides/README/CHANGELOG, bot.js. Nothing verified on a real server.
