#!/usr/bin/env bash
# Compiles the engine + simulator on the JVM and prints the accuracy report.
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=${TMPDIR:-/tmp}/redzero-sim
mkdir -p "$OUT"
kotlinc app/src/main/java/com/redzero/keyboard/engine/*.kt app/src/test/java/com/redzero/keyboard/engine/{TestKeyboards,Sentences,Simulation}.kt \
  -d "$OUT/sim.jar" 2>&1 | grep -v '^warning' || true
kotlin -cp "$OUT/sim.jar" com.redzero.keyboard.engine.Simulation
