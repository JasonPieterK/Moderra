#!/usr/bin/env bash
# Builds Antibadwords-RELEASE[Fabric] - 1-<mc>.jar for every supported Minecraft version into ../1-download-jars
#
#   ./build-all-versions.sh                 build all versions
#   ./build-all-versions.sh 26.3 26.2       only these versions
#   TEST=1 ./build-all-versions.sh 26.3     also start a real dedicated server with the jar and check it
#
# Needs: Gradle 9.8+ (GRADLE=/path/to/gradle), JDK 21 and JDK 25 (JAVA21_HOME / JAVA25_HOME).
# To add a Minecraft version: add one line below. Get the Fabric API version from
#   https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml  (the one ending in +<mc>)
set -uo pipefail
cd "$(dirname "$0")"
GRADLE="${GRADLE:-gradle}"
JAVA21_HOME="${JAVA21_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
JAVA25_HOME="${JAVA25_HOME:-/usr/lib/jvm/java-25-openjdk-amd64}"

# "<minecraft>|<fabric api version>|<remap: true only for obfuscated 1.21.x>|<java to compile with>|<sign packet api: record|array>"
VERSIONS=(
  "26.3|0.165.2+26.3|false|25|record"
  "26.2|0.161.0+26.2|false|25|array"
  "26.1.2|0.155.3+26.1.2|false|25|array"
  "26.1.1|0.145.4+26.1.1|false|25|array"
  "26.1|0.145.1+26.1|false|25|array"
  "1.21.11|0.141.6+1.21.11|true|21|array"
)
wanted=("$@")
mkdir -p ../1-download-jars build-logs
failed=()

for entry in "${VERSIONS[@]}"; do
  IFS='|' read -r mc api remap java sign <<<"$entry"
  if [ ${#wanted[@]} -gt 0 ] && [[ ! " ${wanted[*]} " =~ " ${mc} " ]]; then continue; fi
  # Gradle itself (and Loom) always run on JDK 25; the toolchain compiles with the Java the Minecraft version needs.
  export JAVA_HOME="$JAVA25_HOME" PATH="$JAVA25_HOME/bin:$PATH"
  echo "=== Fabric for Minecraft ${mc} (Fabric API ${api}, Java ${java}) ==="
  rm -rf build/libs
  ok=0
  for attempt in 1 2 3 4 5; do   # Maven Central sometimes answers 429 (too many requests): just try again
    if "$GRADLE" --no-daemon -q clean build -Pminecraft_version="$mc" -Pfabric_version="$api" -Premap="$remap" -Pjava_release="$java" -Psign_api="$sign" \
         -Dorg.gradle.java.installations.paths="$JAVA25_HOME,$JAVA21_HOME" > "build-logs/build-$mc.log" 2>&1; then ok=1; break; fi
    grep -q " 429 " "build-logs/build-$mc.log" || break
    sleep 40
  done
  if [ $ok -eq 1 ]; then
    cp build/libs/*.jar ../1-download-jars/
  else
    echo "!!! build FAILED for $mc (see build-logs/build-$mc.log)"; failed+=("$mc"); continue
  fi

  if [ "${TEST:-0}" = 1 ]; then
    ./test-on-real-server.sh "$mc" "$api" "$remap" "$java" "$sign" || failed+=("$mc (server test)")
  fi
done
echo; ls -1 ../1-download-jars/*Fabric*.jar 2>/dev/null
if [ ${#failed[@]} -gt 0 ]; then echo "FAILED: ${failed[*]}"; exit 1; fi
