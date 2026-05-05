#!/usr/bin/env bash
# ---------------------------------------------------------------------
# Build & run the Smart Daily Timetable app without Maven.
# Requires:
#   - JDK 11+ on PATH
#   - mysql-connector-j JAR placed in ./lib/  (any 8.x version)
# ---------------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
SRC="$ROOT/src/main/java"
RES="$ROOT/src/main/resources"
OUT="$ROOT/build/classes"
LIB="$ROOT/lib"

mkdir -p "$OUT"

CP="$LIB/*"
echo "Compiling..."
find "$SRC" -name '*.java' > "$ROOT/build/sources.txt"
javac -cp "$CP" -d "$OUT" @"$ROOT/build/sources.txt"

cp "$RES/db.properties" "$OUT/"

echo "Running..."
java -cp "$OUT:$CP" com.smartscheduler.Main
