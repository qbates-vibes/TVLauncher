#!/bin/sh
# Builds Simple Launcher, installs it on the connected TV, and reminds you to turn the
# Home button helper back on if it was on (Android switches it off whenever the app is updated).
set -e
cd "$(dirname "$0")"

SERVICE=com.simple.tvlauncher/com.simple.tvlauncher.HomeButtonService

./gradlew assembleRelease

ENABLED=$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')

adb install -r app/build/outputs/apk/release/app-release.apk

case "$ENABLED" in
  *"$SERVICE"*)
    echo
    echo "Android switches the Home button helper off when the app is updated."
    echo "On the TV, open Simple Launcher > Launcher settings > Open accessibility settings,"
    echo "then turn \"Simple Launcher Home button\" off and on again."
    ;;
esac

echo "Done."
