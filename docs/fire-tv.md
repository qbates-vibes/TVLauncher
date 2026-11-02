# Simple Launcher on Fire TV

Tested on an Insignia NS-55F501NA26 running Fire OS 8.1.8.5 (Android 11). Other builds may behave differently, and Amazon's updates can change this. Vega OS devices can't install the app at all.

Build it as described in the [README](../README.md). The steps below replace README steps 3 to 5.

## Install

1. On the TV, go to **Settings → My Fire TV → Developer Options** and turn on **ADB debugging**. If Developer Options is missing, open **About** and select the device name 7 times.
2. Find the TV's IP address under **About → Network**.
3. Connect, and choose **Always allow from this computer** on the TV:
   ```sh
   adb connect 192.168.1.42:5555
   ```
4. Install:
   ```sh
   ./install.sh 192.168.1.42:5555
   ```

**ADB turns itself off after a reboot.** The setting still looks on, but the TV stops accepting connections until you turn **ADB debugging** off and on again.

## Make the Home button open it

Fire TV has no setting for this. The `cmd role` command in the README is accepted but does nothing, because Home opens Amazon's launcher directly. Don't disable Amazon's launcher.

Turn on the Home button helper:

```sh
adb shell settings put secure enabled_accessibility_services com.simple.tvlauncher/com.simple.tvlauncher.HomeButtonService
adb shell settings put secure accessibility_enabled 1
```

The helper can't catch the Home press on this TV. It waits for Amazon's home screen to appear and then switches to Simple Launcher, so you'll see a quick fade, about half a second. It also returns to Simple Launcher after a reboot.

## Using it

| Button | What it does |
|---|---|
| **TV settings** | Opens Amazon's Settings. |
| **Amazon home** | Opens Amazon's home screen. Home stays on Amazon until you open Simple Launcher again. |
| **Inputs** | Opens Amazon's home screen to choose an input. Once you've opened an input or app, Home comes back to Simple Launcher. |

Only Amazon's own apps can switch inputs, so Simple Launcher can't do it directly.

**Getting back from Amazon's home screen:** go to **Settings → Applications**, find Simple Launcher and open it. If you pressed **Inputs** but didn't choose anything, Home stays on Amazon, and this gets you back too. A reboot also returns you to Simple Launcher.

## Undo it

**From the remote:**
- To stop the redirect, go to **Launcher settings → Redirect Home button** and turn it off.
- To remove the app, go to **Settings → Applications**, find Simple Launcher and uninstall it. This also removes the helper.

**From a computer:**

```sh
adb shell settings delete secure enabled_accessibility_services
adb shell settings put secure accessibility_enabled 0
adb uninstall com.simple.tvlauncher
```

If the buttons above ever misbehave, you can also say "Alexa, open settings" into the remote.
