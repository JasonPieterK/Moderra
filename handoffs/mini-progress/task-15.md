## Mini task 15: GUI special pages and main-menu status (session 5, B1 second half + B4 engine part), engine only

Verification level: engine unit tests run (`mvn -q -B test`: 290 tests; new `GuiSpecialPagesTest` 19 tests with simulated clicks and the real chat prompt). No settings added. No renderers, no docs.

### Added (package `engine.gui`)
- `SpecialPages` (package-private): main-menu extras, languages, word-list index, word browser, ladder, mutes, profiles, search results.
- `MenuPrompts.start(engine, state, viewer, request, after)`: connects a `TEXT_INPUT` action to `engine.prompts().start(...)` (TextPrompt.of with `Menu.validateText` as validator; answer -> `Menu.submitText`; cancel/timeout -> `Menu.cancelText`; the resulting `MenuAction` goes to `after`). Console viewer (no id): request cancelled, returns null. Works for STRING, SECRET (masked, never echoed), list add, search, custom word.
- `Menu.validateText(state, text)` (null = ok, else error). `ConfigAccess.engine()` (default null; `EngineConfigAccess` returns it): without an engine the main menu only has categories (old behaviour, old tests unchanged).
- `MenuState.Kind` + LANGUAGES SOURCES WORDS LADDER MUTES PROFILES SEARCH; `TextRequest.Purpose` + SEARCH, ADD_CUSTOM_WORD; new `Icons` (status language language_off word ladder mute profile search). `Engine.profileOverrides(name)`.
- `MenuState.viewer()` is set by `Menu` on every call after the permission check so handlers (unmute) can re-check permissions.

### Behaviour
- Main menu: categories first (slot indexes of categories unchanged), then buttons "Language lists", "Word browser", "Punishment ladder", "Muted players", "World profiles", then info items "Protection level: N", "Scan sources" (lore lists every filters.scan.* switch on/off), "Active profile: x". All follow staged values. Slot 47 = "Search settings".
- Language lists: one entry per wordlists/*.yml (name, script, word count, count at current level, per-level counts, review status, ON/off). Left click toggles (staged in `filters.enabled-word-lists`, written on Done); right click browses its words; custom is always on.
- Word browser: index of languages, custom and the whitelist. Language lists are read-only and paged. Custom list: slot 47 adds a word via prompt, shift-click removes; both APPLY AT ONCE (custom.yml is not a setting). The whitelist opens the existing LIST view of `filters.whitelist` (staged, Done).
- Ladder: READ-ONLY (a free-form section; editing steps/actions in a chest GUI is not feasible). Shows steps with threshold, repeat, actions, message; says where to edit.
- Mutes: list with remaining time/reason/by; click unmutes if the viewer has `antibadwords.mute` (checked at click time), else ERROR message. Applies at once.
- World profiles: read-only list with the override keys/values; default profile glows.
- Search: prompt text; all terms must occur in title/key/description/category. One match: jumps to its category page (right page). Several: results page with the real editable setting entries. None: the prompt validator refuses, so the engine asks again.

### Left for later tasks
- Platform renderers/entry points (`/abw`, `/abw gui`, plugin.yml/Forge/Fabric permission nodes, calling `MenuPrompts.start` on TEXT_INPUT and `Menu.click`), bot tests, docs. Ladder and world-profile editing stay read-only.
