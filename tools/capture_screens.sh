#!/usr/bin/env bash
# Regenera docs/capturas con el emulador Wear OS abierto (p. ej. "Wear OS Large Round").
# Instala la app y el test con "adb install -r" (no borra los datos de la app), corre
# ScreenshotsTest y baja las imágenes. Uso, desde la raíz del repo: bash tools/capture_screens.sh
set -euo pipefail
export MSYS_NO_PATHCONV=1

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ADB="${ADB:-adb}"
OUT="$ROOT/docs/capturas"

cd "$ROOT/watchapp"
./gradlew -q :app:assembleDebug :app:assembleDebugAndroidTest
"$ADB" install -r -t app/build/outputs/apk/debug/app-debug.apk
"$ADB" install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
"$ADB" shell am instrument -w \
    -e class com.rumbo.watchapp.presentation.ScreenshotsTest \
    com.rumbo.watchapp.test/androidx.test.runner.AndroidJUnitRunner

mkdir -p "$OUT"
cd "$OUT"
rm -f ./*.png
"$ADB" pull /sdcard/Android/data/com.rumbo.watchapp/files/capturas/. .
