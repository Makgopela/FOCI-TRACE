#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
[ -f foci-trace.jar ] || ./build.sh
exec java --add-modules jdk.httpserver -jar foci-trace.jar
