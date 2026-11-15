<p align="center"><img src="6-guides/branding/logo-a-refined/moderra-icon-512.png" alt="Moderra logo" width="128" height="128"></p>

# Moderra

**What is it?** A bouncer for your Minecraft server's chat. When someone types a rude word, the message is blocked.
Do it again and they get punished (a warning, a mute, a kick, a ban - you choose). It also checks commands (`/msg`), signs,
books and anvil renames, so there is no sneaky way around it. This is **version 1.0** (the first release under the name Moderra).

## Which file do I download? (60 seconds)

1. Open the **[GitHub Releases page](https://github.com/JasonPieterK/Moderra/releases)** and pick the newest release
   ([latest release](https://github.com/JasonPieterK/Moderra/releases/latest), for example `v1.0`). Scroll to **Assets**.
   (The jars are built by GitHub Actions, `.github/workflows/build-release.yml`, and attached to the release.
   The folder [`1-download-jars/`](1-download-jars/) holds only a README that explains this; git holds no jar files.)
2. Pick the file for **your server type** and **your Minecraft version**. The name tells you everything:

   `Moderra` **26.3** `-1.0-` **paper** `.jar`
   (the Minecraft version, then the Moderra version, then the server type in lower case)

3. Drop it in your server's `plugins/` folder (Bukkit, Spigot, Paper, Purpur) or `mods/` folder (Fabric, Forge). Restart.

| My server is... | Use the jar named... | Folder with the source code |
|-----------------|----------------------|-----------------------------|
| Bukkit | `...-bukkit.jar` | [`2-plugin-bukkit-purpur/`](2-plugin-bukkit-purpur/) |
| Spigot | `...-spigot.jar` | [`2-plugin-bukkit-purpur/`](2-plugin-bukkit-purpur/) (same code) |
| Paper | `...-paper.jar` | [`2-plugin-bukkit-purpur/`](2-plugin-bukkit-purpur/) (same code) |
| Purpur | `...-purpur.jar` | [`2-plugin-bukkit-purpur/`](2-plugin-bukkit-purpur/) (same code) |
| Fabric | `...-fabric.jar` | [`3-mod-fabric/`](3-mod-fabric/) |
| Forge | `...-forge.jar` | [`4-mod-forge/`](4-mod-forge/) |

### Which Minecraft versions? (the 6 newest full releases, checked against Mojang on 2026-11-08)

| Minecraft | Bukkit | Spigot | Paper | Purpur | Fabric | Forge |
|-----------|:----:|:----:|:----:|:----:|:----:|:----:|
| **26.3**   | yes | yes | yes | yes | yes | yes |
| **26.2**   | yes | yes | yes | yes | yes | yes |
| **26.1.2** | yes | yes | yes | yes | yes | yes |
| **26.1.1** | yes | yes | yes | yes | yes | yes |
| **26.1**   | yes | yes | yes | yes | yes | yes |
| **1.21.11**| yes | yes | yes | yes | yes | yes |

That is 36 jars. Minecraft 26.x needs Java 25 on the server. (There is no Minecraft "26.0": the 2026 numbering starts at 26.1.)
Players never install anything; this only goes on the server.

### How well is version 1.0 tested? (honest)

| Check | Result |
|---|---|
| Engine unit tests (`cd 5-shared-engine && mvn -q -B test`) | **444 tests pass** |
| Plugin and mod code against hand-written stand-ins (`tools/stub-compile.sh`: plugin, 3 Fabric and 3 Forge targets) | passes (only a quick sanity check, proves nothing about the real APIs) |
| Plugin and mod code compiled on GitHub against the **real** Spigot, Minecraft, Fabric and Forge APIs (all 36 jars build) | yes |
| Third-party plugin and mod names checked against the real jars (`verify-apis.yml`, `tools/api-manifest.txt`) | yes, every name used by version 1.0 code was read from an API dump (a `[verify]` commit runs the check again) |
| Real Minecraft and plugin APIs dumped for checking (`[verify]` commits) | yes |
| A **real server** started with a version 1.0 jar, `/moderra` commands tried | **NOT done** |
| The **scripted player** (`bot.js`) swears in chat, signs, books, anvils | **NOT done** in this round |
| Any plugin integration tried with the real plugin (EssentialsX, LuckPerms, DiscordSRV, ...) | **NOT done** |
| Discord alerts sent to a real Discord | **NOT done** |

So: the logic is proven by unit tests, and the code fits the real APIs because it compiles against them, but nobody has watched
version 1.0 work on a real server yet. The jars of the oldest generation (before the rename) were run on real Fabric and Forge servers (a different code base).
What must be tested, with exact steps, and what cannot be done at all: [`handoffs/BUILD-VERIFICATION-TODO.md`](handoffs/BUILD-VERIFICATION-TODO.md).
Fabric/Forge details: [`6-guides/FABRIC-AND-FORGE-STATUS.md`](6-guides/FABRIC-AND-FORGE-STATUS.md).

### What works on which server type (none of it was run on a real server yet)

| Feature | Bukkit / Spigot / Paper / Purpur | Fabric | Forge |
|---|:---:|:---:|:---:|
| Word filter, spam, links, look-alike tricks, strikes, ladder, mute, history | yes | yes | yes |
| `censor` (rewrite the text instead of blocking) | yes | blocks instead | blocks instead |
| Menu with help text (lore) | yes | yes | yes |
| Menu text input | chat | fake sign (default), anvil or chat | fake sign (default), anvil or chat |
| Discord webhooks | yes | yes | yes |
| DiscordSRV alerts, Discord filter, `!moderra` and slash commands | yes | no | no |
| Other plugin integrations (EssentialsX, LuckPerms, ban plugins, ...) | yes | no (only `hasPlugin` by mod id, LuckPerms through permission nodes) | no (same) |
| PlaceholderAPI values | `%moderra_<name>%` | `%moderra:<name>%` on Minecraft 26.3 with Text Placeholder API | no |
| Packet scan (sign, book, anvil) and outgoing-text scan | ProtocolLib or PacketEvents | not needed (own hooks) | not needed (own hooks) |
| Network sync: shared folder | yes | yes | yes |
| Network sync: plugin messaging | yes | no | no |

Level for every row: engine unit tests pass, the code compiles against the real APIs, **no real-server run yet**.

## What can it do? (features)

* **Word filter** with 37 language lists, three strength levels, whitelist, and clean-up of tricks (invisible letters, look-alike letters, leetspeak, `f*ck`).
* **Spam and flood control**, **link filter**, player-name check on join (all configurable, off until you switch them on).
* **Checks everywhere**: chat, commands, signs (both sides), books, anvils, command blocks.
* **Moderation**: strikes that last, a punishment ladder, built-in mute, history diary, ban-plugin support. Guide: [`MODERATION.md`](6-guides/MODERATION.md).
* **Discord**: webhook alerts on all servers; DiscordSRV alerts, Discord-to-Minecraft filter, `!moderra` text commands and slash commands (`/moderra-mute`, `/moderra-unmute`, `/moderra-history`, `/moderra-strikes`, `/moderra-check`, `/moderra-reload`) on Bukkit. Guide: [`DISCORD.md`](6-guides/DISCORD.md).
* **In-game menu** (`/moderra`): change every setting in a chest, on Bukkit, Fabric and Forge (help text as lore on all three). Text input by chat, or on Fabric and Forge by a fake sign or an anvil. Guide: [`GUI.md`](6-guides/GUI.md).
* **Plugin integrations** (Bukkit family): EssentialsX, PlaceholderAPI, LuckPerms, Vault, ban plugins, Floodgate/Geyser, chat-channel plugins (TownyChat, mcMMO party chat), TAB, NametagEdit, ProtocolLib and PacketEvents (packet scan), outgoing-text scan, CoreProtect, Plan, Towny, Factions, Citizens, DecentHolograms, network sync. Guide: [`INTEGRATIONS.md`](6-guides/INTEGRATIONS.md).
* **Fabric and Forge extras**: `hasPlugin` by mod id, Fabric Text Placeholder API values (`%moderra:strikes%`, Minecraft 26.3), Styled Chat works through the normal chat filter.
* **Outgoing-text scan** (off by default): checks chat, team prefixes and suffixes and tab-list names that other plugins send, with ProtocolLib or PacketEvents. Mask, cancel or log only.
* **Network sync**: shared mutes and strikes through a shared folder or plugin messaging, with a start-up bridge check and a journal size limit.
* **Rules per world or group** (`worlds.yml`), formal message style ([`MESSAGE-STYLE.md`](6-guides/MESSAGE-STYLE.md); Discord, network, ban and GUI texts included), automatic migration of an old single-file `config.yml`.

## What is in each folder?

```
Moderra/
├── README.md                    <- you are here
├── build-everything.sh          <- one button: builds every jar for every version
├── CHANGELOG.md                 <- what changed, in plain words
├── LICENSE                      <- All Rights Reserved
├── .github/workflows/           <- GitHub Actions: build the 36 jars + release, check the real APIs
│
├── 1-download-jars/             <- only a README: the jars are on GitHub Releases
├── 2-plugin-bukkit-purpur/      <- source code of the plugin (Bukkit, Spigot, Paper, Purpur)
├── 3-mod-fabric/                <- source code of the Fabric mod
├── 4-mod-forge/                 <- source code of the Forge mod
├── 5-shared-engine/             <- the "brain" all four server types share (so they behave the same)
├── 6-guides/                    <- explanations: every config option, moderation, Discord, menu, integrations, status
├── 7-wordlists/                 <- the bad-word lists: one file per language plus your own custom list
├── tools/                       <- stub compile script, API dump and check tools
└── handoffs/                    <- plans, progress notes and the to-do list for real-server tests
```

Rule of thumb: **Releases = use it, 2-4 = the code for each server type, 5 = shared brain, 6 = reading material, 7 = the word lists.**

## Commands (in game or console)

Most commands need `moderra.admin` (operators have it by default). Full list: [`CONFIGURATION.md`](6-guides/CONFIGURATION.md#commands).

| Command | What it does |
|---------|--------------|
| `/moderra` or `/moderra gui [page]` | open the settings menu (players with `moderra.gui`) |
| `/moderra reload` | read the config folder again (and show what is wrong in it) |
| `/moderra addword <word>` / `removeword <word>` / `listwords` | your own blocked words |
| `/moderra check <text>` | would this text be blocked, and by which check? |
| `/moderra lang list` / `enable <code>` / `disable <code>` | language word lists |
| `/moderra whitelist add <word>` / `remove <word>` / `list` | words that are never blocked |
| `/moderra violations <player>` / `resetviolations <player>` / `strikes <player>` / `ladder` | strikes and ladder |
| `/moderra mute <player> [time] [reason]` / `unmute` / `mutes` | built-in mute |
| `/moderra history <player>` / `recent` / `purge <player> confirm` | history |
| `/moderra discord test` / `status`, `/moderra network`, `/moderra info` | status and tests (`/moderra info` lists the plugins and mods it found) |

## Permissions

| Permission | Default | Use |
|------------|---------|-----|
| `moderra.admin` | op | all admin sub-commands |
| `moderra.notify` | op | staff see every blocked word |
| `moderra.mute` | op | `/moderra mute`, `unmute`, `mutes` |
| `moderra.history` | op | `/moderra history` (purge also needs admin) |
| `moderra.discord` | op | `/moderra discord` |
| `moderra.gui` | op | open the settings menu |
| `moderra.gui.edit.<category>` | **true** | change that category in the menu (take it away to lock a category) |
| `moderra.bypass` | nobody | never checked |
| `moderra.bypass.spam` / `.links` / `.evasion` | nobody | exempt from that filter |
| `moderra.bypass.mute` | nobody | never muted |
| `moderra.profile.<name>` | nobody | puts the player on that `worlds.yml` profile |

The slash commands of Discord are not game permissions: they use the Discord allow-lists ([`DISCORD.md`](6-guides/DISCORD.md)).

Rule: admin, notify, mute, history, discord and gui are for operators; `bypass.*` and `profile.*` are for nobody until you grant them;
`gui.edit.*` is true for everybody who can open the menu. The same defaults apply on Bukkit (`plugin.yml`), Fabric (Fabric Permissions
API, e.g. LuckPerms) and Forge (Forge permission nodes). On Forge, nodes for profiles added after the server started appear after a restart.

## I want to change how it behaves

Use the menu (`/moderra`) or edit the files. The settings live in a small folder that looks the same on every server type
(`plugins/Moderra/` or `config/moderra/`):

```
config.yml               general: languages on/off, how strict, where to look, messages
wordlists/<code>.yml     one file per language, plus custom.yml for your own words
moderation/ladder.yml    punishments and strikes
moderation/mute.yml      the built-in mute
moderation/history.yml   the history diary
filters/spam.yml         spam and flood control          (off until you switch it on)
filters/links.yml        links, IP addresses, invites    (off until you choose a mode)
filters/evasion.yml      invisible characters, look-alike letters, leetspeak
filters/confusables.yml  the look-alike and leetspeak tables
worlds.yml               different rules per world or per group of players
discord.yml              Discord alerts and commands     (off until you switch it on)
integrations.yml         other plugins, network sync
```

Every file is created with a comment above every option. An old single-file `config.yml` is converted automatically on the first
start (a backup `config.yml.bak-<date>` is kept). Every option is explained in [`6-guides/CONFIGURATION.md`](6-guides/CONFIGURATION.md).

## Word lists for many languages

The folder [`7-wordlists/`](7-wordlists/) has one bad-word file per language (35 languages in 37 files, from Arabic to Vietnamese),
three strength tiers each, and an empty `custom.yml` for your own words. They are first drafts written by an AI and are all marked
`unreviewed`. English plus your custom list are switched on at the first start; every other language is one command away
(`/moderra lang enable de`). How to read, edit and check them: [`7-wordlists/README.md`](7-wordlists/README.md). Sources: [`7-wordlists/NOTICE.md`](7-wordlists/NOTICE.md).

## I want to build the jars myself

On GitHub: Actions tab, "Build jars and release", Run workflow. A tag like `v1.0` also publishes the release. A commit whose
message contains `[build]` builds the jars too; `[verify]` runs the real-API check.
On your own machine:
```bash
./build-everything.sh            # every loader, every version -> 1-download-jars/ (not tracked by git)
./build-everything.sh 26.3       # only one Minecraft version
```
You need Java 21 and Java 25, Maven (plugin) and Gradle 9.8+ (Fabric/Forge). Details are at the top of the scripts.

## Licence

**All Rights Reserved.** Copyright (c) 2026 Jason P. See [LICENSE](LICENSE). This replaces the MIT licence of the old versions (before the rename).
