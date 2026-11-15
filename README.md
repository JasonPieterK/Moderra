# AntiBadWords

**What is it?** A bouncer for your Minecraft server's chat. When someone types a rude word, the message is blocked.
Do it again and they get punished (a warning, a mute, a kick, a ban - you choose). It also checks commands (`/msg`), signs,
books and anvil renames, so there is no sneaky way around it. This is **version 2**.

## Which file do I download? (60 seconds)

1. Open the **[GitHub Releases page](https://github.com/JasonPieterK/AntiBadWordsPlugin/releases)** and pick the release `v2.0.0`.
   (The jars are built by GitHub Actions, `.github/workflows/build-release.yml`, and attached to the release. The folder
   [`1-download-jars/`](1-download-jars/) still holds the old **version 1** jars.)
2. Pick the file for **your server type** and **your Minecraft version**. The name tells you everything:

   `Antibadwords-RELEASE[` **Bukkit** `] - 2-` **26.3** `.jar`
   (server type in the brackets, then the version `2`, then the Minecraft version)

3. Drop it in your server's `plugins/` folder (Bukkit, Spigot, Paper, Purpur) or `mods/` folder (Fabric, Forge). Restart.

| My server is... | Use the jar named... | Folder with the source code |
|-----------------|----------------------|-----------------------------|
| Bukkit, Spigot or Paper | `...[Bukkit]...` | [`2-plugin-bukkit-purpur/`](2-plugin-bukkit-purpur/) |
| Purpur | `...[Purpur]...` | [`2-plugin-bukkit-purpur/`](2-plugin-bukkit-purpur/) (same code) |
| Fabric | `...[Fabric]...` | [`3-mod-fabric/`](3-mod-fabric/) |
| Forge | `...[Forge]...` | [`4-mod-forge/`](4-mod-forge/) |

### Which Minecraft versions? (the 6 newest full releases, checked against Mojang on 2026-11-08)

| Minecraft | Bukkit / Spigot / Paper | Purpur | Fabric | Forge |
|-----------|:----:|:----:|:----:|:----:|
| **26.3**   | yes | yes | yes | yes |
| **26.2**   | yes | yes | yes | yes |
| **26.1.2** | yes | yes | yes | yes |
| **26.1.1** | yes | yes | yes | yes |
| **26.1**   | yes | yes | yes | yes |
| **1.21.11**| yes | yes | yes | yes |

That is 24 jars. Minecraft 26.x needs Java 25 on the server. (There is no Minecraft "26.0": the 2026 numbering starts at 26.1.)
Players never install anything; this only goes on the server.

### How well is version 2 tested? (honest)

| Check | Result |
|---|---|
| Engine unit tests (`cd 5-shared-engine && mvn -q -B test`) | **410 tests pass** |
| Plugin and mod code against hand-written stand-ins (`tools/stub-compile.sh`) | passes (only a quick sanity check, proves nothing about the real APIs) |
| Plugin and mod code compiled on GitHub against the **real** Spigot, Minecraft, Fabric and Forge APIs (all 24 jars build) | yes |
| Third-party plugin names checked against the real jars (`verify-apis.yml`) | 64 names, 0 failures |
| Real Minecraft and plugin APIs dumped for checking (`[verify]` commits) | yes |
| A **real server** started with a version 2 jar, `/abw` commands tried | **NOT done** |
| The **scripted player** (`bot.js`) swears in chat, signs, books, anvils | **NOT done** in this round |
| Any plugin integration tried with the real plugin (EssentialsX, LuckPerms, DiscordSRV, ...) | **NOT done** |
| Discord alerts sent to a real Discord | **NOT done** |

So: the logic is proven by unit tests, and the code fits the real APIs because it compiles against them, but nobody has watched
version 2 work on a real server yet. The version 1 jars (in `1-download-jars/`) were run on real Fabric and Forge servers.
What must be tested, in order: [`handoffs/BUILD-VERIFICATION-TODO.md`](handoffs/BUILD-VERIFICATION-TODO.md).
Fabric/Forge details: [`6-guides/FABRIC-AND-FORGE-STATUS.md`](6-guides/FABRIC-AND-FORGE-STATUS.md).

## What can it do? (features)

* **Word filter** with 37 language lists, three strength levels, whitelist, and clean-up of tricks (invisible letters, look-alike letters, leetspeak, `f*ck`).
* **Spam and flood control**, **link filter**, player-name check on join (all configurable, off until you switch them on).
* **Checks everywhere**: chat, commands, signs (both sides), books, anvils, command blocks.
* **Moderation**: strikes that last, a punishment ladder, built-in mute, history diary, ban-plugin support. Guide: [`MODERATION.md`](6-guides/MODERATION.md).
* **Discord**: webhook alerts on all servers; DiscordSRV, Discord filter and `!abw` commands on Bukkit. Guide: [`DISCORD.md`](6-guides/DISCORD.md).
* **In-game menu** (`/abw`): change every setting in a chest. Guide: [`GUI.md`](6-guides/GUI.md).
* **Plugin integrations** (Bukkit family): EssentialsX, PlaceholderAPI, LuckPerms, Vault, ban plugins, Floodgate/Geyser, chat-channel plugins, TAB, NametagEdit, ProtocolLib, CoreProtect, Plan, mcMMO, Towny, Factions, Citizens, DecentHolograms, network sync. Guide: [`INTEGRATIONS.md`](6-guides/INTEGRATIONS.md).
* **Rules per world or group** (`worlds.yml`), calm message style ([`MESSAGE-STYLE.md`](6-guides/MESSAGE-STYLE.md)), automatic migration of a version 1 `config.yml`.

## What is in each folder?

```
AntiBadWordsPlugin/
├── README.md                    <- you are here
├── build-everything.sh          <- one button: builds every jar for every version
├── CHANGELOG.md                 <- what changed, in plain words
├── LICENSE                      <- All Rights Reserved
├── .github/workflows/           <- GitHub Actions: build the 24 jars + release, check the real APIs
│
├── 1-download-jars/             <- the old version 1 jars
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

Most commands need `antibadwords.admin` (operators have it by default). Full list: [`CONFIGURATION.md`](6-guides/CONFIGURATION.md#commands).

| Command | What it does |
|---------|--------------|
| `/abw` or `/abw gui [page]` | open the settings menu (players with `antibadwords.gui`) |
| `/abw reload` | read the config folder again (and show what is wrong in it) |
| `/abw addword <word>` / `removeword <word>` / `listwords` | your own blocked words |
| `/abw check <text>` | would this text be blocked, and by which check? |
| `/abw lang list` / `enable <code>` / `disable <code>` | language word lists |
| `/abw whitelist add <word>` / `remove <word>` / `list` | words that are never blocked |
| `/abw violations <player>` / `resetviolations <player>` / `strikes <player>` / `ladder` | strikes and ladder |
| `/abw mute <player> [time] [reason]` / `unmute` / `mutes` | built-in mute |
| `/abw history <player>` / `recent` / `purge <player> confirm` | history |
| `/abw discord test` / `status`, `/abw network`, `/abw info` | status and tests |

## Permissions

| Permission | Default | Use |
|------------|---------|-----|
| `antibadwords.admin` | op | all admin sub-commands |
| `antibadwords.notify` | op | staff see every blocked word |
| `antibadwords.mute` | op | `/abw mute`, `unmute`, `mutes` |
| `antibadwords.history` | op | `/abw history` (purge also needs admin) |
| `antibadwords.discord` | op | `/abw discord` |
| `antibadwords.gui` | op | open the settings menu |
| `antibadwords.gui.edit.<category>` | **true** | change that category in the menu (take it away to lock a category) |
| `antibadwords.bypass` | nobody | never checked |
| `antibadwords.bypass.spam` / `.links` / `.evasion` | nobody | exempt from that filter |
| `antibadwords.bypass.mute` | nobody | never muted |
| `antibadwords.profile.<name>` | nobody | puts the player on that `worlds.yml` profile |

Rule: admin, notify, mute, history, discord and gui are for operators; `bypass.*` and `profile.*` are for nobody until you grant them;
`gui.edit.*` is true for everybody who can open the menu. The same defaults apply on Bukkit (`plugin.yml`), Fabric (Fabric Permissions
API, e.g. LuckPerms) and Forge (Forge permission nodes). On Forge, nodes for profiles added after the server started appear after a restart.

## I want to change how it behaves

Use the menu (`/abw`) or edit the files. The settings live in a small folder that looks the same on every server type
(`plugins/AntiBadWords/` or `config/antibadwords/`):

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

Every file is created with a comment above every option. An old version 1 `config.yml` is converted automatically on the first
start (a backup `config.yml.bak-<date>` is kept). Every option is explained in [`6-guides/CONFIGURATION.md`](6-guides/CONFIGURATION.md).

## Word lists for many languages

The folder [`7-wordlists/`](7-wordlists/) has one bad-word file per language (35 languages in 37 files, from Arabic to Vietnamese),
three strength tiers each, and an empty `custom.yml` for your own words. They are first drafts written by an AI and are all marked
`unreviewed`. English plus your custom list are switched on at the first start; every other language is one command away
(`/abw lang enable de`). How to read, edit and check them: [`7-wordlists/README.md`](7-wordlists/README.md). Sources: [`7-wordlists/NOTICE.md`](7-wordlists/NOTICE.md).

## I want to build the jars myself

On GitHub: Actions tab, "Build jars and release", Run workflow. A tag like `v2.0.0` also publishes the release. A commit whose
message contains `[build]` builds the jars too; `[verify]` runs the real-API check.
On your own machine:
```bash
./build-everything.sh            # every loader, every version -> 1-download-jars/
./build-everything.sh 26.3       # only one Minecraft version
```
You need Java 21 and Java 25, Maven (plugin) and Gradle 9.8+ (Fabric/Forge). Details are at the top of the scripts.

## Licence

**All Rights Reserved.** Copyright (c) 2026 Jason P. See [LICENSE](LICENSE). This replaces the MIT licence of version 1.
