## Mini task 16: text input prompts (session 5, B2), engine only

Verification level: engine unit tests run (`mvn -q -B test`: 190 tests, 10 new in `PromptServiceTest`, fake clock). No adapter code.

### Added (`engine/gui/prompt/`)
- `TextPrompt` (title, hint, secret flag, validator, callback, cancel callback). `TextPrompt.of(...)` for free text,
  `TextPrompt.forSetting(setting, onValue, onCancel)` validates with `Setting.require` (type/min/max/allowed values) and hands the parsed value to the callback; SECRET settings are flagged secret.
- `PromptKind` SIGN / CHAT. `PromptService` (`engine.prompts()`): `start(uuid, prompt)` (returns the kind; replaces an older prompt and runs its cancel callback),
  `onChat(uuid, text)`, `onSignInput(uuid, String[] lines)` (trim, drop empty lines, join with one space; all empty = cancel), `cancel(uuid)`, `onQuit(uuid)`,
  `expireDue()`, `isActive`, `activeKind`, `kindFor()`. Timeout is checked lazily on every call and by a `runSyncLater` task; an invalid answer shows the error and restarts the countdown.
- `PromptSettings` (config.yml, category General): `gui.prompt.mode` (auto|sign|chat), `gui.prompt.timeout` (60s, 5s-1h), `gui.prompt.cancel-word` (cancel), and the texts
  `gui.prompt.messages.chat|sign|error|cancelled|timeout`. Registered in `Engine.registerSettings`.
- `Platform` (additive defaults): `boolean supportsSignPrompt()` = false, `void openSignPrompt(UUID, String title, String hint)` = no-op (called at start and again after an invalid answer).
- `Verdict.captured()`: set by `Engine.inspect` when a CHAT message with a player id was consumed by a chat prompt. Design: type is BLOCK with no hits, so an adapter that knows nothing
  still cancels the message and never calls onViolation. An adapter that knows it should: cancel, not broadcast, not scan, not store, not publish events, not call `onViolation`.
  Checked before the bypass permission and before any check, so an answer is never scanned or counted (test: a swear as answer gives no hits).

### Decisions
- Secrets are never echoed: error texts come from `Setting.require` (secret values print as "the value") or the validator; the engine never repeats typed text and never logs it. Accepted text is not echoed either.
- Callbacks and replies go through `platform.runSync` (the chat event can arrive on an async thread).
- Prompt texts are settings (everything configurable); `{hint}` is the setting's constraint text.

### Left for later tasks
- GUI model calls `engine.prompts().start(...)`; adapters: in the chat handler check `verdict.captured()` first; sign: implement `supportsSignPrompt/openSignPrompt`, call `onSignInput`, cancel the edit when it returns true; call `onQuit` on disconnect.
- Docs (`GUI.md`, CONFIGURATION is regenerated).
