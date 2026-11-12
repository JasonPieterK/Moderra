# AntiBadWords

**What is it?** A bouncer for your Minecraft server's chat. When someone types a rude word, the message is blocked.
Do it again and they get punished (a sound, a potion effect, a kick - you choose in `config.yml`).
It also checks commands (`/msg`), signs, books and anvil renames, so there is no sneaky way around it.

## Which file do I download? (60 seconds)

1. Open the folder **[`1-download-jars/`](1-download-jars/)**.
2. Pick the file for **your server type** and **your Minecraft version**. The name tells you everything:

   `Antibadwords-RELEASE[` **Bukkit** `] - 1-` **26.3** `.jar`
   &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;^ server type &nbsp;&nbsp; ^ Minecraft version

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

That is 24 jars, all in [`1-download-jars/`](1-download-jars/). Minecraft 26.x needs Java 25 on the server.
(There is no Minecraft "26.0": the 2026 numbering starts at 26.1.) Players never install anything; this only goes on the server.

### How well is each one tested?

**Two different things are tested differently. Please read both tables.**

**1. The jars you can download today** (in `1-download-jars/`, version 1, built *before* the current round of changes):

| | Bukkit / Purpur | Fabric | Forge |
|---|---|---|---|
| Jar started on a **real server**, `/abw` commands checked | not done | all 6 versions | all 6 versions |
| A **scripted player** swears in chat, `/msg`, a book, a sign, an anvil | not done | 26.1, 26.1.1, 26.1.2, 1.21.11 | 26.1, 26.1.1, 26.1.2, 1.21.11 |

* The scripted player could not run on 26.2 and 26.3 (the test library does not support those versions yet).
* The Bukkit and Purpur jars have **not** been started on a real Spigot, Paper or Purpur server.

**2. The source code in this repository** (the shared engine, the config folder, `/abw lang`, `/abw whitelist`, world
profiles - newer than the jars):

| Part | How it was checked |
|---|---|
| Shared engine (`5-shared-engine`: all rules, config folder, migration, commands) | **88 automatic unit tests pass** (`cd 5-shared-engine && mvn test`) |
| Bukkit / Purpur plugin code that connects the engine to the server | **not compiled** against the real Spigot API, **never run** on a server (only compiled against a hand-written stand-in) |
| Fabric and Forge code that connects the engine to the game | **not compiled** against Minecraft, Fabric or Forge, **never run** on a server (only compiled against a hand-written stand-in) |
| New jars | **not built**. The place this round was written in could not download Minecraft, Spigot, Fabric or Forge. |

So: the new features are proven in the engine's tests, but nobody has seen them work on a real server yet. What has to
be done to close that gap is listed in [`handoffs/BUILD-VERIFICATION-TODO.md`](handoffs/BUILD-VERIFICATION-TODO.md).

Full details and remaining gaps: [`6-guides/FABRIC-AND-FORGE-STATUS.md`](6-guides/FABRIC-AND-FORGE-STATUS.md).

## What is in each folder?

```
AntiBadWordsPlugin/
├── README.md                    <- you are here
├── build-everything.sh          <- one button: builds every jar for every version
├── CHANGELOG.md                 <- what changed, in plain words
├── LICENSE                      <- MIT: free to use and change
│
├── 1-download-jars/             <- THE FINISHED FILES. Start here if you just want to use it.
├── 2-plugin-bukkit-purpur/      <- source code of the plugin (Bukkit, Spigot, Paper, Purpur)
├── 3-mod-fabric/                <- source code of the Fabric mod
├── 4-mod-forge/                 <- source code of the Forge mod
├── 5-shared-engine/             <- the "brain" all four server types share (so they behave the same)
├── 6-guides/                    <- explanations: every config option, project notes, Modrinth page text
├── 7-wordlists/                 <- the bad-word lists: one file per language (37 files) plus your own custom list
└── handoffs/                    <- the plan for the next features, split into 5 ready-to-paste session prompts
```

Rule of thumb: **1 = use it, 2-4 = the code for each server type, 5 = shared brain, 6 = reading material, 7 = the word lists.**

## Word lists for many languages

The folder [`7-wordlists/`](7-wordlists/) has one bad-word file per language (35 languages in 37 files, from Arabic to Vietnamese),
three strength tiers each, and an empty `custom.yml` for your own words. They are first drafts written by an AI and are all marked
`unreviewed`. The current source code copies them into the config folder (`wordlists/`) on the first start; English plus your custom list are
switched on, every other language is one command away (`/abw lang enable de`). The downloadable version-1 jars still use the lists in their `config.yml`. How to read, edit and check them: [`7-wordlists/README.md`](7-wordlists/README.md). Where they come from: [`7-wordlists/NOTICE.md`](7-wordlists/NOTICE.md).

## Commands (in game or console)

Everything needs the permission `antibadwords.admin` (operators have it by default).

| Command | What it does |
|---------|--------------|
| `/abw reload` | read the config folder again (and show what is wrong in it) |
| `/abw addword <word>` / `removeword <word>` | add or remove your own blocked word |
| `/abw listwords [list]` | show a word list |
| `/abw check <text>` | would this text be blocked? |
| `/abw violations <player>` / `resetviolations <player>` | see or clear someone's strikes |
| `/abw info` | short status |
| `/abw lang list` / `enable <code>` / `disable <code>` | switch language word lists on and off (new, not in the version-1 jars) |
| `/abw whitelist add <word>` / `remove <word>` / `list [page]` | words that are never blocked (new, not in the version-1 jars) |

Other permissions: `antibadwords.notify` (staff see who got blocked, default: operators) and
`antibadwords.bypass` (never blocked, default: nobody). New: `antibadwords.profile.<name>` puts a player on a rule profile from `worlds.yml`.

## I want to change how it behaves

**Version-1 jars:** edit `config.yml` (created on first start).

**Current source code:** the settings live in a small folder that looks the same on every server type
(`plugins/AntiBadWords/` or `config/antibadwords/`):

```
config.yml               general: languages on/off, how strict, where to look, messages
wordlists/<code>.yml     one file per language, plus custom.yml for your own words
moderation/ladder.yml    punishments
worlds.yml               different rules per world or per group of players
```

Every file is created with a comment above every option. An old `config.yml` is converted automatically on the first
start (a backup `config.yml.bak-<date>` is kept; the filter blocks exactly what it blocked before). Every option is
explained in [`6-guides/CONFIGURATION.md`](6-guides/CONFIGURATION.md).

## I want to build the jars myself

```bash
./build-everything.sh            # every loader, every version -> 1-download-jars/
./build-everything.sh 26.3       # only one Minecraft version
```
You need Java 21 and Java 25, Maven (plugin) and Gradle 9.8+ (Fabric/Forge). Details are at the top of the scripts.

Licensed under the [MIT License](LICENSE).
