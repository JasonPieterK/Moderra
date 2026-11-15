# Task 29: network sync (shared mutes and strikes for Velocity / BungeeCord networks)

Branch `mini/29`. Verification: engine unit tests run (`NetworkSyncTest`, 11 tests, two engines on temp folders). No plugin or proxy code
was written, so nothing needed a stub compile. Nothing tested on a real network.

## Decision: no proxy plugin module
Checked Maven Central: `com.velocitypowered:velocity-api` is NOT there (404); `net.md-5:bungeecord-api` is. A Velocity module cannot build
offline, and a Bungee-only module would be half a solution, so no proxy module was added. The shared-folder mode needs no proxy at all
(it also works with no proxy, e.g. several servers on one host). The plugin-messaging side is implemented in the engine only
(transport interface); the loader glue and a proxy relay are left undone (see below).

## What was added (engine, package `engine.network`)
* `NetworkSettings` (`integrations.yml`, category Integrations): `integrations.network.mode` (`off` default | `shared-folder` |
  `plugin-messaging`), `folder` (default `../abw-network`, relative to the plugin config folder), `server-id` (empty = `server-<hash of config path>`),
  `poll-interval` (2s), `sync-mutes` (true), `sync-strikes` (true).
* `SyncRecord` (one JSON line: mute / unmute / strike / reset, origin server, wall-clock ts), `SyncChannel` (send, poll, close),
  `SharedFolderChannel`, `PluginMessagingChannel` (+ `Transport` interface; payload = the JSON line, channel `antibadwords:sync`), `NetworkSync`.
* `Engine.network()`; `NetworkSync` is installed in `registerBuiltIns`, reconfigures on every reload, shut down in `Engine.shutdown`.
* `MuteService.Listener` + `applyRemoteMute` / `applyRemoteUnmute`; `PersistentStrikeService.Listener` + `addRemote` / `resetRemote`
  (remote changes are never passed on again, so no echo loops). Both additive.
* Logs on start / reload what it uses ("Network sync: shared folder ... as server 'x' (mutes on, strikes on)"); mode plugin-messaging without a
  transport logs one warning and syncs nothing.

## Shared-folder mode
Each server appends to its own `sync-<id>.jsonl` (never two writers on one file) and tails the others; read positions are kept in
`read-by-<id>.txt` (restart neither misses nor repeats). Partial last lines are ignored until complete. Damaged lines are skipped.
A journal that already existed when this server started and was never read is "backlog": only its mutes are applied, its strikes are not
(a new server does not inherit stale strike points). Journals are never compacted (they only grow; delete them when all servers are stopped).

## Conflict rules
* Mutes and unmutes: per player the newest timestamp wins, equal timestamps: larger server id wins. Expired mutes are ignored.
* Strikes: additive (two servers adding at once lose nothing). Reset: newest wins; strike points older than the newest known reset are dropped.
* Mute expiry is not synced (`until` is absolute, each server expires it itself). `strikes.clear()` (clear all) is not synced.
* Needs synchronised clocks (NTP). Strike decay/expiry runs per server on its own copy.

## Left undone / for the build-verification list
* Plugin glue for plugin-messaging: Bukkit `Messenger` registration of `antibadwords:sync` and `engine.network().setTransport(...)` /
  `receive(...)`, and a proxy relay that forwards the channel between backend servers (Velocity/Bungee APIs not available offline). Until then use shared-folder.
* Not touched: `CoreCommands.KNOWN_PLUGINS` (backends do not see the proxy as a plugin). No `/abw` command for network status yet.

## Docs section (copy for the final docs task)
Network sync (optional, off by default): in `integrations.yml` set `integrations.network.mode: shared-folder`, point `folder` at a directory all
servers can read and write, and give each server a unique `server-id`. Mutes, unmutes, strike points and strike resets are then shared by all
servers. Conflicts: newest mute change wins; strikes add up; a reset wins over older strikes. Keep server clocks in sync. Mode
`plugin-messaging` (channel `antibadwords:sync`) needs loader and proxy support that is not part of this build.
