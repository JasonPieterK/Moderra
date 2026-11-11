#!/usr/bin/env bash
# One button: builds EVERY jar (Bukkit, Purpur, Fabric, Forge) for every supported Minecraft version
# into ./1-download-jars
#
#   ./build-everything.sh                  everything
#   ./build-everything.sh 26.3 26.2        only these Minecraft versions
#   TEST=1 ./build-everything.sh 26.3      also start real Fabric/Forge servers with the jars and check them
#
# You need:  Maven (for the plugin), Gradle 9.8+ (for Fabric/Forge: set GRADLE=/path/to/gradle),
#            and JDKs 8, 21 and 25 (JAVA8_HOME / JAVA21_HOME / JAVA25_HOME if they are not in /usr/lib/jvm).
set -uo pipefail
cd "$(dirname "$0")"
export JAVA21_HOME="${JAVA21_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}" JAVA25_HOME="${JAVA25_HOME:-/usr/lib/jvm/java-25-openjdk-amd64}"
failed=()
for step in 2-plugin-bukkit-purpur 3-mod-fabric 4-mod-forge; do
  echo; echo "################  $step  ################"
  "./$step/build-all-versions.sh" "$@" || failed+=("$step")
done
echo; echo "Finished jars in 1-download-jars/:"; ls -1 1-download-jars/*.jar
if [ ${#failed[@]} -gt 0 ]; then echo "SOME STEPS FAILED: ${failed[*]}"; exit 1; fi
