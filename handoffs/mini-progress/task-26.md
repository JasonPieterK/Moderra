## Mini task 26: Vault, TAB, NametagEdit, Fabric-side notes - engine tests run, Bukkit part stub-compiled only

Verification: `mvn -q -B test` in 5-shared-engine (3 new tests in `NameplateTest`); `tools/stub-compile.sh plugin` PASS (the repo Bukkit stub was fixed first, separate commit: added `PluginManager#registerEvent`, `Player#getMetadata`, `PluginDescriptionFile#getAuthors`, `AsyncPlayerChatEvent#getRecipients` + `extends Event`). Never run against Vault, TAB, NametagEdit or a server. No new stub members needed for this task (VaultHook is fully reflective).

### Added
- Engine `engine.integrations`: `NameplateSettings` (in `integrations.yml`, category "Integrations"), `PluginCommandScan`.
- `PermissionSource#prefix(UUID)` (default method, empty): Vault Chat prefix / LuckPerms meta prefix. `LuckPermsHook` implements it (`getCachedData().getMetaData().getPrefix()`).
- Settings: `integrations.vault.enabled` (true), `integrations.vault.message-placeholders` (true), `integrations.tab.enabled` / `.scan-commands` (default `[tab]`), `integrations.nametagedit.enabled` / `.scan-commands` (default `[nte, nametagedit]`).
- Messages: `{prefix}` and `{group}` are available in the lines people read (ViolationHandler `shown()` values; never in commands). Empty without a permission source. Works with LuckPerms too.
- TAB/NametagEdit: their commands are scanned (arguments checked like chat) only while the plugin is installed and its switch is on; `commands.ignored-commands` wins. Hooked into the existing command-scan fallback in `Engine` next to Essentials.
- Plugin: `integrations/VaultHook` (reflection: ServicesManager -> `net.milkbowl.vault.permission.Permission` / `chat.Chat`), started by `startVault()` only when LuckPerms is not connected; `plugin.yml` softdepend + Vault, TAB, NametagEdit; `CoreCommands.KNOWN_PLUGINS` + Vault, TAB, NametagEdit.
- Vault profiles: the existing `integrations.luckperms.group-profiles` map is used (primary group / inGroup). Vault has no meta and no offline checks (offline returns false).

### Decisions / unverified
- Vault API is NOT on Maven Central (net.milkbowl.vault:VaultAPI and com.github.MilkBowl:VaultAPI both 404), hence reflection. Method names from memory: `getPrimaryGroup(String, OfflinePlayer)`, `playerInGroup(String, OfflinePlayer, String)`, `getPlayerPrefix(String, OfflinePlayer)`; a failing lookup switches that part off with one log line.
- Only online players are asked (world null = global).
- TAB / NametagEdit: no events used (API names not certain). Instead of changing `filters.scanned-commands` defaults (would not reach old installs and needs a test/migration change), their commands are added at runtime while installed. TAB's own scoreboard/tab text from config files is not scanned.
- Not done on Fabric/Forge: Styled Chat, Text Placeholder API (eu.pb4) and LuckPerms-Fabric need APIs that are not in the repo; nothing guessed. Mods keep using the permission nodes they already use (LuckPerms-Fabric works through those). Add to BUILD-VERIFICATION-TODO: Styled Chat chat-event hook and Text Placeholder API placeholders (`%antibadwords:strikes%`-style) need real eu.pb4 jars.

### Docs section (copy for the final docs task)
- Vault: when LuckPerms is absent, groups from Vault drive `integrations.luckperms.group-profiles`; `{prefix}`/`{group}` usable in warning/broadcast/staff messages.
- TAB / NametagEdit: their commands (`/tab ...`, `/nte ...`) are scanned while installed; edit the lists under `integrations.tab` and `integrations.nametagedit`.
- Fabric/Forge: no Vault/TAB/NametagEdit; Styled Chat and Text Placeholder API not supported yet.
