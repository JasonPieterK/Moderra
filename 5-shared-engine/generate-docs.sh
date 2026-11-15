#!/usr/bin/env bash
# Rewrites the two generated, committed files:
#   6-guides/CONFIGURATION.md                                   (from the settings registry in the code)
#   5-shared-engine/src/main/resources/wordlists-index.txt      (from the files in 7-wordlists/)
# Run it after you add or change a setting, or add a language file. A unit test fails when you forget.
# Needs Maven and a JDK 21+.
set -euo pipefail
cd "$(dirname "$0")"
mvn -q -B compile
snakeyaml="${SNAKEYAML_JAR:-$HOME/.m2/repository/org/yaml/snakeyaml/2.3/snakeyaml-2.3.jar}"
java -cp "target/classes:$snakeyaml" com.jason.plugins.moderra.engine.tools.GenerateDocs ..
