#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
JAVA_HOME=${JAVA_HOME:-/opt/data/jdk/jdk-17.0.13+11}
PATH="$JAVA_HOME/bin:$PATH"
export JAVA_HOME PATH
WORK=$(mktemp -d "${TMPDIR:-/tmp}/vehicle-tracker-calc.XXXXXX")
trap 'rm -rf "$WORK"' EXIT INT TERM
python3 "$ROOT/scripts/check_fuel_calculations.py"
python3 "$ROOT/scripts/check_oil_schedule.py"
CACHE=/opt/data/.gradle/caches/modules-2/files-2.1
KOTLIN_LIB="$CACHE/org.jetbrains.kotlin"
COMPILER=$(find "$KOTLIN_LIB/kotlin-compiler-embeddable/2.1.21" -name '*.jar' -print -quit)
STDLIB=$(find "$KOTLIN_LIB/kotlin-stdlib/2.1.21" -name '*.jar' -print -quit)
SCRIPT=$(find "$KOTLIN_LIB/kotlin-script-runtime/2.1.21" -name '*.jar' -print -quit)
COROUTINES=$(find "$CACHE/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm" -name '*.jar' -print | sort -V | tail -n 1)
TROVE=$(find "$CACHE/org.jetbrains.intellij.deps/trove4j" -name '*.jar' -print -quit)
ANNOTATIONS=$(find "$CACHE/org.jetbrains/annotations" -name '*.jar' -print -quit)
JUNIT_DIR="$CACHE/junit/junit"
JUNIT=""
[ ! -d "$JUNIT_DIR" ] || JUNIT=$(find "$JUNIT_DIR" -name '*.jar' -print -quit)
HAMCREST=""
for HAMCREST_DIR in "$CACHE/org.hamcrest/hamcrest-core" "$CACHE/org.hamcrest/hamcrest"; do
    [ ! -d "$HAMCREST_DIR" ] || HAMCREST=$(find "$HAMCREST_DIR" -name '*.jar' -print -quit)
    [ -z "$HAMCREST" ] || break
done
if [ -z "$COMPILER" ] || [ -z "$STDLIB" ] || [ -z "$SCRIPT" ] || [ -z "$COROUTINES" ] || [ -z "$TROVE" ] || [ -z "$ANNOTATIONS" ]; then
    printf '%s\n' 'Kotlin compiler dependency cache is incomplete; Kotlin smoke checks were skipped.'
    exit 0
fi
CP="$COMPILER:$STDLIB:$SCRIPT:$COROUTINES:$TROVE:$ANNOTATIONS"
mkdir -p "$WORK/classes"
JAVA_OPTS="-Xmx768m" java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
    -no-stdlib -no-reflect -classpath "$STDLIB:$ANNOTATIONS" -d "$WORK/classes" \
    "$ROOT/app/src/main/java/ca/gllavoie/vehicletracker/domain/calculation/FuelEconomy.kt" \
    "$ROOT/app/src/main/java/ca/gllavoie/vehicletracker/domain/calculation/OilSchedule.kt" \
    "$ROOT/scripts/CalculationSmoke.kt"
printf '%s\n' 'PASS: Kotlin domain calculation sources and smoke harness compile against cached Kotlin compiler.'
java -cp "$WORK/classes:$STDLIB:$ANNOTATIONS" ca.gllavoie.vehicletracker.domain.calculation.CalculationSmokeKt
if [ -z "$JUNIT" ]; then
    printf '%s\n' 'JUnit artifacts not cached; Kotlin/JUnit test suite not executed.'
    exit 0
fi
mkdir -p "$WORK/test-classes"
TEST_CP="$WORK/classes:$STDLIB:$ANNOTATIONS:$JUNIT:$HAMCREST"
JAVA_OPTS="-Xmx768m" java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
    -no-stdlib -no-reflect -classpath "$TEST_CP" -d "$WORK/test-classes" \
    "$ROOT/app/src/test/java/ca/gllavoie/vehicletracker/domain/calculation/CalculationTest.kt"
printf '%s\n' 'Kotlin/JUnit test source compiles against cached JUnit artifacts; a JUnit runner is still required to execute it.'
