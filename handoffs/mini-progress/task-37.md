## Task 37: Fabric/Forge gaps closed against the real API dumps (run 43667029230)

Verification: signatures read from the job logs (Fabric 1.21.11 + 26.3, Forge 26.3; Forge 1.21.11 was still running, assumed equal to Fabric 1.21.11 because Mojang names are shared); `tools/stub-compile.sh mods` passes (both variants); engine tests run. Nothing run on a server.

| Item | State | Versions |
|---|---|---|
| 1 Chest GUI (`ChestBackend`, `GuiChestMenu` per variant, `ModGui.install` in both loaders) | partly: names, glow, click blocking, close handling done; **lore not set** (`ItemLore` not in the dumps, added to `tools/dump-mc-api.py`); icons use only PAPER/BOOK/BARRIER/ARROW/NAME_TAG/WRITABLE_BOOK | 1.21.11 (ClickType) and 26.3 (ContainerInput); 26.1/26.1.1/26.1.2/26.2 assumed like the sign split (26.2 and older = ClickType) |
| 2 World/dimension (`player.level().dimension().identifier().toString()`) | done, no shim needed (same in both dumps); also `dimensionName` | all 6 |
| 3 Sign back (`SignLines.isBack`: 1.21.11 `!isFrontText()`, 26.3 `slot().toString()` equals "back") | done; 26.3 relies on `SignTextSlot.toString()` giving BACK (class not dumped, added to list) | all 6 |
| 4 Command blocks (`handleSetCommandBlock` mixin + `BaseCommandBlockMixin` on `performCommand`) | done | all 6 |
| 5 IP (`player.connection.getRemoteAddress()`) | done | all 6 |
| 6 Sign prompt | not done, chat prompt stays: `ClientboundBlockUpdatePacket`, `ClientboundOpenSignEditorPacket`, `SignBlockEntity` are not in the dumps (added to the list) | - |
| 7 hasPlugin | not done: `FabricLoader.isModLoaded` / `ModList.isLoaded` not in dumps (`FabricLoader`, `ModList` added to list) | - |
| 8 Styled Chat / Placeholder API | documented as optional only, no classes in dumps | - |

Wiring: new Gradle property `menu_api` (`click` | `input`), source dir `src/mc-variants/menu-<api>`, extra field in both `build-all-versions.sh` VERSIONS lines and the test scripts; `tools/stub-compile.sh` pairs sign-array/menu-click and sign-record/menu-input. Stubs extended in `tools/api-stubs/mc`.

Docs task: remove the "world name not known on mods" Limits sentence in `GenerateDocs` (line ~193), CHANGELOG and FABRIC-AND-FORGE-STATUS, and the same for sign-back, command blocks and IP; GUI lore and sign prompt remain open.
