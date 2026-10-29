#!/usr/bin/env bash
# 打包 SDK 与示例原子。发行包 = SDK jar + 示例 jar（示例只用于验证契约）。
set -euo pipefail
cd "$(dirname "$0")"
VERSION="$(sed -n 's|.*<version>\([^<]*\)</version>.*|\1|p' pom.xml | head -1)"
mvn ${MVN_ARGS--o} -B -ntp package
mvn ${MVN_ARGS--o} -B -ntp -f example/pom.xml package
echo
echo "sdk    : $(pwd)/target/jenkins-atom-sdk-${VERSION}.jar"
echo "example: $(pwd)/example/target/jenkins-atom-sdk-example-${VERSION}.jar"
