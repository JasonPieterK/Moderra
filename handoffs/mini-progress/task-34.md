# Task 34: plugin messaging glue, /abw network, version 2

Verification: engine unit tests run (`NetworkMessagingTest`, 3 tests), `tools/stub-compile.sh` PASS. Not tested on a real proxy.

* Engine: `network/BungeeForward` (pure: `encodeForward(sub, data)` = UTF "Forward", UTF "ALL", UTF sub-channel, short length, data; `decodeForwarded(sub, msg)`),
  `PluginMessagingChannel` now queues records (max 500, oldest dropped) while `Transport.available()` is false and flushes on send/poll,
  `SyncChannel.queued()`, `NetworkSync.peers()/lastSync()/queued()`, `/abw network` (`NetworkCommand`, admin).
* Plugin: `NetworkMessaging` registers `antibadwords:sync` and `BungeeCord` (out + in) through `Messenger`, wraps outgoing records in `Forward ALL`,
  carrier = first online player (none online: queued), unregisters in `onDisable`. Stubs extended: Messenger, PluginMessageListener, Player.sendPluginMessage, Plugin/Server getServer/getMessenger.
* Version 2: pom, both gradle.properties (Forge's `mod_version` is in `4-mod-forge/gradle.properties`), jar-name patterns in scripts/comments, README line 12, PROJECT_SUMMARY (2.0 and jar table). `1-download-jars/` untouched; README line 42 (version-1 jars there) left as is.
* Decisions: Velocity needs its BungeeCord bridge enabled for `Forward`. Sending runs on whichever thread flushes (poll is async); relies on `sendPluginMessage` being safe off-thread (true on Paper/Spigot in practice, unverified). Channels are registered at start regardless of mode.
* Left: real-network test (add to BUILD-VERIFICATION-TODO); docs by the docs task.
