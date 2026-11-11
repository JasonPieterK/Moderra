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

| | Bukkit / Purpur | Fabric | Forge |
|---|---|---|---|
| Automatic unit tests | 24 tests | 17 tests (shared code) | 17 tests (shared code) |
| Started on a **real server** with the jar, `/abw` commands checked | not done | all 6 versions | all 6 versions |
| A **scripted player** swears in chat, `/msg`, a book, a sign, an anvil | not done | 26.1 (everything), 1.21.11 (chat + commands) | see [`6-guides/FABRIC-AND-FORGE-STATUS.md`](6-guides/FABRIC-AND-FORGE-STATUS.md) |

The Bukkit and Purpur jars are built from source for all six versions but have **not** been started on a real
Spigot, Paper or Purpur server. Purpur runs Paper plugins, so it should work, but that is an expectation, not a test.
Details and the remaining gaps: [`6-guides/FABRIC-AND-FORGE-STATUS.md`](6-guides/FABRIC-AND-FORGE-STATUS.md).

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
├── 5-shared-mod-code/           <- the "brain" Fabric and Forge both use (so they behave the same)
└── 6-guides/                    <- explanations: every config option, project notes, Modrinth page text
```

Rule of thumb: **1 = use it, 2-4 = the code for each server type, 5 = shared brain, 6 = reading material.**

## Commands (in game or console)

Everything needs the permission `antibadwords.admin` (operators have it by default).

| Command | What it does |
|---------|--------------|
| `/abw reload` | re-read `config.yml` |
| `/abw addword <word>` / `removeword <word>` | add or remove your own blocked word |
| `/abw listwords [list]` | show a word list |
| `/abw check <text>` | would this text be blocked? |
| `/abw violations <player>` / `resetviolations <player>` | see or clear someone's strikes |
| `/abw info` | short status |

Other permissions: `antibadwords.notify` (staff see who got blocked, default: operators) and
`antibadwords.bypass` (never blocked, default: nobody).

## I want to change how it behaves

Edit `config.yml` (it is created on first start). Every option is explained in
[`6-guides/CONFIGURATION.md`](6-guides/CONFIGURATION.md): how strict it is (protection level 1-3), which languages,
what the punishments are, what the messages say.

## I want to build the jars myself

```bash
./build-everything.sh            # every loader, every version -> 1-download-jars/
./build-everything.sh 26.3       # only one Minecraft version
```
You need Java 21 and Java 25, Maven (plugin) and Gradle 9.8+ (Fabric/Forge). Details are at the top of the scripts.

Licensed under the [MIT License](LICENSE).
