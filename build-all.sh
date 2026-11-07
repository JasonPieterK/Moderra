#!/usr/bin/env bash
# Builds one AntiBadWords jar per supported Minecraft release into ./dist
#
#   ./build-all.sh                 build every version below
#   ./build-all.sh 26.2 26.3       build only the listed versions
#
# Requirements: Maven 3.9+, JDK 21 (for 1.21.x) and JDK 25 (for 26.x, which needs Java 25).
# Point JAVA21_HOME / JAVA25_HOME at the JDKs; if unset, the JDK on PATH is used for both.
#
# To support a new Minecraft release add one line to MINECRAFT_VERSIONS:
#   "<minecraft version>|<plugin.yml api-version>|<spigot-api version>|<java release>"
# The jar only uses Bukkit/Spigot API, so it runs on Spigot and Paper.
# Spigot API versions: https://hub.spigotmc.org/nexus/content/repositories/snapshots/org/spigotmc/spigot-api/
set -euo pipefail
cd "$(dirname "$0")"

MINECRAFT_VERSIONS=(
  # Releases from the last 12 months (no snapshots / pre-releases / betas).
  "1.21.11|1.21|1.21.11-R0.1-SNAPSHOT|21"
  "26.1|26.1|26.1-R0.1-SNAPSHOT|25"
  "26.1.1|26.1|26.1.1-R0.1-SNAPSHOT|25"
  "26.1.2|26.1|26.1.2-R0.1-SNAPSHOT|25"
  "26.2|26.2|26.2-R0.1-SNAPSHOT|25"
  "26.3|26.3|26.3-R0.1-SNAPSHOT|25"
)

wanted=("$@")
mkdir -p dist
failed=()

for entry in "${MINECRAFT_VERSIONS[@]}"; do
  IFS='|' read -r mc api paper java <<<"${entry%%#*}"
  paper="${paper%% *}"; java="${java%% *}"
  if [ ${#wanted[@]} -gt 0 ] && [[ ! " ${wanted[*]} " =~ " ${mc} " ]]; then
    continue
  fi

  jdk_var="JAVA${java}_HOME"
  jdk_home="${!jdk_var:-}"
  echo "=== Minecraft ${mc} (spigot-api ${paper}, Java ${java}) ==="
  if ( [ -n "$jdk_home" ] && export JAVA_HOME="$jdk_home" PATH="$jdk_home/bin:$PATH"; \
       mvn -B -q clean package -Dmc.version="$mc" -Dmc.api.version="$api" \
           -Dspigot.api.version="$paper" -Djava.release="$java" ); then
    cp target/AntiBadWords."$mc".V*.jar dist/
  else
    echo "!!! build FAILED for ${mc}"; failed+=("$mc")
  fi
done

echo
ls -1 dist/*.jar
if [ ${#failed[@]} -gt 0 ]; then echo "FAILED: ${failed[*]}"; exit 1; fi
