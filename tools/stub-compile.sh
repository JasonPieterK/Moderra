#!/usr/bin/env bash
# Compiles the Bukkit plugin and the Fabric/Forge mod layer (plus the shared engine) with plain javac against the
# HAND-WRITTEN stubs in tools/api-stubs/. This is only a sanity check that the code is consistent with what the stubs
# say; it is NOT a build against the real Spigot/Minecraft/Fabric/Forge APIs. See tools/api-stubs/README.md.
#
#   tools/stub-compile.sh            all targets
#   tools/stub-compile.sh plugin     only the Bukkit plugin
#   tools/stub-compile.sh mods       only Fabric + Forge (every per-version variant combination)
#
# Needs JDK 21 (javac) and the SnakeYAML jar, which Maven downloads (run `mvn -q -B test` in 5-shared-engine once, or
# set SNAKEYAML=/path/to/snakeyaml.jar). Output goes to a temporary folder that is deleted at the end.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STUBS="$ROOT/tools/api-stubs"
ENGINE="$ROOT/5-shared-engine/src"
JAVAC="${JAVAC:-javac}"
RELEASE="${RELEASE:-21}"
WHAT="${1:-all}"

SNAKEYAML="${SNAKEYAML:-$(find "${HOME}/.m2" -name 'snakeyaml-*.jar' 2>/dev/null | sort | tail -n 1)}"
if [ -z "$SNAKEYAML" ] || [ ! -f "$SNAKEYAML" ]; then
  echo "SnakeYAML jar not found. Run 'cd 5-shared-engine && mvn -q -B dependency:resolve' or set SNAKEYAML=/path/to/jar" >&2
  exit 2
fi

LUCKPERMS="${LUCKPERMS:-$(find "${HOME}/.m2" -path '*net/luckperms/api/*' -name 'api-*.jar' 2>/dev/null | sort | tail -n 1)}"
if [ -z "$LUCKPERMS" ]; then
  echo "LuckPerms API jar not found (it is on Maven Central): mvn -q dependency:get -Dartifact=net.luckperms:api:5.4" >&2
  exit 2
fi

OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
FAILED=0

compile() { # name classpath source-roots...
  local name="$1" cp="$2"; shift 2
  local list="$OUT/$name.sources"
  : > "$list"
  for dir in "$@"; do find "$dir" -name '*.java' >> "$list"; done
  mkdir -p "$OUT/$name"
  if "$JAVAC" --release "$RELEASE" -proc:none -nowarn -encoding UTF-8 -cp "$cp" -d "$OUT/$name" @"$list" 2> "$OUT/$name.log"; then
    echo "PASS  $name ($(wc -l < "$list") files)"
  else
    echo "FAIL  $name"; sed 's/^/      /' "$OUT/$name.log" | head -40
    FAILED=1
  fi
}

if [ "$WHAT" = all ] || [ "$WHAT" = plugin ]; then
  # the plugin sources, the engine, and the name-only DiscordSRV shim (the pom compiles it but keeps it out of the jar)
  compile plugin "$SNAKEYAML:$LUCKPERMS" "$STUBS/bukkit" "$ENGINE/main/java" \
    "$ROOT/2-plugin-bukkit-purpur/src/main/java" "$ROOT/2-plugin-bukkit-purpur/src/discordsrv-shim/java" \
    "$ROOT/2-plugin-bukkit-purpur/src/papi-shim/java" "$ROOT/2-plugin-bukkit-purpur/src/protocollib-shim/java" \
    "$ROOT/2-plugin-bukkit-purpur/src/plan-shim/java"
fi

if [ "$WHAT" = all ] || [ "$WHAT" = mods ]; then
  # the same combinations the build scripts use (sign : menu : Forge ModList : Fabric Text Placeholder API):
  # 1.21.11 sign-array+menu-click+instance, 26.1-26.2 sign-array+menu-input+static, 26.3 sign-record+menu-input+static (+placeholders api)
  for combo in sign-array:menu-click:instance:none sign-array:menu-input:static:none sign-record:menu-input:static:api; do
    IFS=: read -r sign menu modlist ph <<<"$combo"
    compile "fabric-$sign-$menu-$ph" "$SNAKEYAML" "$STUBS/mc" "$ENGINE/main/java" "$ENGINE/mc/java" "$ENGINE/mc-variants/$sign" "$ENGINE/mc-variants/$menu" \
      "$ENGINE/mc-variants/placeholders-$ph" "$ROOT/3-mod-fabric/src/main/java"
    compile "forge-$sign-$menu-$modlist" "$SNAKEYAML" "$STUBS/mc" "$STUBS/mc-modlist-${modlist}" "$ENGINE/main/java" "$ENGINE/mc/java" "$ENGINE/mc-variants/$sign" "$ENGINE/mc-variants/$menu" \
      "$ENGINE/mc-variants/forge-modlist-$modlist" "$ROOT/4-mod-forge/src/main/java"
  done
fi

exit "$FAILED"
