#!/usr/bin/env bash
# 打包 SDK 与示例原子。发行包 = SDK jar + 示例 jar（示例只用于验证契约）。
set -euo pipefail
cd "$(dirname "$0")"
mvn ${MVN_ARGS--o} -B -ntp package
mvn ${MVN_ARGS--o} -B -ntp -f example/pom.xml package
echo
echo "sdk    : $(pwd)/target/jenkins-atom-sdk-0.1.0.jar"
echo "example: $(pwd)/example/target/jenkins-atom-sdk-example-0.1.0.jar"
