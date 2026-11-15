# Task 48: remaining texts in the formal style, jars to GitHub Releases, docs merge, version 2.1

Verification: engine unit tests run (all green), `tools/stub-compile.sh` all PASS, `7-wordlists/tools/validate.py` exit 0.
The pom and gradle version properties were NOT built (no network to the build hosts): the next `[build]` run must prove them.

## Decisions
- Console log lines use the same sentences without colour codes ("Area: error. The plugin cannot ..."). Colour codes in a log are noise.
- Discord webhook templates are plain sentences without a prefix (the sender name and the card title or `[kind]` tag name the event). Bot replies start with `[AntiBadWords] `.
- GUI: names gold (`§6`), lore gray (`§7`) are added where the item is drawn (`GuiManager.item`, `ChestBackend.stack`). `MenuAction.error/saved/close` now return the full prefixed text (`Chat.error/ok/info`); the adapters no longer add colours. Titles are unchanged (32-character limit).
- Version: `project.version` / `mod_version` stay `2` (jar names). Display version 2.1 is `abw.display.version` (plugin pom, used by plugin.yml) and `display_version` (gradle.properties, used by `fabric.mod.json` and `mods.toml`).
- The ignore file now lists `1-download-jars/*.jar`.
