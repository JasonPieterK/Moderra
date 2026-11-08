# AntiBadWords

Spigot/Paper plugin that blocks profanity in **chat, commands, signs, books and anvil renames** and punishes repeat
offenders (potion effects, temporary attribute changes, sounds, console commands).

## Supported Minecraft versions

One jar per release from the last 12 months (releases only, no snapshots/betas). Download them from [`dist/`](dist/).

| Minecraft | Jar | Built against |
|-----------|-----|---------------|
| 1.21.11 | `AntiBadWords.1.21.11.V1.10.jar` | spigot-api 1.21.11 (Java 21) |
| 26.1 | `AntiBadWords.26.1.V1.10.jar` | spigot-api 26.1 (Java 25) |
| 26.1.1 | `AntiBadWords.26.1.1.V1.10.jar` | spigot-api 26.1.1 (Java 25) |
| 26.1.2 | `AntiBadWords.26.1.2.V1.10.jar` | spigot-api 26.1.2 (Java 25) |
| 26.2 | `AntiBadWords.26.2.V1.10.jar` | spigot-api 26.2 (Java 25) |
| 26.3 | `AntiBadWords.26.3.V1.10.jar` | spigot-api 26.3 (Java 25) |

Minecraft 26.x needs Java 25 on the server.

**Server software:** Bukkit/Spigot and Paper (same jar). Purpur loads Paper plugins, so the same jar should work there (untested). Not for Folia.

**Fabric and Forge:** server-side mod sources live in [`mods/`](mods/README.md). They are **not compiled or tested yet** - read that file first.

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
mvn clean package                       # jar for 1.21.11 -> target/
JAVA21_HOME=... JAVA25_HOME=... ./build-all.sh   # every supported version -> dist/
./build-all.sh 26.3                     # a single version
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
