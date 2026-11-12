# 5-shared-mod-code - the brain both mods share

| Folder | What it is | Tested here? |
|--------|-----------|--------------|
| `src/main/java` | plain Java: reads `config.yml`, counts strikes, builds messages, runs the `/abw` logic | **yes** - `mvn test` |
| `src/mc/java` | the parts that touch Minecraft: scanning chat/commands/signs/books/anvils, punishments, `/abw` registration | only if the Fabric/Forge build succeeded |
| `src/test` | automatic tests of the plain-Java part |  |

It also reuses `WordMatcher` from [`../2-plugin-bukkit-purpur`](../2-plugin-bukkit-purpur/), so the plugin and the mods
block exactly the same words. Run the tests: `cd 5-shared-mod-code && mvn test`.
