## Mini task 6/7: history (session 4, item 4) - engine only

Verification level: engine unit tests run (21 new: `HistoryStoreTest` 14, `HistoryEngineTest` 7). Adapters not touched.

### Added
- `moderation/HistoryStore`: subscribes via `events.subscribe(history::record)` (Engine constructor). `record(event)` builds a small
  `Entry` on the caller thread and queues it; ONE daemon writer thread ("AntiBadWords-history", own thread, not Platform.runAsync, so
  order is kept and `shutdown` can flush) appends to `data/history/YYYY-MM.jsonl` (month in UTC of the event time). Other methods:
  `find(uuidOrNull, page, size)` -> `Page(entries,total)` newest first, `recent(n)`, `count(uuid)`, `purge(uuid)`, `flush()`
  (waits for the writer), `cleanupNow()`, `enqueue(Entry)`, `shutdown()` (writes the rest, stops the thread, later events ignored).
- Line fields: time, kind, uuid, player, world, source, check, detail, actor ("system" default), points (= event data `count`),
  step, text (only when stored). Empty fields are left out. Only these fields are ever written (no other event data: no secrets).
  Mapping from event data: detail <- detail | reason | actions | command; actor <- actor | sender; `duration` is appended as " [10m]".
  So the mute service only needs to publish kind `mute`/`unmute`/`mute-expired` with data `reason`, `duration`, `actor`.
- `moderation/HistorySettings` (file `moderation/history.yml`, category "History"): `history.enabled` (true), `history.kinds` (`*`),
  `history.store-message-text` (false), `history.redact-words` (true; masks `word` and `detail` with stars, and stores NO text when the
  word is not found in it, e.g. disguised spelling), `history.retention-days` (90, 0 = forever), `history.max-total-size-mb`
  (50, 0 = none), `history.page-size` (8).
- `command/HistoryCommand` (`/abw history <player> [page]`, `recent [n]` (max 100), `purge <player> [confirm]`), permission
  `HistoryStore.PERMISSION` = `antibadwords.history`; purge also needs `antibadwords.admin`; first call only counts and tells how to
  confirm, the call with `confirm` deletes and publishes an admin-command audit. Offline players via `engine.resolvePlayer`. Times UTC.
  Player-supplied text is stripped of `§` and control characters before display.
- Engine: `engine.history()`, `HistorySettings.register` in `registerSettings`, command registered in `registerBuiltIns`,
  `history.shutdown()` in `shutdown()`. `SettingsRegistryTest` list extended ("moderation/history.yml", "History"). `6-guides/CONFIGURATION.md` regenerated.

### Decisions
- Enabled by default (metadata only; text is off by default). Retention and size are checked at the first write, then at most hourly,
  and when the size limit is hit. Retention is per whole monthly file. Size cap deletes the oldest months, never the current one;
  if the current file alone is over the cap new events are dropped with one warning per hour.
- Reads (`/abw history`) wait for the writer and read the files on the calling thread (rare, staff-only).

### Left for later
- Permission node `antibadwords.history` must be added to `plugin.yml` and the Fabric/Forge permission registration (adapters).
- Docs (README, CHANGELOG, `6-guides/MODERATION.md`, engine README) and the bot test. Not verified on a real server.
