# 1-download-jars - where the jars are

This folder holds **no jar files in git**. The finished jars are on **GitHub Releases**:

* Repository: <https://github.com/JasonPieterK/AntiBadWordsPlugin>
* All releases: <https://github.com/JasonPieterK/AntiBadWordsPlugin/releases>
* Newest release: <https://github.com/JasonPieterK/AntiBadWordsPlugin/releases/latest> (open it, scroll to **Assets**, click the jar you need)

GitHub Actions builds the jars (`.github/workflows/build-release.yml`) and attaches all 36 of them to every release.
The jars of the project's earlier name are not in the repository any more. Use a Moderra release.

## How to pick the right jar

Pick **one** jar: your server type and your Minecraft version. The file name is:

`Moderra<MINECRAFT VERSION>-<VERSION>-<SERVER TYPE>.jar`, for example `Moderra26.3-1.0-paper.jar`

The server type part is lower case: `bukkit`, `spigot`, `paper`, `purpur`, `fabric` or `forge`. The four plugin jars (bukkit, spigot, paper, purpur) contain the same code; they are separate files because Modrinth lists the server types separately.
The version is `<major>.<minor>`: the major number grows with a big update, the minor number with small updates.

| My server is... | Folder | File name pattern |
|---|---|---|
| Bukkit | `plugins/` | `Moderra<mc>-<version>-bukkit.jar` |
| Spigot | `plugins/` | `Moderra<mc>-<version>-spigot.jar` |
| Paper | `plugins/` | `Moderra<mc>-<version>-paper.jar` |
| Purpur | `plugins/` | `Moderra<mc>-<version>-purpur.jar` |
| Fabric | `mods/` | `Moderra<mc>-<version>-fabric.jar` |
| Forge | `mods/` | `Moderra<mc>-<version>-forge.jar` |

Replace `<mc>` with your Minecraft version: `26.3`, `26.2`, `26.1.2`, `26.1.1`, `26.1` or `1.21.11`.
Example: Fabric on Minecraft 26.1.2 uses `Moderra26.1.2-1.0-fabric.jar`.

* Minecraft 26.x needs Java 25 on the server. Minecraft 1.21.11 needs Java 21 or newer.
* Put in **one** Moderra jar only. Players install nothing.
* Restart the server. The plugin creates its config folder (`plugins/Moderra/` or `config/moderra/`).

## How a developer builds the jars

* **On GitHub:** push a commit whose message contains `[build]`. The workflow builds all 36 jars and keeps them as a workflow artifact.
  A tag such as `v1.0` (or the text `[release v1.0]` in the commit message) also publishes a GitHub Release with the jars attached.
  A commit with `[verify]` runs the real-API check.
* **On your machine:** run `./build-everything.sh` in the repository root. It writes every jar into this folder (`1-download-jars/`).
  `./build-everything.sh 26.3` builds one Minecraft version only. You need Maven, Gradle 9.8 or newer and JDK 8, 21 and 25.
  The scripts say more in their first lines.
* The jars you build here are not tracked by git (`.gitignore`).
