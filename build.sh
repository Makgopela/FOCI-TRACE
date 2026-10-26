#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
rm -rf out foci-trace.jar
mkdir -p out
javac --add-modules jdk.httpserver -encoding UTF-8 -cp lib/hsqldb.jar -d out $(find src -name '*.java')
java -cp out:lib/hsqldb.jar za.co.focigroup.focitrace.SelfTest
jar cfm foci-trace.jar MANIFEST.MF -C out .
echo "Built foci-trace.jar"
