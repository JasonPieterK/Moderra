## Mini task 24: gaps that need no blocked API (GUI editors, edit rights, page argument, Discord/strikes fixes, stubs, /abw info)

Verification level: engine unit tests run (`mvn -q -B test`: 333 tests, 0 failures; new `GuiEditorTest` 11, `Task24Test` 7, one
assertion added to `DiscordLinkTest`, two old read-only GUI tests rewritten). Plugin and both mod layers: `tools/stub-compile.sh`
passes (hand-written stubs only; nothing was run, nothing compiled against the real APIs).

### 1. GUI
- **Ladder editor** (`engine.gui.EditorPages`): ladder page lists the steps (sorted by `at`), click = step page, shift-right = delete step,
  slot 47 "Add step" (name prompt; new step starts one above the highest `at`, `repeat: once`, a default message, action `warn`, and opens for editing).
  Step page: At (left/right +-1, shift x10), Repeat (once / every N, middle or drop = once), Message, Broadcast, Final command (text prompt, `-` clears),
  Delete step, then the action list (click = replace text, shift-left = move up, shift-right/middle/drop = remove), slot 47 "Add action"
  (checked with `Ladder.action`, so an unknown action or `mute` without time is refused by the prompt).
- **World-profile editor**: profiles page (click = edit, shift-right = delete; `default`, the default profile and profiles used by rules cannot be deleted),
  "Add profile"; profile page lists the overrides (boolean = toggle, numbers = step, enum = cycle, others = text prompt; middle/drop/shift-right = remove),
  "Add setting" (`<key> <value>`, lists comma separated; only overridable registry settings). **World rules page** (new main-menu button "World rules",
  `Kind.RULES`): add `<pattern> <profile>` (pattern may contain spaces), left/right = cycle profile, shift-left/right = move up/down (first match wins, order is kept
  in the file), middle/drop = remove.
- Staging: `MenuState.sectionValue/stageSection` keep private deep copies of the raw sections; counted in `hasUnsavedChanges/stagedCount`; written on Done together with
  the settings by `ConfigAccess.save(changes, sections)` (new default method; `EngineConfigAccess` writes each section as a whole with the comment-preserving editor
  and reloads once). Comments INSIDE a replaced section are lost, everything else in the file stays (tested). Before writing, `EditorPages.problem` re-reads the staged
  sections with the real parsers (`Ladder.parse`, `WorldProfiles.parse`); any problem stops the save, shows it and keeps the changes.
  New API: `Engine.rawSection(Section)`, `WorldProfiles.flatten` is public, `Menu.step/cycle` package visible, `TextRequest.Purpose` has 8 new values (adapters do not need
  to know them: both adapters now validate prompts with `Menu.validateText`).
- **Edit rights**: permission `antibadwords.gui.edit.<category>` (`Menu.editNode(category)`: lower-case, other characters -> `-`, e.g. `antibadwords.gui.edit.spam`,
  `.ladder`, `.worlds`, `.languages`). Checked on EVERY click that changes something (setting entries, list add/remove/reset, language toggles, custom words, all editor
  clicks), on text answers, and again for every staged change at Done (rights removed while the menu is open). Viewing is always allowed; the lore says when a setting
  cannot be changed. The engine just calls `CommandSender.has`; "default true for holders of antibadwords.gui" is the platform's job: Bukkit registers one node per category with
  `PermissionDefault.TRUE` after every reload (`AntiBadWordsPlugin.registerProfilePermissions`), mods return true from `ModPermissions.fallback` for the prefix (Fabric passes
  the fallback to the permissions API, Forge uses the fallback for unregistered nodes). Wildcards (`antibadwords.gui.edit.*`) are the permission plugin's business.
  `Menu.editNodes(access)` lists all nodes. Tests with a viewer must answer true for `Menu.EDIT_PREFIX` nodes (the existing GUI tests were adjusted).
