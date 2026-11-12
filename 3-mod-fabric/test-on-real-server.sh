#!/usr/bin/env bash
# Starts a real Minecraft dedicated server with the mod (Loom's runServer), types some /abw commands into its console
# and checks the output. Usage: ./test-on-real-server.sh <mc> <fabric api> <remap> <java> <sign api>
set -uo pipefail
cd "$(dirname "$0")"
mc=$1; api=$2; remap=$3; java=$4; sign=${5:-record}
GRADLE="${GRADLE:-gradle}"
JAVA21_HOME="${JAVA21_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"; JAVA25_HOME="${JAVA25_HOME:-/usr/lib/jvm/java-25-openjdk-amd64}"
export JAVA_HOME="$JAVA25_HOME" PATH="$JAVA25_HOME/bin:$PATH"
log="build-logs/server-$mc.log"
rm -rf run && mkdir -p run && echo "eula=true" > run/eula.txt
printf 'online-mode=false\nlevel-type=flat\ngenerate-structures=false\nspawn-protection=0\nview-distance=2\nsimulation-distance=2\nserver-port=25599\n' > run/server.properties
fifo=$(mktemp -u); mkfifo "$fifo"
( sleep "${WARMUP:-75}"; for c in "abw info" "abw check what the f.u.c.k" "abw check hello world" "abw addword zorblax" "abw check zorblax" "abw removeword zorblax" "abw bogus" "stop"; do echo "$c"; sleep 4; done ) > "$fifo" &
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dantibadwords.selftest=1"
for attempt in 1 2 3; do
  timeout 420 "$GRADLE" --no-daemon runServer -Pminecraft_version="$mc" -Pfabric_version="$api" -Premap="$remap" -Pjava_release="$java" -Psign_api="$sign" \
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
check "Blocked: matches 'fuck'" "/abw check blocks f.u.c.k"
check "Clean: no blocked word found" "/abw check lets clean text through"
check "Added custom blocked word" "/abw addword"
check "Removed custom blocked word" "/abw removeword"
check "Unknown subcommand" "/abw unknown subcommand message"
grep -qE "SELFTEST FAILED|MixinTransformerError|InvalidMixinException" "$log" && { echo "  FAIL mixin errors in log"; bad=1; }
exit $bad
