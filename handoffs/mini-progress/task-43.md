## Task 43: Fabric/Forge mods: GUI lore, text prompt, hasPlugin, Placeholder API

Verification: signatures read from the `mc-api-dump` job logs of all six Minecraft versions, Fabric and Forge (saved copies of
the earlier dump run, which has the same classes: ItemLore, SignBlockEntity, AnvilMenu, ...). `tools/stub-compile.sh` PASS for
every combination (plugin, 3 Fabric, 3 Forge). Engine tests run. Nothing ran on a real server.

| Item | State | Versions |
|---|---|---|
| A Chest menu lore | done: `DataComponents.LORE` + `new ItemLore(List<Component>)` in `ChestBackend.stack`; the 1-argument constructor is identical in all dumps (26.3 only adds a 2-argument one) | all 6 |
| B Sign prompt (fake sign packets) | not possible: a fake sign needs `Blocks`, `BlockState` (`defaultBlockState`), `CompoundTag`/`BlockEntity` construction; none of these classes is in the dumps, so nothing was guessed | none |
| B fallback: anvil rename prompt | done: `Platform.supportsSignPrompt()` is true, `openSignPrompt` opens `AnvilPrompt` (server-side `GuiAnvilMenu extends AnvilMenu`, paper in the input slot named with the hint). The player types in the name field and clicks the result item; the text goes to `engine.prompts().onSignInput(uuid, [text])`. Escape or close = cancel (empty answer). Slots are emptied before close (nothing is given back), the result is never taken (no XP), clicks on anvil slots are swallowed, shift-click is disabled, the anvil name scan (`AnvilMenuMixin`) skips this menu | all 6 (`clicked` per variant: `menu-click` 1.21.11, `menu-input` 26.x) |
| C `Platform.hasPlugin` | done: Fabric `FabricLoader.getInstance().isModLoaded(id)`; Forge `ModList.get().isLoaded(id)` on 1.21.11 and static `ModList.isLoaded(id)` on 26.1-26.3 (the dumps show no `get()` there) via the new variant `forge-modlist-instance|static`. Name mapping in `ModPluginIds` (default = lower-case name; known: PlaceholderAPI -> `placeholder-api`) | all 6 |
| D Fabric Text Placeholder API | done for 26.3 only: `%antibadwords:<name>%` for every `PlaceholderValues.NAMES` entry (`Placeholders.registerCommon(Identifier, handler)`), registered only when mod `placeholder-api` is loaded. Compile-only dependency `eu.pb4:placeholder-api:3.2.0+26.3` from https://maven.nucleoid.xyz. Other versions use the `placeholders-none` variant (empty hook) because no matching library version is verified | 26.3 (others: fallback, no placeholders) |
| D Styled Chat | documented only: Styled Chat changes how chat is formatted on the vanilla chat path. We filter on `ServerMessageEvents.ALLOW_CHAT_MESSAGE` (Fabric) and `ServerChatEvent` (Forge), before formatting, so no code is needed. Not run with the mod installed | - |

### Wiring
- New Gradle properties: Fabric `placeholders_api` (`api|none`) and `placeholder_api_version`; Forge `modlist_api` (`static|instance`). Both
  `build-all-versions.sh` (extra columns), both `test-on-real-server.sh`, both `build.gradle`/`gradle.properties` and `tools/stub-compile.sh` follow.
- New stub roots `tools/api-stubs/mc-modlist-instance` and `mc-modlist-static` (the two `ModList` shapes cannot live in one class).
  The `eu.pb4` stubs guess the handler method name `onPlaceholderRequest`; the code only uses a lambda, so the name is not used.
- `fabric.mod.json` now suggests `placeholder-api`.

### Text for the docs task (task 48)
- README / CHANGELOG: "Fabric and Forge: the menu items show help text as lore. The text input of the menu uses an anvil (type the value in
  the name field, click the result item) because a fake sign is not available. Set `gui.prompt.mode: chat` to keep the chat input.
  `/abw info` shows installed mods that match known plugin names. Fabric 26.3: `%antibadwords:strikes%` and the other placeholder names with the Text Placeholder API."
- Behaviour change to mention: `gui.prompt.mode: auto` now picks the anvil on the mods (it was the chat before).

### Open items
1. The engine message `gui.prompt.messages.sign` says "on the sign". `AnvilPrompt.open` therefore sends one extra line that says to use the anvil. Task 48 can add a platform-specific text. (The `PromptSettings` file is not owned by task 43.)
2. Placeholder API library versions for 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2: only `3.2.0+26.3` is verified (maven.nucleoid.xyz is blocked here and no dump exists). Add the versions to the Fabric table in `build-all-versions.sh` (columns `api|<version>`) and the placeholders appear.
3. To make a fake sign possible later: add `net.minecraft.world.level.block.Blocks`, `net.minecraft.world.level.block.state.BlockState`, `net.minecraft.nbt.CompoundTag`, `net.minecraft.world.level.block.entity.BlockEntity` and `net.minecraft.core.RegistryAccess` to `CLASSES` in `tools/dump-mc-api.py` (that file is not owned by task 43).
4. Not verified on a real server: the anvil result slot for a player with 0 XP (the server ignores the cost because the click is swallowed, but the client may not send the click when it cannot afford the item). Test with a survival player at 0 levels. Mod ids `styledchat`, `floodgate` in `ModPluginIds` are the expected ids, not read from a dump.
5. `ModPluginIds` has no unit test: `src/mc` is not on the Maven test path.
