#!/usr/bin/env bash
set -Eeuo pipefail
PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
mkdir -p "$PROJECT_DIR/.run/protocol-tests"
javac -d "$PROJECT_DIR/.run/protocol-tests" \
  "$PROJECT_DIR/android-app/app/src/main/java/com/spectra/analyzer/WireProtocol.java" \
  "$PROJECT_DIR/tools/ProtocolSelfTest.java"
java -cp "$PROJECT_DIR/.run/protocol-tests" com.spectra.analyzer.ProtocolSelfTest
javac -d "$PROJECT_DIR/.run/protocol-tests" \
  "$PROJECT_DIR/android-app/app/src/main/java/com/spectra/analyzer/RadarLayout.java" \
  "$PROJECT_DIR/android-app/app/src/main/java/com/spectra/analyzer/StreamStats.java" \
  "$PROJECT_DIR/tools/PerformanceSelfTest.java"
java -cp "$PROJECT_DIR/.run/protocol-tests" com.spectra.analyzer.PerformanceSelfTest
javac -d "$PROJECT_DIR/.run/protocol-tests" \
  "$PROJECT_DIR/android-app/app/src/main/java/com/spectra/analyzer/SpectrumHistory.java" \
  "$PROJECT_DIR/tools/HistorySelfTest.java"
java -cp "$PROJECT_DIR/.run/protocol-tests" com.spectra.analyzer.HistorySelfTest
javac -d "$PROJECT_DIR/.run/protocol-tests" \
  "$PROJECT_DIR/android-app/app/src/main/java/com/spectra/analyzer/DeviceMetadata.java" \
  "$PROJECT_DIR/tools/MetadataSelfTest.java"
java -cp "$PROJECT_DIR/.run/protocol-tests" com.spectra.analyzer.MetadataSelfTest
javac -d "$PROJECT_DIR/.run/protocol-tests" \
  "$PROJECT_DIR/android-app/app/src/main/java/com/spectra/analyzer/BleSignals.java" \
  "$PROJECT_DIR/tools/BleSignalsSelfTest.java"
java -cp "$PROJECT_DIR/.run/protocol-tests" com.spectra.analyzer.BleSignalsSelfTest
