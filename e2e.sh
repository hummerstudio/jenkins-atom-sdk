#!/usr/bin/env bash
# Runs the example atom exactly like the Jenkins runner does: files in, files out, secrets resolved before launch.
set -euo pipefail
cd "$(dirname "$0")"

SDK_JAR="$(pwd)/target/jenkins-atom-sdk-0.1.0.jar"
EXAMPLE_JAR="$(pwd)/example/target/jenkins-atom-sdk-example-0.1.0.jar"
[ -f "$SDK_JAR" ] || { echo "missing $SDK_JAR — run ./build.sh first"; exit 1; }
[ -f "$EXAMPLE_JAR" ] || { echo "missing $EXAMPLE_JAR — run ./build.sh first"; exit 1; }

WORK="$(mktemp -d)"
mkdir -p "$WORK/run" "$WORK/ws"
printf '{"msg":"hello from sdk","count":"2","bkWorkspace":"%s"}' "$WORK/ws" > "$WORK/run/input.json"
printf '{"deployToken":"s3cr3t-123"}' > "$WORK/run/.secrets.json"

bk_data_dir="$WORK/run" bk_data_input=input.json bk_data_output=output.json \
  "${JAVA_HOME:-}/bin/java" -cp "$EXAMPLE_JAR:$SDK_JAR" io.github.hummerstudio.atom.sdk.AtomRunner 2>/dev/null \
  || java -cp "$EXAMPLE_JAR:$SDK_JAR" io.github.hummerstudio.atom.sdk.AtomRunner

echo "--- output.json"
cat "$WORK/run/output.json"; echo
echo "--- artifact"
cat "$WORK/ws/out/greeting.txt"
echo "--- secrets file removed: $([ -f "$WORK/run/.secrets.json" ] && echo NO || echo YES)"

python3 - "$WORK/run/output.json" <<'PY'
import json, sys
doc = json.load(open(sys.argv[1]))
assert doc["status"] == "success", doc
assert doc["data"]["greeting"]["value"] == "hello from sdk", doc
assert doc["data"]["files"]["type"] == "artifact", doc
assert doc["data"]["report"]["target"] == "index.html", doc
print("contract check: OK")
PY
rm -rf "$WORK"
