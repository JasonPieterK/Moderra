## Mini task 30: mcMMO, Towny, Factions, Citizens, DecentHolograms (command text scanning)

Verification: engine unit tests run (`mvn -B test`: 375 tests, 4 new in `engine/integrations/PluginCommandsTest`; `generate-docs.sh` run). No plugin glue was needed
(the Bukkit command listener already feeds command lines to the engine; `Platform.hasPlugin` does the detection). Nothing was run against the real plugins.

### Added
- `engine.integrations.PluginCommandSettings` (file `integrations.yml`, category "Integrations"): per plugin `integrations.<mcmmo|towny|factions|citizens|decentholograms>.enabled` (true)
  and `.scan-commands` (list). Entry = command label plus optional sub-words ("npc rename"); text after it is scanned like chat; a bare label ("tc") scans all arguments.
- `engine.integrations.PluginCommands` (`engine.pluginCommands()`): `active()`, `logFound()` (one startup line when something is found), `extraCommandText(ignored, line)`.
  `Engine.judge` calls it after the Essentials extras; `commands.ignored-commands` wins; inert on Fabric/Forge (`hasPlugin` false).
- `CoreCommands.KNOWN_PLUGINS` += mcMMO, Towny, Factions, FactionsUUID, Citizens, DecentHolograms. `plugin.yml` softdepend += same names.
- Defaults (all from memory, NOT verified against the plugins; editable): mcMMO `p pc a ac party rename|create|password`; Towny `tc nc lc gc townchat nationchat localchat generalchat`,
  `town|nation new|rename|set board|set tag`; Factions `f create|tag|desc|description|motd|title|name|rename`; Citizens `npc create|rename|name`;
  DecentHolograms `dh|holo create|line add|line set|line insert` (and `dh lines ...`). Detected plugin names for Factions: Factions, FactionsUUID, SaberFactions, FactionsKore.

### Decisions
- Command based only: no plugin events are hooked (no API names guessed). Toggled party/town/nation/faction chat modes go through AsyncPlayerChatEvent, which our existing
  chat handler already filters when those plugins use it; plugins that send chat via their own events (e.g. mcMMO party chat events) are NOT covered beyond the commands. Unverified.
- Hologram name is scanned together with the line text (arguments after the sub-words).
- Needs `filters.scan.commands` on (the existing switch).

### Stub members needed
None (no new Bukkit glue).

### Docs section (copy)
"Integrations: mcMMO, Towny, Factions, Citizens and DecentHolograms - when installed, the text of their party/town/nation chat commands, names, boards, NPC names and
hologram lines is checked like chat. Edit `integrations.<plugin>.scan-commands` in integrations.yml; switch off with `integrations.<plugin>.enabled: false`. Bukkit family only."
