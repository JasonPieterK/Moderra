#!/usr/bin/env bash
# Builds Antibadwords-RELEASE[Forge] - 2-<mc>.jar for every supported Minecraft version into ../1-download-jars
#
#   ./build-all-versions.sh                 build all versions
#   ./build-all-versions.sh 26.3 26.2       only these versions
#   TEST=1 ./build-all-versions.sh 26.3     also start a real dedicated server with the jar and check it
#
# Needs: Gradle 9.8+ (GRADLE=/path/to/gradle) and JDK 25, 21 and 8 (ForgeGradle uses an old JDK 8 for one tool).
# To add a Minecraft version: add one line below, with the newest Recommended (or Latest) build from
#   https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json
set -uo pipefail
cd "$(dirname "$0")"
GRADLE="${GRADLE:-gradle}"
JAVA8_HOME="${JAVA8_HOME:-/usr/lib/jvm/java-8-openjdk-amd64}"
JAVA21_HOME="${JAVA21_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA25_HOME="${JAVA25_HOME:-/usr/lib/jvm/java-25-openjdk-amd64}"
export JAVA_HOME="$JAVA25_HOME" PATH="$JAVA25_HOME/bin:$PATH"

# "<minecraft>|<forge build>|<forge major>|<java to compile with>|<mappings: empty for unobfuscated 26.x>|<sign packet api: record|array>"
VERSIONS=(
  "26.3|66.1.0|66|25||record"
  "26.2|65.1.5|65|25||array"
  "26.1.2|64.1.5|64|25||array"
  "26.1.1|63.0.2|63|25||array"
  "26.1|62.0.9|62|25||array"
  "1.21.11|61.2.4|61|21|1.21.11|array"
)
wanted=("$@")
mkdir -p ../1-download-jars build-logs
failed=()

for entry in "${VERSIONS[@]}"; do
  IFS='|' read -r mc forge major java maps sign <<<"$entry"
  if [ ${#wanted[@]} -gt 0 ] && [[ ! " ${wanted[*]} " =~ " ${mc} " ]]; then continue; fi
  echo "=== Forge for Minecraft ${mc} (Forge ${forge}, Java ${java}) ==="
  rm -rf build/libs
  ok=0
  for attempt in 1 2 3 4 5; do   # Maven Central sometimes answers 429 (too many requests): just try again
    if "$GRADLE" --no-daemon -q clean build -Pminecraft_version="$mc" -Pforge_version="$forge" -Pforge_major="$major" \
         -Pjava_release="$java" -Pmappings_version="$maps" -Psign_api="$sign" \
         -Dorg.gradle.java.installations.paths="$JAVA25_HOME,$JAVA21_HOME,$JAVA8_HOME" > "build-logs/build-$mc.log" 2>&1; then ok=1; break; fi
    grep -q " 429 " "build-logs/build-$mc.log" || break
    sleep 40
  done
  if [ $ok -eq 1 ]; then
    cp "build/libs/Antibadwords-RELEASE[Forge] - 2-$mc.jar" ../1-download-jars/   # the full jar, not the "-slim" one
  else
    echo "!!! build FAILED for $mc (see build-logs/build-$mc.log)"; failed+=("$mc"); continue
  fi
  if [ "${TEST:-0}" = 1 ]; then
    ./test-on-real-server.sh "$mc" "$forge" "$major" "$java" "$maps" "$sign" || failed+=("$mc (server test)")
  fi
done
echo; ls -1 ../1-download-jars/*Forge*.jar 2>/dev/null
if [ ${#failed[@]} -gt 0 ]; then echo "FAILED: ${failed[*]}"; exit 1; fi
