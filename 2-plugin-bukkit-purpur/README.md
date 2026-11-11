# 2-plugin-bukkit-purpur - the plugin's source code

For Bukkit, Spigot, Paper and Purpur servers (one code base; Purpur runs Paper plugins).

| Where | What |
|-------|------|
| `src/main/java/.../AntiBadWordsPlugin.java` | the main class: loads `config.yml`, keeps the strike counters |
| `.../AntiBadWordsListener.java` | listens to chat, commands, signs, books, anvils |
| `.../WordMatcher.java` | decides "is this word blocked?" (handles `f.u.c.k`, `fvck`, etc.) |
| `.../PunishmentExecutor.java` | applies effects, attribute changes, sounds, console commands |
| `.../AntiBadWordsCommand.java` | the `/abw` command |
| `src/main/resources/config.yml` | the default config (also used by the Fabric and Forge mods) |
| `src/test/` | automatic tests |
| `build-all-versions.sh` | builds the Bukkit + Purpur jars for every Minecraft version |

`mvn clean package` builds one jar for 1.21.11. `./build-all-versions.sh` builds all of them into `../1-download-jars/`.