- **Page argument**: `Menu.open(Engine|ConfigAccess, String page)` and `Menu.opensPage(...)`. Page = empty, a number (main menu page), category name (case, spaces and `-`
  ignored; unique prefix/part is enough; optional trailing page number), `languages|words|ladder|mutes|profiles|rules`, `search <text>`, or a setting key (lands on its category page).
  `Platform.openMenu(UUID, String page)` (default delegates to `openMenu(UUID)`), `GuiCommand` passes the joined arguments, tells the player about an unknown page and
  tab-completes. Bukkit `GuiManager.open(Player, String)` (the old `open(Player,int)` stays) and `ModGui.open(player, page)` both use it; the old "click next page" loop is gone.

### 2. Discord
- `CommandSender.suppressAudit()` (default false); the Discord command sender returns true so `CommandContext.audit()` publishes nothing for it: through Discord there is exactly ONE
  `admin-command` event per command (source `discord`, with user and channel), also for mute/unmute (test added). Decision: keep Discord's richer event, drop the command's own.
- `link.sources` and `evasion.detect.sources` defaults now include `discord-in` (new installs). `LegacyMigration.LEGACY_DEFAULTS` writes the old lists for migrated installs
  (`LinkSettings.LEGACY_SOURCES`, `EvasionSettings.LEGACY_DETECT_SOURCES`); tested both ways.

### 3. Stale strikes.json
Decision: set aside, never silently revive. When strikes are not kept (clear-violations-on-quit true, or save-to-disk false) an existing `data/strikes.json` is renamed to
`strikes.json.discarded` (one file, replaced next time, so nothing is lost for good, e.g. if the config was briefly unreadable) at start AND at shutdown, with a log line naming
the setting (`PersistentStrikeService.discardFile`). A kept file (clear-on-quit false + saving on) is read exactly as before. No stamp inside the file was needed. 5 tests.

### 4. Stubs
`tools/api-stubs/{bukkit,mc}` (copied from the scratchpad `bs17` and `mc-stub`, extended with `PluginManager.getPlugin/isPluginEnabled`, `Plugin.isEnabled/getDescription`,
`Bukkit.getPluginManager`, `JavaPlugin.isEnabled`), `tools/api-stubs/README.md` (says plainly: hand-written assumptions, not the real APIs), `tools/stub-compile.sh [plugin|mods]`
(javac, JDK 21, SnakeYAML from `~/.m2`; plugin + engine + DiscordSRV shim; Fabric and Forge x `sign-array`/`sign-record`). It passes: 5 of 5 targets.
The script found one REAL bug that the earlier ad-hoc compiles had missed: `ForgePermissions.has` had a duplicate `case "antibadwords.gui"` (compile error); fixed.

### 5. /abw info
`Platform.hasPlugin(String)` (default false; Bukkit: `isPluginEnabled`). `/abw info` now ends with "Integrations detected: ..." (from `CoreCommands.KNOWN_PLUGINS`: DiscordSRV, LuckPerms,
PlaceholderAPI, Essentials, LiteBans, AdvancedBan, floodgate) and "DiscordSRV features ... work on Bukkit/Paper/Purpur only; not available on Fabric/Forge". Other agents who add
integrations should add their plugin name to `KNOWN_PLUGINS`.

### Left for the docs task / later
- Docs: README/CHANGELOG/GUI guide: editors, the edit-permission list (one node per category; list from `Menu.editNodes`), `/abw gui <page>`, strikes file behaviour, `discord-in` defaults.
- Not verified on a real server: the Bukkit permission registration (default TRUE), the chest rendering of the new pages (they only use existing icons plus `rule`, mapped to COMPASS in
  `gui-icons.yml`; mods still lack a chest backend), the Bukkit `/abw gui <page>` path.
- Editors do not rename steps/profiles and do not edit `use-permission-profiles`/`default-profile` (those are normal settings in category "Worlds"). A profile cannot override the ladder steps from the GUI (edit worlds.yml).
