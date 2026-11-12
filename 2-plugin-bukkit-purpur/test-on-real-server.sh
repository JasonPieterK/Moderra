#!/usr/bin/env bash
# Starts a real Spigot / Paper / Purpur server with the plugin jar, types some /abw commands into its console and checks
# the output (the same checks the Fabric and Forge scripts do, without the mixin checks).
#
#   SERVER_JAR=/path/to/paper-26.3.jar ./test-on-real-server.sh 26.3
#   OLD_CONFIG=1 SERVER_JAR=... ./test-on-real-server.sh 26.3     also checks the migration of the old single config.yml
#
# !!! THIS SCRIPT HAS NEVER BEEN RUN. It was written in an environment that could not download a server jar or the
# !!! Spigot API (see ../handoffs/BUILD-VERIFICATION-TODO.md). Expect to fix small things the first time you use it.
#
# You bring the server jar yourself (Paper: https://papermc.io/downloads, Purpur: https://purpurmc.org); this script
# does not download anything. Needs the JDK the Minecraft version wants (25 for 26.x, 21 for 1.21.11) as JAVA_HOME or on PATH.
set -uo pipefail
cd "$(dirname "$0")"
mc=${1:?usage: SERVER_JAR=/path/to/server.jar ./test-on-real-server.sh <minecraft version>}
: "${SERVER_JAR:?set SERVER_JAR to a Spigot, Paper or Purpur server jar for Minecraft $mc}"
server_jar=$(cd "$(dirname "$SERVER_JAR")" && pwd)/$(basename "$SERVER_JAR")
plugin_jar="../1-download-jars/Antibadwords-RELEASE[Bukkit] - 1-${mc}.jar"
[ -f "$plugin_jar" ] || { echo "missing $plugin_jar - build it first with ./build-all-versions.sh $mc"; exit 2; }
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"

mkdir -p build-logs
log="$PWD/build-logs/server-$mc.log"
rm -rf run && mkdir -p run/plugins && echo "eula=true" > run/eula.txt
cp "$plugin_jar" run/plugins/
printf 'online-mode=false\nlevel-type=flat\ngenerate-structures=false\nspawn-protection=0\nview-distance=2\nsimulation-distance=2\nserver-port=25597\n' > run/server.properties
if [ "${OLD_CONFIG:-0}" = 1 ]; then
  mkdir -p run/plugins/AntiBadWords && cp ../5-shared-engine/src/test/resources/legacy/config-v1-default.yml run/plugins/AntiBadWords/config.yml
fi
fifo=$(mktemp -u); mkfifo "$fifo"
( sleep "${WARMUP:-60}"; for c in "abw info" "abw check what the f.u.c.k" "abw check hello world" "abw addword zorblax" "abw check zorblax" "abw removeword zorblax" "abw lang list" "abw lang enable de" "abw check du arschloch" "abw lang disable de" "abw whitelist add zorbwhite" "abw whitelist list 99" "abw whitelist remove ZORBWHITE" "abw reload" "abw bogus" "stop"; do echo "$c"; sleep 4; done ) > "$fifo" &
( cd run && timeout 420 "$java_bin" -Xmx2G -jar "$server_jar" --nogui < "$fifo" > "$log" 2>&1 )
rm -f "$fifo"

check() { if grep -q "$1" "$log"; then echo "  ok   $2"; else echo "  FAIL $2"; bad=1; fi; }
bad=0; echo "--- real server check, Bukkit plugin on Minecraft $mc ($(basename "$SERVER_JAR"))"
check "Blocked: matches 'fuck'" "/abw check blocks f.u.c.k"
check "Clean: no blocked word found" "/abw check lets clean text through"
check "Added custom blocked word" "/abw addword"
check "Removed custom blocked word" "/abw removeword"
check "Unknown subcommand" "/abw unknown subcommand message"
if [ "${OLD_CONFIG:-0}" = 1 ]; then
  check "Old single-file config.yml found" "old config.yml is migrated on first start"
  check "Languages switched on (same as before): en, id, pt-br, ru, en-gb, de, zh, custom" "migration keeps the same languages"
  check "Language de is already on" "/abw lang enable (German was already on in the old config)"
else
  check "Created config files: config.yml, moderation/ladder.yml, worlds.yml" "first start creates the config folder"
  check "Language de (German) switched on" "/abw lang enable"
fi
check "Copied [0-9]* word-list files into wordlists/" "word lists are copied out of the jar"
check "Languages ([0-9]* of [0-9]* on" "/abw lang list"
check "Blocked: matches 'arschloch'" "a German word is blocked while de is on"
check "Language de switched off" "/abw lang disable"
check "Added to the whitelist: zorbwhite" "/abw whitelist add"
check "Whitelist ([0-9]* words)" "/abw whitelist list"
check "Removed from the whitelist: zorbwhite" "/abw whitelist remove (capital letters do not matter)"
check "AntiBadWords config reloaded" "/abw reload"
grep -q "Config problem in" "$log" && { echo "  FAIL config problems reported in log"; bad=1; }
grep -qE "Error occurred while enabling AntiBadWords|NoClassDefFoundError|NoSuchMethodError" "$log" && { echo "  FAIL plugin errors in log"; bad=1; }
echo "(to also run the scripted player: start the server yourself and run ../5-shared-engine/live-player-test/bot.js against it)"
exit $bad
