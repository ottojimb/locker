# Locker

**A custom sleep screen for the Bigme B7: your last page, with your own image on top.**

I wanted the sleep screen on my Bigme B7 to show what I was reading, with a little image of my choice on top, instead of the default calendar. The stock options are a clock, a calendar, a picture or "transparent", and none of them do that. So I built a small app that does.

When you press the power button, Locker takes a screenshot of whatever is on screen, puts your PNG on top, and leaves that on the e-ink panel while the device sleeps. You can use a single image or a folder, and with a folder it picks a random image every time you lock.

> Unofficial, hobby project. Not affiliated with Bigme. Tested on a Bigme B7 (Android 14, firmware `Bigme_V1.020260918`). It may work on other Bigme / Xrz devices that use the same sleep screen app (`com.xrz.standby`), but I have not tried them.

## Features

- Shows the page you were on (KOReader, the Bigme reader, any app) as the sleep screen
- Puts your PNG on top. Transparent parts let the page show through
- Folder mode: a random image each time you lock, never the same one twice in a row
- Screenshots stay in memory only and are never saved to disk
- Tiny, with no dependencies, ads, tracking or internet permission

## Install

### Download (easiest)

1. Grab the latest `locker-*.apk` from the [Releases page](https://github.com/ottojimb/locker/releases/latest).
2. Copy it to the B7 and open it with a file manager, or install it over USB:

   ```sh
   adb install -r locker-*.apk
   ```

### Build it yourself

Requirements: Android SDK and JDK 17+.

```sh
git clone https://github.com/ottojimb/locker.git
cd locker
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Setup (do this once)

1. **Turn on the service:** Settings → Accessibility → **Locker screen** → On.
   Android will warn you that the app can see your screen. That is how it takes the screenshot, and the screenshot never leaves the device.
2. **Set Bigme's sleep style to "Transparent":** open Bigme's sleep screen settings and pick **Transparent**.
   This step matters. The clock, calendar and picture styles draw an opaque window above every app, which covers Locker's image.
3. **Choose your image:** open the **Locker** app and:
   - tap **Choose overlay PNG** for a single image, or
   - tap **Choose folder (random each lock)** and pick a folder with PNG, JPG or WebP files.
4. **Lock the device** with the power button. Enjoy!

## Making a good overlay

- The screen is **1264 × 1680 px** (portrait). Use that size. Other sizes are stretched to fit.
- Use a **PNG with transparency**. Transparent areas show your page, and opaque areas cover it.
- Semi-transparent white boxes look great on e-ink for text like "Do not disturb, I'm reading".
- Bigme's Transparent style draws a small sleeping-moon icon (and notification icons) on top. Locker can't remove those.

## Tips and known quirks

- **Don't swipe Locker away in Bigme's recents.** Bigme force-stops the app, and Android then turns off the accessibility service, so you would have to enable it again. Lock the app in recents if your launcher allows it.
- If you press power right after unlocking, folder mode may show the previous image again, because the next one is still loading.
- While the screen is on, Locker refreshes its screenshot after changes and every few seconds. It refreshes it again when the B7 drops into its light standby after about 5 s without touching anything.
- There is also a **"Lock with image"** Quick Settings tile if you prefer not to use the power button.

## How it works

Bigme's sleep screen is a system dialog (`com.xrz.standby`) that the firmware shows when you press power. A normal app can't replace it without root. With the **Transparent** style, though, that dialog is see-through, so:

1. An accessibility service keeps a recent screenshot (`takeScreenshot()`, which needs no root and no prompt on each lock).
2. When Bigme's sleep dialog appears, Locker takes a fresh screenshot, composes it with your PNG, and shows the result in an accessibility overlay right under Bigme's transparent dialog.
3. The e-ink panel freezes with that frame. When the screen turns on again, the overlay is removed.

Debug logs:

```sh
adb logcat -s LockerService OverlayStore PowerStandbyManager
```

## Releasing (for maintainers)

Pushing a tag like `v0.2.0` makes GitHub Actions build the APK and attach it to a new release:

```sh
git tag v0.2.0
git push origin v0.2.0
```

The APK is signed with the keystore stored in the repository secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`), so each new release installs over the previous one. Without those secrets, the workflow publishes a debug-signed APK instead.

## Contributing

Got another Bigme (or another e-reader) and want to try it? Issues and PRs are very welcome. A report like "works on my B6 / B751c" or "doesn't work, here's the logcat" helps a lot.

Happy reading!
