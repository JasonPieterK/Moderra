# Hand-written API stubs (NOT the real APIs)

`bukkit/` and `mc/` are tiny Java files **written by hand** for this repository. They contain only the classes, methods
and signatures that our plugin and mod code calls, copied from how the code (and earlier versions of it) used the Bukkit
/ Spigot, Minecraft (Mojang names), Brigadier, Fabric, Forge and Mixin APIs.

**They are assumptions, not the real APIs.** A passing `tools/stub-compile.sh` proves only that the code agrees with the
stubs: that names exist, argument counts match, types line up and nothing in the shared engine breaks the loader layers.
It does **not** prove that Spigot 1.x / Minecraft 1.21.11 / 26.x / Fabric / Forge really have those methods with those
signatures, that the Mixins apply, or that anything works at run time. A stub that is wrong (a method that does not
exist in the real API, a renamed class, a changed return type) hides exactly the mistake you most want to find.

What this is good for: quick feedback while no network access to the Minecraft build hosts is possible. What it is not
good for: any claim that the plugin or a mod "builds" or "works". The real checks are `2-plugin-bukkit-purpur/build-all-versions.sh`,
`./build-everything.sh` and the real-server scripts; see `handoffs/BUILD-VERIFICATION-TODO.md`.

## Rules

- Not on any build path and not packaged: Maven and Gradle never read this folder.
- When code starts to use a new API call, extend the stub in the same commit (one line is enough). Never add something
  to a stub that the code does not use. If you are not sure the real API has it, do not use it; list it in
  `handoffs/BUILD-VERIFICATION-TODO.md` instead.
- Mixin classes only compile against the stub annotations; nothing here processes Mixins.
- `mc/` stands for Minecraft 1.21.11 / 26.x style Mojang names (`Identifier`, `PermissionSet`, ...). The two
  `sign-array` / `sign-record` variants of the sign packet are both compiled by the script.

## Use

```
tools/stub-compile.sh            # plugin + Fabric + Forge (both sign variants), JDK 21
tools/stub-compile.sh plugin
tools/stub-compile.sh mods
```
