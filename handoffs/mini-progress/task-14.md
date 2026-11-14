## Mini task 14: GUI engine model (session 5, B1 first half) - engine only

Verification level: engine unit tests run (`mvn -q -B test` green; new: `gui/MenuTest` 16 tests, `GuiEngineTest` 1 test on a real engine).
No settings added (no generate-docs change). Special pages, text prompts, platform renderers, docs are NOT done.

### Added (package `engine.gui`, no Minecraft imports)
- `Menu` (static pure-ish functions): `open(Engine)` / `open(ConfigAccess)` -> `MenuState`; `render(state) -> List<Slot>`;
  `title(state)`; `click(state, slot, ClickType, CommandSender viewer) -> MenuAction`; `submitText(state, text, viewer)`;
  `cancelText(state, viewer)`; `closeRequested(state, viewer)` (unsaved changes -> confirm page: Save / Discard / Keep editing);
  `closed(state)` (viewer left: drop staged). `Menu.PERMISSION = "antibadwords.gui"`, checked on EVERY call; a missing viewer or
  permission gives `DENIED` and drops the staged changes (fail closed).
- `MenuState` (one per viewer): navigation stack of `View(kind MAIN|CATEGORY|LIST|CONFIRM, target, page)`, staged values,
  pending `TextRequest`. `hasUnsavedChanges()`, `stagedCount()`, `value(setting)`, `current()`.
- `Page` (title + slots map; slot constants), `Entry` (icon, name, lore, glowing, handler), `Slot` (index, icon, name, lore, glowing),
  `ClickType` (LEFT RIGHT SHIFT_LEFT SHIFT_RIGHT MIDDLE DROP OTHER), `MenuAction` (kinds NONE REFRESH CLOSE SAVED DENIED ERROR
  TEXT_INPUT; `closesMenu()` true for CLOSE/SAVED/DENIED), `TextRequest(key, purpose SET_VALUE|ADD_ITEM, prompt, hint, masked)`,
  `Icons` (abstract icon name constants), `ConfigAccess` (registry/current/save) with `EngineConfigAccess`.

### Layout and behaviour
- 54 slots: 0-44 content, nav row 45 back, 46 prev, 47 add (list view), 48 cancel, 49 page/unsaved info, 50 Done, 51 reset list
  (list view), 52 next, 53 main menu. Only occupied slots are returned by `render`.
- Main menu = one button per category that has at least one `Setting` (sections are not shown); category page = its settings.
- Entries: BOOLEAN toggle (left/right); INT/DOUBLE/DURATION left +step, right -step, shift x10, clamped to min/max; ENUM left next,
  right previous (wraps); STRING and SECRET -> `TEXT_INPUT` (secret request has `masked=true`, value never in lore, name or errors);
  STRING_LIST -> sub-menu (click an item removes it, slot 47 asks for a new item, 51 resets the list; paginated).
  MIDDLE or DROP on any setting = reset to default. Staging the loaded value again removes the staged change.
- Steps: INT 1; DOUBLE 0.01/0.1/1 by range size (or default size without range); DURATION 1s / 1m / 10m by max (or default). Constant per setting.
- Done: validates every staged value with `Setting.require`, writes via `engine.config().set` for each and reloads ONCE
  (`EngineConfigAccess.save`), returns `SAVED` ("Saved N settings..."); failure returns `ERROR` and keeps the changes.
  Cancel discards and returns `CLOSE`. Title gets " *" while there are unsaved changes.

### Decisions
- Page index is clamped when a page is built (removing the last item of the last page falls back one page).
- Empty secret input is refused (use reset to clear). Item/value display is cut at 60 characters.
- A closed menu (`SAVED`/`CLOSE`) keeps its view stack; platforms drop the `MenuState` on close.
- Per-setting edit rights were not added (only `antibadwords.gui`).

### Left for later tasks
- Special pages (languages, word browser, ladder, mutes, profiles), main-menu status items, search, text prompts (B2), renderers (B3),
  entry points and permission registration in plugin.yml/Forge/Fabric (B4), bot tests (B5), docs (B6).
