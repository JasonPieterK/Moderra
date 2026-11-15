# Summary of the handoffs (version 2.0)

This summary uses ASD-STE100 Simplified Technical English. Each sentence gives one fact or one instruction.

## General data

- The work is on the branch `claude/eager-carson-fylodx`.
- Session 1 used Sonnet 5.5.
- Session 2, mini tasks 1 and 2, and the first attempt of mini task 3 used Fable 5.2. The Fable 5.2 limit then stopped.
- Session 3 used Fable 5.2 and, after one usage limit, Opus 5 as the fallback model.
- From mini task 3 onwards, all agents used Sonnet 5.5, as the owner instructed.
- Sessions 4 and 5 were divided into small tasks. Then more tasks for integrations, gaps and risks followed.
- The development container cannot connect to the Minecraft build servers. GitHub Actions builds the jars and checks the APIs.
- The engine has 410 unit tests. All tests pass.
- GitHub compiled all 24 jars against the real APIs. The result is a pass.
- Nobody did a test on a real server. Nobody did a test with the test bot. You must do these tests.

## Session 1 (easy): word lists

- The agent made the folder `7-wordlists/`.
- The folder has 37 language files and one file for custom words.
- The agent wrote a validator (`validate.py`) and a statistics tool (`stats.py`).
- All words from the old 7 lists are in the new files.
- The agent read the LDNOOBW licence. The agent did not copy words from LDNOOBW.
- An AI wrote all lists. No native speaker examined the lists.
- Later, mini task 25 added words to the short lists (`sw`, `fil`, `he`, `bn`).
- Mini task 25 also added a false-positive test. This test checks normal sentences in all 37 languages.

## Session 2 (medium): engine and configuration

- The agent moved the shared code to `5-shared-engine/`.
- All four server types now use one engine.
- The engine has a settings registry. The registry makes the config files and `6-guides/CONFIGURATION.md`.
- The configuration is now a folder with many files.
- An old `config.yml` changes to the new format automatically. The plugin keeps a backup of the old file.
- The agent added world profiles (`worlds.yml`).
- The agent added `/moderra lang` and `/moderra whitelist`.
- A test shows that an old install gives the same results after the upgrade.

## Session 3 (medium): new filters

- The agent added an evasion filter. It finds invisible characters and letters that look like other letters.
- The agent added a spam filter. It has six rules.
- The agent added a link filter. It also finds hidden links and IP addresses.
- The agent added more sources: player names at join, the back side of signs, and command blocks.
- A fast word index makes the check quick. One check takes less than 0.3 ms at the 99th percentile.
- Most new filters are off by default. This keeps the old behaviour.

## Session 4 (hard): moderation

- Mini task 1 added strikes that the plugin keeps after a restart. It also added a list of known players.
- Mini task 2 added a duration parser and an escalation ladder.
- Mini task 3 changed the old punishment setup into a one-step ladder. It added `/moderra ladder` and `/moderra strikes`.
- Mini tasks 4 and 5 added a built-in mute with `/moderra mute`, `/moderra unmute` and `/moderra mutes`.
- Mini tasks 6 and 7 added a history log with `/moderra history`. By default, the log does not keep chat text.
- Mini task 8 connected these features to the plugin and to the mods. It added `6-guides/MODERATION.md`.
- Mini task 8 also repaired a test that failed at random.

## Session 5 (hard): Discord and the in-game menu

- Mini tasks 9 and 10 added Discord webhook alerts. The plugin hides all webhook URLs in logs and messages.
- Mini tasks 11 and 12 added a DiscordSRV connection for Bukkit. It filters messages from Discord to Minecraft.
- Discord commands use the prefix `!moderra`. Only listed roles or users can use them.
- Mini tasks 14 and 15 added the in-game menu model, special pages and a search.
- Mini task 16 added text input through chat.
- Mini task 17 added the chest menu for Bukkit.
- Mini task 18 and mini task 37 added the chest menu for Fabric and Forge.
- Mini task 24 added editors for the ladder and the world profiles. It added edit permissions for each category.

## More tasks after the five sessions

- Integrations (mini tasks 21 to 30): EssentialsX, EssentialsX Chat, PlaceholderAPI, LuckPerms, Vault, ban plugins, Geyser and Floodgate, TAB, NametagEdit, chat-channel plugins, ProtocolLib, CoreProtect, Plan, Velocity and BungeeCord network sync, mcMMO, Towny, Factions, Citizens and DecentHolograms.
- Mini task 31 wrote all player messages again. The messages are now formal and have colours, bold text and italic text.
- Mini task 32 made the whitelist work for Chinese, Japanese, Korean and Thai. It added the IP address for IP bans. It added a censor function for Discord messages.
- Mini tasks 33 and 35 added a GitHub check. This check compares our API names with the real plugin jars. All 64 names pass.
- Mini task 34 added network sync through plugin messages. It also changed the version to 2.
- Mini task 36 put all integration settings in one package.
- Mini task 37 closed gaps on Fabric and Forge: world names, the back side of signs, command blocks and the IP address.
- Mini tasks 38 and 39 added the ProtocolLib packet scan, the Plan extension and the TownyChat channel event.
- Mini task 40 wrote the final guides and did a review of all permissions.
- Mini task 41 repaired the compile errors on GitHub. Then all 24 jars compiled.
- The licence is now "All Rights Reserved".

## Items that are not complete

- The item lore in the Fabric and Forge chest menu is not available.
- The sign-editor prompt is not available. The chat prompt replaces it.
- `hasPlugin` on Fabric and Forge is not available.
- PacketEvents, DiscordSRV slash commands and mcMMO party chat are not available.
- The events of CarbonChat, ChatControl and FactionsUUID are not available.
- VentureChat, HuskChat, TAB and NametagEdit have no usable events. The plugin scans their commands only.

## Instructions for you

1. Download the jars from the GitHub release `v1.0` (the first Moderra release; `v2.0.0` was the tag under the old name).
2. Install the jar on a test server. Use a copy of your world.
3. Do a test of chat, signs, books, anvils, mute, the ladder and the menu.
4. Do a test of each plugin that you use with Moderra.
5. Read `handoffs/BUILD-VERIFICATION-TODO.md`. It has the full list of tests.
6. Tell a native speaker to examine the word lists of your languages.
