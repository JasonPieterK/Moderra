# AntiBadWords

Spigot/Paper plugin that blocks profanity in **chat, commands, signs, books and anvil renames** and punishes repeat
offenders (potion effects, temporary attribute changes, sounds, console commands).

## Supported Minecraft versions and file names

Every jar is named `Antibadwords-RELEASE[LOADER] - VERSION-MCVERSION.jar`. All jars are release **1** (the numbering
starts again at 1) and only stable Minecraft releases are built (no snapshots, pre-releases or betas).

| Minecraft | Bukkit / Spigot / Paper | Purpur | Fabric | Forge |
|-----------|-------------------------|--------|--------|-------|
| 26.3 | `Antibadwords-RELEASE[Bukkit] - 1-26.3.jar` | `Antibadwords-RELEASE[Purpur] - 1-26.3.jar` | not built yet | not built yet |
| 26.2 | `Antibadwords-RELEASE[Bukkit] - 1-26.2.jar` | `Antibadwords-RELEASE[Purpur] - 1-26.2.jar` | not built yet | not built yet |
| 26.1 | `Antibadwords-RELEASE[Bukkit] - 1-26.1.jar` | `Antibadwords-RELEASE[Purpur] - 1-26.1.jar` | not built yet | not built yet |
| 1.21.11 | `Antibadwords-RELEASE[Bukkit] - 1-1.21.11.jar` | `Antibadwords-RELEASE[Purpur] - 1-1.21.11.jar` | not built yet | not built yet |

Minecraft 26.x needs Java 25 on the server. The Bukkit and Purpur jars of one Minecraft version contain identical
code (Purpur runs Paper/Spigot plugins unchanged); they are separate files only so each platform has its own download.
The Fabric and Forge sources are in [`mods/`](mods/README.md): read that file for what is and is not verified.

## Commands and permissions

| Command | Permission | What it does |
|---------|-----------|--------------|
| `/abw reload` | `antibadwords.admin` | reload config.yml |
| `/abw addword <word or phrase>` / `removeword` | `antibadwords.admin` | manage the custom list |
| `/abw check <text>` | `antibadwords.admin` | test whether a text would be blocked |
| `/abw violations <player>` / `resetviolations <player>` | `antibadwords.admin` | inspect / clear a player's strikes |
| `/abw info` | `antibadwords.admin` | status summary |
| `/abw listwords [list]` | `antibadwords.admin` | show a word list |

Other permissions: `antibadwords.notify` (staff alerts, default op), `antibadwords.bypass` (exempt, default nobody).

## Building

```bash
mvn clean package                       # Bukkit jar for 1.21.11 -> target/
JAVA21_HOME=... JAVA25_HOME=... ./build-all.sh   # every supported version -> dist/
./build-all.sh 26.3                     # a single version (builds the Bukkit and the Purpur jar)
```

To support a new Minecraft release add one line to `MINECRAFT_VERSIONS` in `build-all.sh`.

## Documentation

| File | Contents |
|------|----------|
| [CHANGELOG.md](CHANGELOG.md) | What changed in 1.10 and the bugs that were fixed |
| [docs/PROJECT_SUMMARY.md](docs/PROJECT_SUMMARY.md) | Work done, supported versions, honest status |
| [docs/CONFIGURATION.md](docs/CONFIGURATION.md) | Every config option and permission |
| [docs/modrinth/](docs/modrinth/) | Modrinth page text, screenshot plan and upload hand-off |

Licensed under the [MIT License](LICENSE).
