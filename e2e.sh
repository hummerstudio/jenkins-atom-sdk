#!/usr/bin/env bash
# Runs the example atom exactly like the Jenkins runner does: files in, files out, secrets resolved before launch.
set -euo pipefail
cd "$(dirname "$0")"
VERSION="$(sed -n 's|.*<version>\([^<]*\)</version>.*|\1|p' pom.xml | head -1)"

SDK_JAR="$(pwd)/target/jenkins-atom-sdk-${VERSION}.jar"
EXAMPLE_JAR="$(pwd)/example/target/jenkins-atom-sdk-example-${VERSION}.jar"
[ -f "$SDK_JAR" ] || { echo "missing $SDK_JAR — run ./build.sh first"; exit 1; }
[ -f "$EXAMPLE_JAR" ] || { echo "missing $EXAMPLE_JAR — run ./build.sh first"; exit 1; }

WORK="$(mktemp -d)"
mkdir -p "$WORK/run" "$WORK/ws"
printf '{"msg":"hello from sdk","count":"2","bkWorkspace":"%s"}' "$WORK/ws" > "$WORK/run/input.json"
printf '{"deployToken":"s3cr3t-123"}' > "$WORK/run/.secrets.json"

# the runner reads atom_data_* since the rename; the old bk_data_* names made it run against the cwd and
# write output.json somewhere the check never looked
atom_data_dir="$WORK/run" atom_data_input=input.json atom_data_output=output.json \
  atom_workspace="$WORK/ws" "${JAVA_HOME:-}/bin/java" \
  -cp "$EXAMPLE_JAR:$SDK_JAR" com.jenkinschina.atom.sdk.AtomRunner

echo "--- output.json"
cat "$WORK/run/output.json"; echo
echo "--- artifact"
# follow the path the atom reported: the example writes next to its data directory, not into the workspace,
# and a check that hardcodes the path tests the guess rather than the contract
ARTIFACT="$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["data"]["files"]["value"][0])' "$WORK/run/output.json")"
cat "$ARTIFACT"
echo "--- secrets file removed: $([ -f "$WORK/run/.secrets.json" ] && echo NO || echo YES)"

python3 - "$WORK/run/output.json" <<'PY'
import json, sys
doc = json.load(open(sys.argv[1]))
assert doc["status"] == "success", doc
assert doc["data"]["greeting"]["value"] == "hello from sdk", doc
assert doc["data"]["files"]["type"] == "artifact", doc
import os
assert os.path.exists(doc["data"]["files"]["value"][0]), doc["data"]["files"]
assert doc["data"]["report"]["target"] == "index.html", doc
print("contract check: OK")
PY
rm -rf "$WORK"
