# Project summary (version 1.0)

**Moderra** is a chat filter for Minecraft servers. It blocks bad words in chat, commands, signs, books and anvil names,
and it punishes repeat offenders. It runs as a **plugin** on Bukkit, Spigot, Paper and Purpur, and as a **mod** on Fabric and Forge.
Current version: **1.0** (the first release under the name Moderra). Jar names: `Moderra<mc>-<version>-<loader>.jar`, for example `Moderra26.3-1.0-paper.jar`.
Before the rename the project had the version numbers 1 to 2.1 under its former name; "old version" in the documents means those.
Licence: All Rights Reserved. Author: Jason P.

## How it is built

| Folder | What it is |
|---|---|
| `5-shared-engine/` | The shared engine in pure Java: word matching, filters, strikes, ladder, mute, history, Discord, menu logic, config. All four server types use it, so they behave the same. Unit tests live here. |
| `2-plugin-bukkit-purpur/` | The thin plugin adapter (Bukkit, Spigot, Paper, Purpur) and the integrations with other plugins. |
| `3-mod-fabric/`, `4-mod-forge/` | The thin mod adapters. Shared Minecraft code is in `5-shared-engine/src/mc/`; per-version shims are in `src/mc-variants/`. |
| `6-guides/` | These guides. `CONFIGURATION.md` is generated from the code (`5-shared-engine/generate-docs.sh`). |
| `7-wordlists/` | One bad-word file per language (35 languages in 37 files), all first drafts marked `unreviewed`. |
| `1-download-jars/` | Only a README. The jars are on GitHub Releases. |
| `tools/` | Stub compile script, API dump and API check tools, `api-manifest.txt` (every third-party name the code uses). |
| `handoffs/` | Plans, progress notes and `BUILD-VERIFICATION-TODO.md` (the open list). |

## What Moderra 1.0 does

* **Filters:** word lists in 37 files, three strength levels, whitelist, look-alike and leetspeak tricks, spam and flood control, link filter, player names on join.
* **Moderation:** persistent strikes, a punishment ladder (warn, mute, kick, ban, tempban, ipban, command, effects), a built-in mute, a history diary, ban-plugin support, Bedrock names. Guide: [MODERATION.md](MODERATION.md).
* **Discord:** webhook alerts, DiscordSRV alerts, a Discord-to-Minecraft filter, `!moderra` text commands and slash commands. Guide: [DISCORD.md](DISCORD.md).
* **Menu:** every setting in a chest, with help text as lore, ladder and world editors, text input by chat, fake sign or anvil. Guide: [GUI.md](GUI.md).
* **Integrations (Bukkit family):** EssentialsX, PlaceholderAPI, LuckPerms, Vault, ban plugins, Floodgate/Geyser, TownyChat, mcMMO, TAB, NametagEdit, ProtocolLib, PacketEvents, CoreProtect, Plan, network sync. Guide: [INTEGRATIONS.md](INTEGRATIONS.md).
* **Outgoing-text scan:** checks chat, team and tab-list text that other plugins send (off by default).
* **Fabric and Forge extras:** `hasPlugin` by mod id, Text Placeholder API values on Fabric 26.3. Status: [FABRIC-AND-FORGE-STATUS.md](FABRIC-AND-FORGE-STATUS.md).
* **Messages:** one formal style (ASD-STE100) with one palette. Guide: [MESSAGE-STYLE.md](MESSAGE-STYLE.md). A message that still has the old default text is upgraded; a changed message is never touched.
* **Upgrade from an old version:** an old single-file `config.yml` is converted on the first start, with a backup.

## History in short

* **Old version 1** was a Bukkit plugin with one big `config.yml` (and separate Fabric and Forge ports). It got a full code review (23 bugs fixed), a new word matcher and hardened punishments. Its jars are no longer in the repository.
* **Old version 2.0** replaced the code base with the shared engine, the config folder, the word-list files, the new filters, the moderation tools, Discord, the menu and the integrations. All 24 jars were built on GitHub against the real APIs (tag `v2.0.0`).
* **Old version 2.1** closed the gaps of 2.0: menu lore and a sign or anvil prompt on the mods, `hasPlugin` on the mods, Fabric placeholders, PacketEvents scan, outgoing-text scan (chat, team, tab list), mcMMO party chat, DiscordSRV slash commands, network bridge check and journal size limit, the formal style for the remaining texts, and the move of the jars to GitHub Releases. The full list is in [CHANGELOG.md](../CHANGELOG.md).

## Supported Minecraft versions

| Minecraft | Jar name (loader = bukkit, spigot, paper, purpur, fabric or forge) | Java on the server |
|-----------|---------------------------------------------------------|--------------------|
| 26.3 | `Moderra26.3-1.0-<loader>.jar` | 25 |
| 26.2 | `Moderra26.2-1.0-<loader>.jar` | 25 |
| 26.1.2 | `Moderra26.1.2-1.0-<loader>.jar` | 25 |
| 26.1.1 | `Moderra26.1.1-1.0-<loader>.jar` | 25 |
| 26.1 | `Moderra26.1-1.0-<loader>.jar` | 25 |
| 1.21.11 | `Moderra1.21.11-1.0-<loader>.jar` | 21 or newer |

Only full releases (no snapshots, pre-releases or betas): the six newest, checked against Mojang on 2026-11-08. That is 36 jars (6 server types).
Download them from GitHub Releases ([`../1-download-jars/README.md`](../1-download-jars/README.md)). A developer builds them with `./build-everything.sh` or a `[build]` commit.

## Server software

| Software | Status |
|----------|--------|
| Spigot, Paper | Supported: the jars are compiled against the Spigot API and use no Paper-only classes. |
| Purpur and other Paper or Spigot forks | Expected to work. Not tested. |
| CraftBukkit | Should work (Spigot API). Not tested. |
| Fabric, Forge | Supported by their own jars (`[Fabric]`, `[Forge]`). Plugin integrations do not exist there. |
| Folia | **Not supported**: the plugin uses the regular Bukkit scheduler. |

## Honest status

* The shared engine has 444 unit tests, all green. The word lists are checked by `7-wordlists/tools/validate.py` and a false-positive test over all 37 languages.
* The plugin and mod code compiles against the real Spigot, Minecraft, Fabric and Forge APIs on GitHub. Every third-party name was read from an API dump.
* **Nothing of version 1.0 was run on a real server.** The open list, with exact test steps and the hard limits, is [`../handoffs/BUILD-VERIFICATION-TODO.md`](../handoffs/BUILD-VERIFICATION-TODO.md).

## Still open

* A real-server test of every jar and every integration (see the open list).
* A review of the word lists by native speakers.
* Logo and gallery screenshots ([modrinth/SCREENSHOT_PLAN.md](modrinth/SCREENSHOT_PLAN.md)).
