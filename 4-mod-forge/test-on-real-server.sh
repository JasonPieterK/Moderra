#!/usr/bin/env bash
# Starts a real Forge dedicated server with the mod, types some /abw commands into its console and checks the output.
# Usage: ./test-on-real-server.sh <mc> <forge build> <forge major> <java> <mappings> <sign api>
set -uo pipefail
cd "$(dirname "$0")"
mc=$1; forge=$2; major=$3; java=$4; maps=$5; sign=${6:-record}
GRADLE="${GRADLE:-gradle}"
JAVA8_HOME="${JAVA8_HOME:-/usr/lib/jvm/java-8-openjdk-amd64}"
JAVA21_HOME="${JAVA21_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"; JAVA25_HOME="${JAVA25_HOME:-/usr/lib/jvm/java-25-openjdk-amd64}"
export JAVA_HOME="$JAVA25_HOME" PATH="$JAVA25_HOME/bin:$PATH"
log="build-logs/server-$mc.log"
rm -rf run && mkdir -p run && echo "eula=true" > run/eula.txt
# OLD_CONFIG=1: start with the old single-file config.yml of version 1, to check the automatic migration on a real server.
if [ "${OLD_CONFIG:-0}" = 1 ]; then
  mkdir -p run/config/antibadwords && cp ../5-shared-engine/src/test/resources/legacy/config-v1-default.yml run/config/antibadwords/config.yml
fi
printf 'online-mode=false\nlevel-type=flat\ngenerate-structures=false\nspawn-protection=0\nview-distance=2\nsimulation-distance=2\nserver-port=25598\n' > run/server.properties
fifo=$(mktemp -u); mkfifo "$fifo"
( sleep "${WARMUP:-80}"; for c in "abw info" "abw check what the f.u.c.k" "abw check hello world" "abw addword zorblax" "abw check zorblax" "abw removeword zorblax" "abw lang list" "abw lang enable de" "abw check du arschloch" "abw lang disable de" "abw whitelist add zorbwhite" "abw whitelist list 99" "abw whitelist remove ZORBWHITE" "abw reload" "abw bogus" "stop"; do echo "$c"; sleep 4; done ) > "$fifo" &
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dantibadwords.selftest=1"
for attempt in 1 2 3; do
  timeout 480 "$GRADLE" --no-daemon runServer -Pminecraft_version="$mc" -Pforge_version="$forge" -Pforge_major="$major" \
    -Pjava_release="$java" -Pmappings_version="$maps" -Psign_api="$sign" \
    -Dorg.gradle.java.installations.paths="$JAVA25_HOME,$JAVA21_HOME,$JAVA8_HOME" < "$fifo" > "$log" 2>&1
  grep -q " 429 " "$log" && { sleep 40; continue; }; break
done
rm -f "$fifo"
check() { if grep -q "$1" "$log"; then echo "  ok   $2"; else echo "  FAIL $2"; bad=1; fi; }
bad=0; echo "--- real server check, Forge for Minecraft $mc"
check "SELFTEST loaded+mixed net.minecraft.commands.Commands" "command mixin applied"
check "SELFTEST loaded+mixed net.minecraft.server.network.ServerGamePacketListenerImpl" "sign + book mixins applied"
check "SELFTEST loaded+mixed net.minecraft.world.inventory.AnvilMenu" "anvil mixin applied"
check "SELFTEST loaded+mixed net.minecraft.world.inventory.ItemCombinerMenu" "anvil-close mixin applied"
check "Blocked: matches 'fuck'" "/abw check blocks f.u.c.k"
check "Clean: no blocked word found" "/abw check lets clean text through"
check "Added custom blocked word" "/abw addword"
check "Removed custom blocked word" "/abw removeword"
check "Unknown subcommand" "/abw unknown subcommand message"
# Added with the shared engine (folder config, /abw lang, /abw whitelist). NOT RUN YET, see handoffs/BUILD-VERIFICATION-TODO.md.
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
grep -qE "SELFTEST FAILED|MixinTransformerError|InvalidMixinException" "$log" && { echo "  FAIL mixin errors in log"; bad=1; }
exit $bad
