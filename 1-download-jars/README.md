# 1-download-jars - where the jars are

This folder holds **no jar files in git**. The finished jars are on **GitHub Releases**:

* Repository: <https://github.com/JasonPieterK/AntiBadWordsPlugin>
* All releases: <https://github.com/JasonPieterK/AntiBadWordsPlugin/releases>
* Newest release: <https://github.com/JasonPieterK/AntiBadWordsPlugin/releases/latest> (open it, scroll to **Assets**, click the jar you need)

GitHub Actions builds the jars (`.github/workflows/build-release.yml`) and attaches all 24 of them to every release.
The old version 1 jars were removed from the repository. Use a version 2 release.

## How to pick the right jar

Pick **one** jar: your server type and your Minecraft version. The file name is:

`Antibadwords-RELEASE[SERVER TYPE] - 2-MINECRAFT VERSION.jar`

The number `2` is the major version of AntiBadWords (release 2.1 still uses `2` in the file name).

| My server is... | Folder | File name pattern |
|---|---|---|
| Bukkit, Spigot or Paper | `plugins/` | `Antibadwords-RELEASE[Bukkit] - 2-<mc>.jar` |
| Purpur | `plugins/` | `Antibadwords-RELEASE[Purpur] - 2-<mc>.jar` |
| Fabric | `mods/` | `Antibadwords-RELEASE[Fabric] - 2-<mc>.jar` |
| Forge | `mods/` | `Antibadwords-RELEASE[Forge] - 2-<mc>.jar` |

Replace `<mc>` with your Minecraft version: `26.3`, `26.2`, `26.1.2`, `26.1.1`, `26.1` or `1.21.11`.
Example: Fabric on Minecraft 26.1.2 uses `Antibadwords-RELEASE[Fabric] - 2-26.1.2.jar`.

* Minecraft 26.x needs Java 25 on the server. Minecraft 1.21.11 needs Java 21 or newer.
* Put in **one** AntiBadWords jar only. Players install nothing.
* Restart the server. The plugin creates its config folder (`plugins/AntiBadWords/` or `config/antibadwords/`).

## How a developer builds the jars

* **On GitHub:** push a commit whose message contains `[build]`. The workflow builds all 24 jars and keeps them as a workflow artifact.
  A tag such as `v2.1.0` (or the text `[release v2.1.0]` in the commit message) also publishes a GitHub Release with the jars attached.
  A commit with `[verify]` runs the real-API check.
* **On your machine:** run `./build-everything.sh` in the repository root. It writes every jar into this folder (`1-download-jars/`).
  `./build-everything.sh 26.3` builds one Minecraft version only. You need Maven, Gradle 9.8 or newer and JDK 8, 21 and 25.
  The scripts say more in their first lines.
* The jars you build here are not tracked by git (`.gitignore`).
