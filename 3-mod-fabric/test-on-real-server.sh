#!/usr/bin/env bash
# Starts a real Minecraft dedicated server with the mod (Loom's runServer), types some /moderra commands into its console
# and checks the output. Usage: ./test-on-real-server.sh <mc> <fabric api> <remap> <java> <sign api> [menu api] [placeholders api] [placeholder-api version]
set -uo pipefail
cd "$(dirname "$0")"
mc=$1; api=$2; remap=$3; java=$4; sign=${5:-record}; menu=${6:-input}; ph=${7:-none}; phv=${8:-}
GRADLE="${GRADLE:-gradle}"
JAVA21_HOME="${JAVA21_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"; JAVA25_HOME="${JAVA25_HOME:-/usr/lib/jvm/java-25-openjdk-amd64}"
export JAVA_HOME="$JAVA25_HOME" PATH="$JAVA25_HOME/bin:$PATH"
log="build-logs/server-$mc.log"
rm -rf run && mkdir -p run && echo "eula=true" > run/eula.txt
# OLD_CONFIG=1: start with the old single-file config.yml of version 1, to check the automatic migration on a real server.
if [ "${OLD_CONFIG:-0}" = 1 ]; then
  mkdir -p run/config/antibadwords && cp ../5-shared-engine/src/test/resources/legacy/config-v1-default.yml run/config/antibadwords/config.yml   # old folder name: the mod must move it to config/moderra
fi
printf 'online-mode=false\nlevel-type=flat\ngenerate-structures=false\nspawn-protection=0\nview-distance=2\nsimulation-distance=2\nserver-port=25599\n' > run/server.properties
fifo=$(mktemp -u); mkfifo "$fifo"
( sleep "${WARMUP:-75}"; for c in "moderra info" "moderra check what the f.u.c.k" "moderra check hello world" "moderra addword zorblax" "moderra check zorblax" "moderra removeword zorblax" "moderra lang list" "moderra lang enable de" "moderra check du arschloch" "moderra lang disable de" "moderra whitelist add zorbwhite" "moderra whitelist list 99" "moderra whitelist remove ZORBWHITE" "moderra reload" "moderra bogus" "stop"; do echo "$c"; sleep 4; done ) > "$fifo" &
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dmoderra.selftest=1"
for attempt in 1 2 3; do
  timeout 420 "$GRADLE" --no-daemon runServer -Pminecraft_version="$mc" -Pfabric_version="$api" -Premap="$remap" -Pjava_release="$java" -Psign_api="$sign" -Pmenu_api="$menu" -Pplaceholders_api="$ph" -Pplaceholder_api_version="$phv" \
    -Dorg.gradle.java.installations.paths="$JAVA25_HOME,$JAVA21_HOME" < "$fifo" > "$log" 2>&1
  grep -q " 429 " "$log" && { sleep 40; continue; }; break
done
rm -f "$fifo"
check() { if grep -q "$1" "$log"; then echo "  ok   $2"; else echo "  FAIL $2"; bad=1; fi; }
bad=0; echo "--- real server check, Minecraft $mc"
check "SELFTEST loaded+mixed net.minecraft.commands.Commands" "command mixin applied"
check "SELFTEST loaded+mixed net.minecraft.server.network.ServerGamePacketListenerImpl" "sign + book mixins applied"
check "SELFTEST loaded+mixed net.minecraft.world.inventory.AnvilMenu" "anvil mixin applied"
check "SELFTEST loaded+mixed net.minecraft.world.inventory.ItemCombinerMenu" "anvil-close mixin applied"
check "Blocked: matches 'fuck'" "/moderra check blocks f.u.c.k"
check "Clean: no blocked word found" "/moderra check lets clean text through"
check "Added custom blocked word" "/moderra addword"
check "Removed custom blocked word" "/moderra removeword"
check "Unknown subcommand" "/moderra unknown subcommand message"
# Added with the shared engine (folder config, /moderra lang, /moderra whitelist). NOT RUN YET, see handoffs/BUILD-VERIFICATION-TODO.md.
if [ "${OLD_CONFIG:-0}" = 1 ]; then
  check "Old single-file config.yml found" "old config.yml is migrated on first start"
  check "Languages switched on (same as before): en, id, pt-br, ru, en-gb, de, zh, custom" "migration keeps the same languages"
  check "Language de is already on" "/moderra lang enable (German was already on in the old config)"
else
  check "Created config files: config.yml, moderation/ladder.yml, worlds.yml" "first start creates the config folder"
  check "Language de (German) switched on" "/moderra lang enable"
fi
check "Copied [0-9]* word-list files into wordlists/" "word lists are copied out of the jar"
check "Languages ([0-9]* of [0-9]* on" "/moderra lang list"
check "Blocked: matches 'arschloch'" "a German word is blocked while de is on"
check "Language de switched off" "/moderra lang disable"
check "Added to the whitelist: zorbwhite" "/moderra whitelist add"
check "Whitelist ([0-9]* words)" "/moderra whitelist list"
check "Removed from the whitelist: zorbwhite" "/moderra whitelist remove (capital letters do not matter)"
check "Moderra config reloaded" "/moderra reload"
grep -q "Config problem in" "$log" && { echo "  FAIL config problems reported in log"; bad=1; }
grep -qE "SELFTEST FAILED|MixinTransformerError|InvalidMixinException" "$log" && { echo "  FAIL mixin errors in log"; bad=1; }
exit $bad
