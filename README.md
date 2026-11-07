# Simple Launcher for Google TV

A clean, fast, ad-free home screen for Android TV / Google TV.

- Grid of your installed apps (TV banners where available, icons otherwise)
- Favorites row you can add to, remove from, and reorder
- Hide apps you never use
- Shortcuts to TV Settings and to TV inputs (HDMI)
- Clock in the top-right corner
- Dark theme with a solid color, or one of your own wallpaper pictures or looping videos
- Three home screen styles, switched under **Launcher settings → Home screen style**:
  - **Classic:** favorites and a grid of all your apps
  - **Scenic:** like Classic without favorites, with only the first row of apps showing near the bottom so your wallpaper shows. The apps rest semi-transparent and turn solid as soon as you press a button; after 15 seconds without a press they fade back. Set how see-through they rest with **Apps when idle**, under the style button. Press Down to bring up the rest
  - **Hero select:** game-style, with apps grouped by role
- Back up your setup, copy it to another TV, or share it ([see below](#back-up-copy-or-share-your-setup))
- **No internet permission.** No recommendations, ads or tracking. Everything is stored on the TV.

Built with Kotlin + Jetpack Compose for TV. Works on Android 9 and newer (every Google TV qualifies).

**On a Fire TV?** Build it with steps 1 and 2 below, then follow [docs/fire-tv.md](docs/fire-tv.md) instead of steps 3 to 5.

---

## Using it with the remote

| Button | What it does |
|---|---|
| D-pad | Move around. The selected item gets a white outline and grows slightly. |
| OK | Open the app |
| **Hold OK** on an app | Options: Open, Add to/Remove from favorites, Move, Hide app, App info |
| Back | Leaves the settings screen or move mode (it never leaves the home screen) |

The **Stock home** button opens the TV's own home screen. The Home button helper stands aside until you open Simple Launcher again.

**To reorder apps:** hold OK on an app and choose **Move**. The tile gets a yellow outline. Press Left/Right to move it one place, or Up/Down to move it a whole row (Up/Down only work in the All apps grid). Press OK or Back when you're done. Do this on a tile in the Favorites row to reorder favorites, or on a tile in All apps to reorder the grid. To go back to A–Z order, open **Launcher settings → App order**.

**To unhide an app:** go to **Launcher settings**, then **Hidden apps**.

---

## Step 1: Install the tools on your Mac (one time)

You'll type these commands into the **Terminal** app (press ⌘-Space, type "Terminal", press Enter). Copy each line, paste it, and press Enter.

**1a. Java 17 and the Android command-line tools** (you already have Homebrew). Temurin is a ready-made build of Java 17; it may ask for your Mac password:

```sh
brew install --cask temurin@17
brew install --cask android-commandlinetools
```

**1b. Tell your Mac where they are.** This adds three lines to your shell settings file, so new Terminal windows always know where the tools are:

```sh
cat >> ~/.zshrc <<'EOF'
export JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null)"
export ANDROID_HOME="$(brew --prefix)/share/android-commandlinetools"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
EOF
source ~/.zshrc
```

Check it worked: `java -version` should now say **17** (not 1.8).

**1c. Download the Android pieces needed for building, and `adb`:**

```sh
sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

`--licenses` asks you to accept Google's licenses: type `y` and press Enter each time.

Check it worked: `adb version` prints a version number.

## Step 2: Build the app

```sh
cd ~/Repos/TVLauncher
./gradlew assembleRelease
```

The first build takes a few minutes because it downloads Gradle and libraries. That download happens on your Mac; the app itself never uses the internet. When you see **BUILD SUCCESSFUL**, the app file is at:

```
app/build/outputs/apk/release/app-release.apk
```

(This is an optimized build, signed with a key that was generated on your Mac. That's fine for installing it on your own TV.)

## Step 3: Connect to the TV and install

**3a. Find the TV's IP address:** on the TV, go to **Settings → Network & Internet →** your Wi-Fi network. It looks like `192.168.1.42`.

**3b. Connect.** Use whichever option matches what your TV shows:

- **Option 1: plain network debugging** (most TCL TVs). Run:
  ```sh
  adb connect 192.168.1.42:5555
  ```
  A prompt appears on the TV: choose **Always allow from this computer**, then **OK**.

- **Option 2: "Wireless debugging" with a pairing code.** On the TV, go to **Developer options → Wireless debugging → Pair device with pairing code**. The TV shows an IP:port and a 6-digit code. Run:
  ```sh
  adb pair 192.168.1.42:PAIRING_PORT      # type the 6-digit code when asked
  adb connect 192.168.1.42:CONNECT_PORT   # the port shown on the main Wireless debugging screen
  ```

Check it worked: `adb devices` should list the TV as `device`. If it says `unauthorized`, accept the prompt on the TV.

Optional: check your exact model and Android version.

```sh
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
```

**3c. Install:**

```sh
adb install -r app/build/outputs/apk/release/app-release.apk
```

`-r` means "replace if already installed".

**Updating later:** after you or Claude change the code, run `./install.sh` from the project folder. It builds the app and installs it on the TV in one step. Your favorites and settings are kept. With more than one TV connected, name the one to use: `./install.sh 192.168.1.42:5555`.

## Step 4: Try it before making it the default

Open **Simple Launcher** from Google TV's Apps list, or run:

```sh
adb shell am start -n com.simple.tvlauncher/.MainActivity
```

Check that your apps show up, OK opens them, and favorites, hiding and the clock all work. **Don't go on to step 5 until you're happy with it.**

## Step 5: Make it the default home app

Google TV has no on-screen menu for choosing a home app, so use adb:

```sh
adb shell cmd role add-role-holder android.app.role.HOME com.simple.tvlauncher
```

Check that it was accepted:

```sh
adb shell dumpsys role | grep -A1 'android.app.role.HOME'
```

You should see `holders=com.simple.tvlauncher`.

(Don't be surprised if `adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME` still prints `com.google.android.apps.tv.launcherx/.home.HomeActivity`. On Google TV, the stock launcher gives itself a higher priority than other home apps, so it can win even after this step. That's the Home button problem described below.)

Now press **Home** on the remote. If Simple Launcher opens, you're done. If the stock Google TV screen still opens, read the next section.

## Optional: wallpaper pictures and videos

You can copy as many pictures and videos as you like to the TV, then choose between them on the TV. Landscape 1920×1080 JPG, PNG or WebP pictures work best. Videos can be MP4, MKV, WebM or MOV; a 1080p MP4 (H.264) is the safest choice.

1. Open Simple Launcher at least once, so the TV creates the folder for it.
2. Copy pictures from your Mac. You can list several at once; keep the folder path at the end, including the final `/`:
   ```sh
   adb push ~/Pictures/beach.jpg ~/Pictures/forest.jpg /sdcard/Android/data/com.simple.tvlauncher/files/
   ```
   Or copy every picture in a folder on your Mac (the `/.` at the end of the first path means "the folder's contents"):
   ```sh
   adb push ~/Pictures/TV\ Wallpapers/. /sdcard/Android/data/com.simple.tvlauncher/files/
   ```
3. On the TV, open **Launcher settings**. Under **Wallpaper picture or video**, select the one you want and press OK. The selected one shows a ✓, and videos show a ▶.

To go back to a plain color, pick a color under **Background color**.

See which pictures are on the TV:

```sh
adb shell ls /sdcard/Android/data/com.simple.tvlauncher/files/
```

Remove one (use the exact name from the list above):

```sh
adb shell rm /sdcard/Android/data/com.simple.tvlauncher/files/forest.jpg
```

If you remove the picture or video you're currently using, the launcher switches to the first remaining one.

**Videos** fill the screen (the edges are cropped if the shape doesn't match), loop, and play only while the launcher is on screen. Opening an app stops the video, and it carries on from the same spot when you come back. Video sound has its own mute button and volume slider under **Launcher settings → Video sound**. Background music stays off while a video plays with sound; mute the video to hear your music instead.

### Getting the best video quality

What I measured on the TCL (Realtek RTD6748, 4K panel, interface drawn at 1080p, 18 GB free):

- **The launcher sends videos straight to the TV's video layer**, like Netflix does, so a 4K video shows at the panel's full 4K even though the launcher's own interface is 1080p.
- **The TV decodes H.264, HEVC (H.265), VP9 and AV1 in hardware, up to 4K.** HEVC gives the same picture as H.264 at roughly half the size.
- **Memory (RAM) doesn't depend on the loop's length.** The video plays from storage a little at a time; only its resolution changes how much memory the decoder uses, and the launcher frees the decoder whenever you open an app.
- **Storage grows with length:**

| | Data rate | Per minute | 2-minute loop |
|---|---|---|---|
| A typical 720p H.264 download (like the first fireplace) | about 2 Mbps | about 16 MB | about 32 MB |
| 1080p HEVC, good quality | about 6–10 Mbps | 45–75 MB | 90–150 MB |
| 4K HEVC, good quality | about 15–30 Mbps | 110–225 MB | 225–450 MB |

**The best choice for this TV: a 4K HEVC loop about 2 minutes long, roughly 300 MB.** Beyond a minute or two nobody notices a fire (or rain, or waves) repeating, so a longer loop only uses more storage. The loop point matters more than the length: a hard cut from the last frame back to the first is the most visible flaw, so the loop should crossfade.

**The source matters most.** A 720p video at 2 Mbps will look soft and blocky however it's converted; re-encoding can't add detail that isn't there. Start from the best original you can find (4K, or at least 1080p), then let `videowall.sh` do the rest:

```sh
./videowall.sh "media/My Fireplace 4K.mp4" --push
```

It makes a 2-minute seamless loop in `media/` (which git ignores) using the settings above, keeps the original resolution up to 4K (it never upscales), adds a thumbnail, and with `--push` copies it to the TV. Options: `--length 90` for a 90-second loop, `--start 30` to start 30 seconds into the original. A 2-minute 4K loop takes about 8 minutes to make on this Mac.

## Optional: hero select logo and background music

Use your own files for these. They're copied onto the TV only, so they never end up in this project or on GitHub. The launcher still never uses the internet.

**Logo** (top-left of the hero select screen). A PNG with a transparent background looks best:

```sh
adb push ~/Pictures/my-logo.png /sdcard/Android/data/com.simple.tvlauncher/files/logo/
```

**Music** (plays softly while the launcher is on screen). MP3, M4A, AAC, OGG, FLAC and WAV all work. When the TV turns on, files play once each in A–Z order, and the last one loops. So a short intro followed by a long theme works well; name them so they sort in the right order (for example `1-intro.mp3` and `2-theme.mp3`):

```sh
adb push ~/Music/menu-theme.mp3 /sdcard/Android/data/com.simple.tvlauncher/files/music/
```

Press Home on the TV afterwards so the launcher picks up the new files.

How the music behaves:
- It plays only on the **Hero select** home screen. Classic and Scenic are silent (apart from a video wallpaper's own sound).
- It fades in, and stops as soon as you open an app or the screensaver starts.
- The intro plays only the first time the home screen shows after the TV turns on (pressing the remote's power button off and on again counts). Coming back to the home screen any other time skips the intro: after 30 seconds, the last file fades in and loops.
- Moving around inside the launcher (Launcher settings, pressing Home) doesn't interrupt it, or restart the 30-second wait.
- It won't start if something else is already playing (for example, Spotify in the background).
- Change the loudness under **Launcher settings → Background music**: select the slider and press Left or Right (moving it also unmutes). The button next to it mutes and unmutes.

To remove files, list them and delete by name:

```sh
adb shell ls /sdcard/Android/data/com.simple.tvlauncher/files/music/
adb shell rm /sdcard/Android/data/com.simple.tvlauncher/files/music/menu-theme.mp3
```

---

## Back up, copy or share your setup

`tvstate.sh` saves a TV's whole setup to one file on your Mac, and loads a saved setup onto any TV, including a Fire TV. A setup holds your favorites, app order, hidden apps, Hero roles, home screen style, colors, clock, music volume and video sound, plus your wallpaper pictures and videos, logo and music files. It doesn't change anything about the Home button.

```sh
./tvstate.sh save living-room                 # saves profiles/living-room.zip
./tvstate.sh load living-room                 # replaces the TV's setup with it
./tvstate.sh list                             # shows your saved setups
```

- **More than one TV connected?** Add the TV's address at the end, for example `./tvstate.sh load living-room 192.168.1.42:5555`.
- **Sharing:** send someone the `.zip` from the `profiles` folder. They put it in their own `profiles` folder (or give its full path) and run `./tvstate.sh load`. Your wallpaper pictures and videos are included (videos can make the file large), so add `--no-media` when saving to leave pictures and music out. `--no-media` on `load` keeps the TV's own pictures and music.
- **Apps that aren't installed** on the other TV stay in the setup and appear in their place if you install them later. Apps that have a different name on Fire TV and Google TV, such as YouTube, are matched up.
- **When it loads:** right away if Simple Launcher is on screen. Otherwise the next time it opens, so it never interrupts someone watching.
- **Changed your mind?** On the TV, open **Launcher settings → Saved setups** and choose **Undo last change**. The TV keeps the last 10 setups it replaced there. They survive app updates but not uninstalling the app, so keep the `.zip` on your Mac as your real backup.

The `profiles` folder is never uploaded to GitHub.

---

## The Home button problem (Google TV)

On many Google TV devices, the Home button always opens the stock Google TV home screen, even after step 5. You have two options. **Do these only after Simple Launcher is installed and working (steps 3 to 5).**

### Option B (try this first): the Home button helper

This option disables nothing. The app includes an optional accessibility service. When it's on, it catches the Home press and opens Simple Launcher instead. If the TV handles Home before the helper can catch it, the helper switches back to Simple Launcher the moment the stock screen appears. You might see the stock screen flash briefly. The helper doesn't read the screen and collects nothing.

Turn it on:

1. On the TV, go to **Launcher settings → Open accessibility settings**.
2. Find **Simple Launcher Home button** and turn it on.

If it isn't listed (some Google TV versions hide downloaded services), use adb. First see what's already turned on:

```sh
adb shell settings get secure enabled_accessibility_services
```

- If it printed `null` or nothing at all, turn on just this one:
  ```sh
  adb shell settings put secure enabled_accessibility_services com.simple.tvlauncher/com.simple.tvlauncher.HomeButtonService
  adb shell settings put secure accessibility_enabled 1
  ```
- If it printed something (for example `com.other/.Service`), keep it and add ours after a colon:
  ```sh
  adb shell settings put secure enabled_accessibility_services 'com.other/.Service:com.simple.tvlauncher/com.simple.tvlauncher.HomeButtonService'
  adb shell settings put secure accessibility_enabled 1
  ```

**After every update of the app,** Android switches the helper off, even though it still shows in the list. Go back to **Launcher settings → Open accessibility settings** and turn **Simple Launcher Home button** off and on again. (`./install.sh` reminds you.)

To stop it redirecting without touching accessibility settings, go to **Launcher settings → Redirect Home button**.

Turn it off: switch it off in Accessibility settings, or put back exactly what the `get` command printed before. If that was `null`, run:
`adb shell settings delete secure enabled_accessibility_services`

### Option A: disable the stock launcher

This is the cleanest and fastest option. Disabling an app with `pm disable-user` doesn't delete it, and one command turns it back on.

First, see which home screens your TV has:

```sh
adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

Packages that are safe to disable (only these):

| Package | What it is | Notes |
|---|---|---|
| `com.google.android.apps.tv.launcherx` | The Google TV home screen | Disabling it also removes Google TV's "For you" home, profiles and, on some models, the quick-settings panel that the remote's gear button opens. Use the **TV settings** button in Simple Launcher instead. |
| `com.google.android.tungsten.setupwraith` | Google TV setup app | Disable it **only if**, after disabling launcherx, Home shows a setup or black screen instead of Simple Launcher. |

**Do NOT disable anything else.** That includes Settings (`com.android.tv.settings`), Google Play services (`com.google.android.gms`), anything starting with `com.tcl.`, and anything starting with `com.android.`.

Disable the stock launcher:

```sh
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
```

Press Home. If you get a setup or black screen instead of Simple Launcher, also run:

```sh
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
```

**Turn them back on** (this always works over adb, even if the TV screen looks broken):

```sh
adb shell pm enable com.google.android.apps.tv.launcherx
adb shell pm enable com.google.android.tungsten.setupwraith
```

To see everything that's currently disabled: `adb shell pm list packages -d`

Notes:
- A TV system update may turn the stock launcher back on. Just run the disable command again.
- As a last resort, a factory reset restores everything, but you should never need it.

---

## Undo everything (back to the stock launcher)

Do these in order:

```sh
# 1. Turn the stock launcher back on (only needed if you used Option A)
adb shell pm enable com.google.android.apps.tv.launcherx
adb shell pm enable com.google.android.tungsten.setupwraith

# 2. Turn off the Home button helper (only needed if you turned it on with adb and nothing else was on)
adb shell settings delete secure enabled_accessibility_services

# 3. Remove Simple Launcher. The TV goes back to Google TV as the home screen.
#    This deletes your setup from the TV, so save it first if you might want it back: ./tvstate.sh save backup
adb uninstall com.simple.tvlauncher
```

Then, if you want, turn off **Developer options** on the TV (Settings → System → Developer options → Off).

---

## Troubleshooting

- **`java -version` still says 1.8:** open a new Terminal window, or run `source ~/.zshrc`.
- **"SDK location not found" while building:** run
  `echo "sdk.dir=$ANDROID_HOME" > local.properties` in the project folder, then build again.
- **`adb: device unauthorized`:** look at the TV and accept the prompt. Then run `adb disconnect` and `adb connect ...` again.
- **`adb connect` times out:** make sure the Mac and the TV are on the same Wi-Fi, and that debugging is still on (TVs sometimes turn it off after a reboot).
- **The Inputs list is empty or switching does nothing:** your TV's input switching may not go through Android's standard TV input system. Use the remote's Input button, or open TCL's input app from the app grid.
- **Check that the app requests no internet access:**
  `adb shell dumpsys package com.simple.tvlauncher | grep -i "android.permission"`
  You won't see `android.permission.INTERNET` in the list.

## Project layout

```
app/src/main/
  AndroidManifest.xml          HOME + DEFAULT intent filter, banner, <queries>, no INTERNET
  java/com/simple/tvlauncher/
    MainActivity.kt            Entry point; Home presses while open reset to the top
    LauncherViewModel.kt       Combines installed apps + saved settings into screen state
    HomeButtonService.kt       Optional accessibility Home-button helper
    data/AppRepository.kt      Lists TV + regular apps, removes duplicates, refreshes on install/uninstall
    data/DeviceScreens.kt      Opens the TV's own settings, home and inputs screens, per platform
    data/SettingsStore.kt      Favorites, hidden apps, roles, colors, clock format (DataStore)
    data/StateFile.kt          A setup as JSON, for backing up and copying between TVs
    data/StateTransfer.kt      Writes the current setup, loads pushed ones, keeps snapshots for undo
    data/AppAliases.kt         Apps with different package names on Fire TV and Google TV
    data/Role.kt               Hero select roles and their defaults
    data/BackgroundMusic.kt    Plays the music you push with adb
    data/TvInputs.kt           HDMI and other inputs via TvInputManager
    data/UserFiles.kt          Lists and loads the wallpaper, logo and music you push with adb
    ui/VideoWallpaper.kt       Looping video wallpaper on the TV's video layer
    ui/                        Compose for TV screens: Classic/Scenic and Hero select home, video wallpaper, dialogs, settings, clock
install.sh                     Builds and installs on the connected TV
tvstate.sh                     Saves and loads setups (see "Back up, copy or share your setup")
videowall.sh                   Turns a video into a seamless wallpaper loop (see "Getting the best video quality")
```

The project also opens directly in Android Studio (File → Open → this folder).
