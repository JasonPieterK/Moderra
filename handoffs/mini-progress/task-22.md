## Mini task 22: PlaceholderAPI and LuckPerms (Bukkit family) - engine unit tested, Bukkit classes stub-compiled only

Verification level: engine unit tests run (`mvn -q -B test`: 13 new in `IntegrationsTest`, whole suite green; `generate-docs.sh` run).
Bukkit part compiled with `javac --release 21` against the scratchpad stub `bs22` (copy of `bs17` plus `OfflinePlayer`, `Plugin#isEnabled`,
`Bukkit.getPluginManager`), the PlaceholderAPI shim and the REAL LuckPerms API jar `net.luckperms:api:5.4` (Maven Central; signatures were
checked with `javap`). Never run against a real PlaceholderAPI or LuckPerms; the Maven build of the plugin was not run (Spigot repo blocked).

### Settings (file `integrations.yml`, category "Integrations", class `engine.integration.IntegrationSettings`)
`integrations.placeholderapi.enabled` (true), `.parse-in-messages` (false); `integrations.luckperms.enabled` (true), `.use-meta` (true),
`.meta-key` (`antibadwords-profile`), `.primary-group-only` (false), `.offline-permission-checks` (true); section
`integrations.luckperms.group-profiles` (map group -> profile, file order counts, default `{}`).
MERGE NOTE: the EssentialsX agent also owns `integrations.yml`. `IntegrationSettings.register` sets the file header only if none exists
yet; `Engine.registerBuiltIns` got one line `IntegrationSettings.register(registry);`; `SettingsRegistryTest` got the file `integrations.yml` and
category "Integrations" appended (the other agent will add the same, keep ONE copy each). `6-guides/CONFIGURATION.md` is regenerated: regenerate after the merge.

### Engine
- `engine.integration.PermissionSource` (interface a permission plugin implements): `name()`, `primaryGroup(uuid)`, `inGroup(uuid, group)`,
  `meta(uuid, key)` (all from memory, safe on the chat thread), `hasPermission(uuid, node)` -> `CompletableFuture<Boolean>` (offline capable, never fails).
  `engine.setPermissionSource(..)` / `engine.permissionSource()`. Not on `Platform`, so mods are untouched.
- `ExternalProfiles.choose(...)` (pure logic) + `validate(...)` (reload warning for a mapped profile that does not exist).
- Profile order: permission `antibadwords.profile.<name>` > LuckPerms meta > group map > world rules > default. `WorldProfiles.resolve(world, dim, perms, external)`
  (old 3-argument method kept), `Engine.settings(UUID player, world, dim, perms)` (old 3-argument overload kept; `inspect`/`judge`/`LadderCommands` use the new one).
  A throwing source never breaks checking (logged, profile falls back).
- `engine.hasPermission(uuid, node)`: online -> `Platform.has`; offline -> the source (if `offline-permission-checks`), else false. Used by `/abw mute`
  to warn that an OFFLINE target has `antibadwords.bypass.mute` (answer comes back through `Platform.runSync`).
- `engine.placeholders()` -> `PlaceholderValues.resolve(uuid, name)`: `strikes`, `strikes_next` (strike count of the next step), `next_step` (step name), `muted`,
  `mute_remaining`, `mute_reason`, `profile`, `violations_total` (history count, cached 30 s and refreshed with `runAsync`; first call may answer 0),
  `protection_level`. Unknown name -> null. Unknown/offline players work (profile by default rules). Never chat text, word lists or secrets.

### Bukkit (`2-plugin-bukkit-purpur`)
- `PlaceholderApiHook` (+ `AbwPlaceholderExpansion extends PlaceholderExpansion`, compiled against the name-only shim `src/papi-shim/java`, excluded from the jar by
  `<exclude>me/**</exclude>`, added in `pom.xml` as build-helper source `add-papi-shim`). The expansion class is only loaded when PlaceholderAPI is installed and enabled.
  Optional parsing of OTHER plugins' placeholders in messages sent to one player (`BukkitPlatform.setMessageParser`, reflection on
  `PlaceholderAPI.setPlaceholders(OfflinePlayer, String)`); broadcasts are never parsed. Default off because text a player typed could contain `%x%`.
- `LuckPermsHook implements PermissionSource` uses the LuckPerms API directly (`pom.xml`: `net.luckperms:api:5.4`, scope provided). It is loaded only after
  `AntiBadWordsPlugin.startLuckPerms` found the enabled plugin, inside try/catch Throwable. Group test = permission `group.<name>` (inheritance and contexts aware),
  primary group from cached meta data, meta from `getCachedData().getMetaData().getMetaValue(key)`, offline via `UserManager.loadUser(uuid)` (future, never joined).
- `plugin.yml`: `softdepend: [DiscordSRV, PlaceholderAPI, LuckPerms]` (the DiscordSRV agent edits the same line: merge to one list). Startup logs what was found
  (one line each for connected / switched off / failed; nothing when the other plugin is not installed).

### Fabric and Forge (read only, nothing added)
Fabric: `FabricPermissions` already asks the Fabric Permissions API (`me.lucko.fabric.api.permissions.v0.Permissions#check`, implemented by LuckPerms on Fabric) for every node,
including `antibadwords.profile.<name>` and `antibadwords.bypass.*`. Forge: `ForgePermissions` registers the nodes with Forge's `PermissionAPI` (LuckPerms Forge answers them);
profile nodes are collected at server start. So on mods: profile by LuckPerms group = grant `antibadwords.profile.<name>` to the group; no meta, no group map, no PlaceholderAPI,
offline permission checks answer false. The `integrations.*` keys exist in the files there and do nothing. Fabric "Text Placeholder API" (eu.pb4:placeholder-api) is not in the
repo: future work only.

### For the docs task
Document: the settings above, the profile order, the placeholder list (all `%antibadwords_<name>%`), `/lp group vip meta set antibadwords-profile relaxed`,
`/lp group vip permission set antibadwords.profile.relaxed true` (works everywhere), the privacy note on `parse-in-messages`, the mod limits.

### Not done / unsure
- Not run on a real server. API names used by reflection/shim from memory: `PlaceholderExpansion#getIdentifier/getAuthor/getVersion/persist/onRequest(OfflinePlayer,String)/register/unregister`,
  `PlaceholderAPI.setPlaceholders(OfflinePlayer,String)`. A mismatch gives a single warning line; the plugin keeps working.
- `PlaceholderExpansion#register()` can return false when another jar already registered `antibadwords`: logged.
- No `/abw info` line for the hooks yet.
