#!/bin/sh
# Sandbox-friendly build+test without Maven: compiles against the jars in lib/
# (gitignored, fetched from Maven Central) using $JAVA_HOME or `java` on PATH.
set -e
cd "$(dirname "$0")/.."
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}"
CP="lib/jme3-core-3.8.1-stable.jar:lib/jme3-desktop-3.8.1-stable.jar:lib/lemur-1.16.0.jar:lib/guava-19.0.jar:lib/slf4j-api-1.7.32.jar:lib/slf4j-simple-1.7.32.jar"
TESTCP="$CP:lib/junit-platform-console-standalone-1.10.2.jar"
rm -rf out && mkdir -p out/main out/test
"${JAVA_BIN}javac" --release 11 -cp "$CP" -d out/main $(find src/main/java -name '*.java')
"${JAVA_BIN}javac" --release 11 -cp "$TESTCP:out/main" -d out/test $(find src/test/java -name '*.java')
"${JAVA_BIN}java" -cp "out/main:out/test:$TESTCP" org.junit.platform.console.ConsoleLauncher execute -c com.lemurguiinspector.GuiDumperTest --details=tree
