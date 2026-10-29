#!/usr/bin/env bash
# Fails when the Android application ID, or the launcher activity, is not the same everywhere it is repeated.
# Gradle: applicationId == namespace. The CMake APP_ID (PGO data and the perf log live in Android/media/<APP_ID>), the
# device tools (tools/gbsat.bat, tools/gbsat.ps1) and the manifest's launcher activity must agree with them.
set -u
cd "$(dirname "$0")/.."
gradle=apps/android/app/build.gradle.kts
cmake=apps/android/app/src/main/cpp/CMakeLists.txt
manifest=apps/android/app/src/main/AndroidManifest.xml
bridge=apps/android/app/src/main/java/app/gbsat/emulator/NativeBridge.kt
native_jni=apps/android/app/src/main/cpp/gbsat_jni.cpp
native_ra=apps/android/app/src/main/cpp/gbsat_ra.cpp
fail=0

id=$(sed -n 's/^[[:space:]]*applicationId = "\([^"]*\)".*/\1/p' "$gradle")
ns=$(sed -n 's/^[[:space:]]*namespace = "\([^"]*\)".*/\1/p' "$gradle")
cm=$(sed -n 's/.*APP_ID "\([^"]*\)".*/\1/p' "$cmake")
bat=$(sed -n 's/^set APP=\(.*\)$/\1/p' tools/gbsat.bat | tr -d '\r')
ps=$(awk -F"'" '/^\$App[[:space:]]*=/ {print $2}' tools/gbsat.ps1)
bat_act=$(sed -n 's/^set ACTIVITY=\(.*\)$/\1/p' tools/gbsat.bat | tr -d '\r')
ps_act=$(awk -F"'" '/^\$Activity[[:space:]]*=/ {print $2}' tools/gbsat.ps1)
source_pkg=$(sed -n 's/^package[[:space:]]*//p' "$bridge" | head -1)
jni_prefix="Java_$(printf '%s' "$id" | tr . _)_NativeBridge_"
jni_class="$(printf '%s' "$id" | tr . /)/NativeBridge"

man_act=$(grep -B1 -A8 'android.intent.category.LAUNCHER' "$manifest" >/dev/null && \
          sed -n '/<activity/,/<\/activity>/p' "$manifest" | sed -n 's/.*android:name="\([^"]*\)".*/\1/p' | head -1)

check() { # name actual expected
  if [ "$2" != "$3" ]; then echo "ERROR: $1 is '$2', expected '$3'"; fail=1; fi
}
[ -n "$id" ] || { echo "ERROR: applicationId not found in $gradle"; exit 1; }
check "namespace (build.gradle.kts)"        "$ns"      "$id"
check "Kotlin source package"                "$source_pkg" "$id"
if ! grep -Fq "$jni_prefix" "$native_jni" "$native_ra"; then echo "ERROR: JNI exports do not use $id.NativeBridge"; fail=1; fi
if ! grep -Fq "FindClass(\"$jni_class\")" "$native_ra"; then echo "ERROR: JNI FindClass does not use $jni_class"; fail=1; fi
check "APP_ID (CMakeLists.txt)"             "$cm"      "$id"
check "APP (tools/gbsat.bat)"               "$bat"     "$id"
check "\$App (tools/gbsat.ps1)"             "$ps"      "$id"
check "ACTIVITY (tools/gbsat.bat)"          "$bat_act" "$man_act"
check "\$Activity (tools/gbsat.ps1)"        "$ps_act"  "$man_act"

# Android/media/<id> must come from GB_APP_ID, never be typed again in native code or Kotlin
if grep -rnE 'Android/media/[a-z]+\.[a-z]+\.[a-z]+' apps/android/app/src/main/cpp apps/android/app/src/main/java --include=*.cpp --include=*.hpp --include=*.kt | grep -v '^\s*//' ; then
  echo "ERROR: a hard-coded Android/media/<package> path (use GB_APP_ID in native code, packageName in Kotlin)"; fail=1
fi

if [ "$fail" = 0 ]; then echo "OK: applicationId $id, activity $man_act"; fi
exit $fail
