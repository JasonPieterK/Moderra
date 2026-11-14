## Mini task 17: Bukkit GUI renderer and entry points (session 5, B3 Bukkit part + B4 Bukkit part)

Verification level: javac against the hand-written Bukkit stub only (compiles clean, engine + plugin sources). No server run, no engine change (no engine tests touched).
Fabric/Forge not in scope.

### Added (`2-plugin-bukkit-purpur/.../gui/`)
- `GuiManager`: one session per viewer; draws `Menu.render` into a 54-slot chest (`Bukkit.createInventory(holder, 54, title)`, title clipped to 32 chars), new inventory when the title changes (the " *" unsaved marker), otherwise clear + refill. Handles every `MenuAction` (REFRESH, ERROR message, CLOSE/SAVED/DENIED close + message, TEXT_INPUT).
  Close by the viewer calls `Menu.closeRequested` (unsaved changes: confirm page reopened one tick later). Quit and plugin disable call `Menu.closed` / close all. `/abw gui [page]`: page is reached by clicking the model's own next-page slot (found by icon `next_page`), because the model has no "go to page" call.
- `GuiListener`: `InventoryClickEvent` and `InventoryDragEvent` cancelled at LOWEST for every inventory whose top inventory is ours (any click type, shift, number key, double click, drop, drag, clicks in the player's own inventory); only then mapped to engine `ClickType` (LEFT/RIGHT/SHIFT/MIDDLE/DROP, everything else OTHER = no effect). Close and quit events.
- `GuiIcons` + resource `gui-icons.yml` (in the jar, pom resource added): icon name to `Material.matchMaterial` name, `fallback: PAPER`; admin copy `plugins/AntiBadWords/gui-icons.yml` overrides single keys; read again on every menu open; unknown/air/non-item material gives the fallback. New engine icon names just work (fallback) until mapped.
- `MenuHolder`: marks our inventories.
- Text prompts: TEXT_INPUT closes the chest and calls `engine.prompts().start` with a `TextPrompt.of(...)` (validator = `Setting.require`, secrets never echoed); answer goes to `Menu.submitText`, cancel/timeout to `Menu.cancelText`, then the chest reopens. Quit calls `prompts().onQuit`.
- `AntiBadWordsListener.onAsyncChat`: `verdict.captured()` is checked right after `inspect`: cancel, return (no broadcast, no censor, no `onViolation`).
- Entry: bare `/abw` by a player with `antibadwords.gui` opens the menu; console and players without the permission keep the engine's text behaviour. `/abw gui [page]` (player + permission; console gets a message; no permission gets "No permission."). Tab completion offers `gui` to permitted players. `AntiBadWordsCommand.wrap` is now public static. `plugin.yml`: `antibadwords.gui` (default op).

### Decisions
- Sign prompt NOT implemented: `supportsSignPrompt()` stays false (comment in `BukkitPlatform`). Paper's `Player#openSign(Sign, Side)` needs a placed sign block in the world and is not Bukkit/Spigot API; a temporary block is too risky. Chat prompt is the Bukkit path.
- Used `Enchantment.LURE` + `ItemFlag.HIDE_ENCHANTS` for the glow (stable names across versions); `HIDE_ATTRIBUTES` too. Not verified on a real server: on 1.20.5+ item tooltips may need `HIDE_ADDITIONAL_TOOLTIP` (cosmetic).
- Inventory title via `String` overload of `Bukkit.createInventory` (works on Spigot and Paper, deprecated on newest Paper).

### Left / for BUILD-VERIFICATION
- Real-server check (click cancelling, number keys, chat prompt round trip, confirm page reopen, icon names per version, ItemFlag names on 1.21.x) is not done. Bot tests (B5) and docs (B6) belong to later tasks.
