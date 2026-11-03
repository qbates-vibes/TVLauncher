#!/bin/sh
# Saves Simple Launcher setups from a TV, and loads them onto a TV.
#   ./tvstate.sh save NAME [TV] [--no-media]   copy the TV's setup, pictures and music to profiles/NAME.zip
#   ./tvstate.sh load NAME|FILE.zip [TV] [--no-media]
#                                              replace the TV's setup (--no-media keeps the TV's own pictures and music)
#   ./tvstate.sh list                          show the setups in profiles/
# TV is the address from `adb devices` (like 192.168.1.42:5555); it's only needed with more than one TV connected.
# The setup a load replaces stays on the TV: Launcher settings > Saved setups.
set -e
cd "$(dirname "$0")"

PKG=com.simple.tvlauncher
DIR=/sdcard/Android/data/$PKG/files
PROFILES=profiles

usage() {
  sed -n '2,8p' "$0" | sed 's/^# \{0,1\}//'
  exit 1
}

[ $# -gt 0 ] || usage
COMMAND=$1
shift
NAME=
MEDIA=1
for arg in "$@"; do
  case "$arg" in
    --no-media) MEDIA= ;;
    -*) usage ;;
    *) if [ -z "$NAME" ]; then NAME=$arg; else export ANDROID_SERIAL="$arg"; fi ;;
  esac
done

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

check_tv() {
  if ! adb shell pm path "$PKG" >/dev/null 2>&1; then
    echo "Simple Launcher isn't installed on this TV, or the TV isn't connected (check: adb devices)."
    exit 1
  fi
}

# Copies the files directly inside a TV folder ("" is the top folder) into media/KIND.
pull_files() {
  mkdir -p "$WORK/setup/media/$2"
  adb shell "find '$DIR/$1' -maxdepth 1 -type f 2>/dev/null" </dev/null | tr -d '\r' | while IFS= read -r file; do
    adb pull "$file" "$WORK/setup/media/$2/" </dev/null >/dev/null 2>&1
  done
}

count() {
  find "$WORK/setup/media/$1" -type f 2>/dev/null | wc -l | tr -d ' '
}

case "$COMMAND" in
  save)
    [ -n "$NAME" ] || usage
    case "$NAME" in
      *[!A-Za-z0-9._-]*) echo "Use only letters, numbers, dots, dashes and underscores in the name."; exit 1 ;;
    esac
    check_tv
    mkdir -p "$WORK/setup"
    if ! adb pull "$DIR/state/current.json" "$WORK/setup/state.json" >/dev/null 2>&1; then
      echo "The TV hasn't written its setup yet. Open Simple Launcher on the TV once, then try again."
      echo "(If it still fails, the TV has an older Simple Launcher: run ./install.sh first.)"
      exit 1
    fi
    if [ -n "$MEDIA" ]; then
      pull_files "" wallpaper
      pull_files logo logo
      pull_files music music
    fi
    mkdir -p "$PROFILES"
    rm -f "$PROFILES/$NAME.zip"
    (cd "$WORK/setup" && zip -qr - .) > "$PROFILES/$NAME.zip"
    if [ -n "$MEDIA" ]; then
      echo "Saved $PROFILES/$NAME.zip (wallpapers: $(count wallpaper), logo: $(count logo), songs: $(count music))."
    else
      echo "Saved $PROFILES/$NAME.zip (settings only, no pictures or music)."
    fi
    ;;

  load)
    [ -n "$NAME" ] || usage
    case "$NAME" in
      *.zip) ZIP=$NAME ;;
      *) ZIP=$PROFILES/$NAME.zip ;;
    esac
    [ -f "$ZIP" ] || { echo "There's no $ZIP. Saved setups: ./tvstate.sh list"; exit 1; }
    LABEL=$(basename "$ZIP" .zip | tr -c 'A-Za-z0-9._\n-' '_')
    mkdir -p "$WORK/setup"
    unzip -q "$ZIP" -d "$WORK/setup"
    [ -f "$WORK/setup/state.json" ] || { echo "$ZIP has no state.json, so it isn't a saved setup."; exit 1; }
    [ -n "$MEDIA" ] || rm -rf "$WORK/setup/media"
    check_tv

    # Copy under a temporary name, then rename, so the TV never reads a half-copied setup.
    # adb push can't create folders in the app's storage, so make them first and push file by file.
    INBOX=$DIR/state/inbox
    PARTIAL=$INBOX/$LABEL.partial
    adb shell "rm -rf '$PARTIAL' '$INBOX/$LABEL' && rm -f '$DIR/state/last-import.json' && mkdir -p '$PARTIAL'"
    adb push "$WORK/setup/state.json" "$PARTIAL/" >/dev/null 2>&1 || { echo "Couldn't copy the setup to the TV."; exit 1; }
    if [ -d "$WORK/setup/media" ]; then
      adb shell "mkdir -p '$PARTIAL/media/wallpaper' '$PARTIAL/media/logo' '$PARTIAL/media/music'"
      for kind in wallpaper logo music; do
        for file in "$WORK/setup/media/$kind"/*; do
          [ -f "$file" ] || continue
          adb push "$file" "$PARTIAL/media/$kind/" >/dev/null 2>&1 || { echo "Couldn't copy $file to the TV."; exit 1; }
        done
      done
    fi
    adb shell "mv '$PARTIAL' '$INBOX/$LABEL'"

    # Only bring the launcher forward if it's already on screen, so nobody watching gets interrupted.
    if adb shell dumpsys activity activities | grep -m1 'ResumedActivity' | grep -q "$PKG/"; then
      adb shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1
      i=0
      while [ $i -lt 20 ]; do
        if RESULT=$(adb shell "cat '$DIR/state/last-import.json'" 2>/dev/null) && [ -n "$RESULT" ]; then
          echo "$RESULT"
          exit 0
        fi
        sleep 0.5
        i=$((i + 1))
      done
    fi
    echo "Copied. The TV loads it the next time Simple Launcher opens (press Home)."
    ;;

  list)
    if ls "$PROFILES"/*.zip >/dev/null 2>&1; then
      ls -lh "$PROFILES"/*.zip | awk '{ print $5 "\t" $NF }'
    else
      echo "No saved setups yet. Make one with: ./tvstate.sh save NAME"
    fi
    ;;

  *) usage ;;
esac
