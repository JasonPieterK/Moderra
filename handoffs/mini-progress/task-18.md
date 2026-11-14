## Mini task 18: Fabric/Forge GUI glue, chat prompt capture, /abw gui (B3 mod part, B4, B2)

Verification: engine unit tests run (`GuiCommandTest`, 3 new tests); mod code stub-compiled with javac (sign-record and sign-array), `ModGui.mapClick` run once; no real Minecraft API checked.

### Added
- Engine: `Platform.supportsMenu()` (false) and `openMenu(UUID)` (false), additive defaults. `command/GuiCommand` (`/abw gui`, permission `antibadwords.gui`, registered in `Engine`).
  `CommandRegistry.execute`: bare `/abw` by a player with `antibadwords.gui` on a platform with `supportsMenu()` opens the menu; console, players without the permission and platforms without a menu keep the usage line. `/abw gui [page]`: the page argument is ignored (menu always opens on the main page).
  `FakePlatform` got `menus` / `menusOpened`.
- Mods (`src/mc/java/.../mod/gui/`): `GuiBackend` (3-method interface, documented contract), `ModGui` (sessions per player, `open`, `onClick(player, slot, vanillaClickTypeName, button)`, `onClosed`, `onQuit`, text prompts via `engine.prompts()` with the menu closed during the prompt and reopened after, `mapClick`, `install`, `available`).
- `ModRuntime.blockChat`: `verdict.captured()` returns true (cancel) before `onViolation`; `ModPlatform` implements `supportsMenu/openMenu`; `onQuit` calls `ModGui.onQuit`; `ModRuntime.onlinePlayer(UUID)` is public.
- `ForgePermissions`: node `antibadwords.gui` (op default via the shared fallback). Fabric needed nothing (generic node check).
- No stub additions were needed (assumption list: none).

### Decisions
- The vanilla chest menu itself (`GuiBackend` implementation) is NOT written: the ChestMenu/ItemStack/DataComponents/openMenu APIs cannot be verified for 1.21.11 and 26.x. `ModGui.available()` is false until a loader installs a backend, so mods behave as before. Exact APIs to check are in `handoffs/BUILD-VERIFICATION-TODO.md` (section "Mini task 18").
- Click types travel as vanilla enum names so `ModGui` has no vanilla enum dependency.

### Left
- ChestBackend per version + `ModGui.install` call in both loaders; sign prompt on mods; `plugin.yml` permission (Bukkit task); docs.
