# messenger-cleaner-lsposed

LSPosed module for removing Meta AI surfaces, inbox ads, and other unwanted Messenger promo surfaces while keeping normal chats, thread navigation, and people search usable.

The current hook set was developed against Messenger `556.0.0.59.68` (`com.facebook.orca`).

## What it removes

- Meta AI entry points and related feature gates
- inbox ads
- menu surfaces such as `Chat with AIs`, `Create an AI`, `Facebook Reels`, `Facebook Events`, and `Chat moments`

## Install

End users should install the prebuilt APK from the GitHub Releases page instead of building from source.

1. Download the latest module APK from `Releases`.
2. Install the APK on the phone.
3. Open `LSPosed`, enable `Messenger Cleaner`, and scope it to `com.facebook.orca`.
4. Force-stop Messenger or reboot the phone.
5. Open `Chats`, `Notifications`, and the main menu once to do a quick smoke test.

## Precheck a Messenger build

Most users can skip this section.

This is only needed if you want to test compatibility from source before trusting a Messenger update.

If you do not already have a local checkout, start with:

```bash
git clone https://github.com/<your-account>/messenger-cleaner-lsposed.git
cd messenger-cleaner-lsposed
```

Use the device check only if all of these are true:

- your phone is plugged into the computer or reachable over ADB
- `USB debugging` is enabled
- `adb` is installed on the computer
- Messenger is installed on that phone

Check the Messenger currently installed on your phone:

```bash
./scripts/precheck-messenger.sh --device
```

Use the APK check if you already have a Messenger APK file and do not want to use a plugged-in phone:

Check a specific Messenger APK file:

```bash
./scripts/precheck-messenger.sh /path/to/base.apk
```

The script writes a readable report to:

- `reports/latest.md`
- `reports/latest.html`
- `reports/latest.json`

### How to read the result

- `low`: core hook signatures were found; the build looks structurally compatible, but you should still do a short in-app smoke test.
- `medium`: core hooks may still work, but UI/menu surfaces probably moved; only update if you can verify immediately.
- `high`: required hook signatures were not found; do not trust that Messenger build until the module is reviewed and updated.

## Build from source

This is only needed if you are developing or changing the module yourself.

```bash
git clone https://github.com/<your-account>/messenger-cleaner-lsposed.git
cd messenger-cleaner-lsposed
./scripts/build-module.sh
```

This requires a working Android build environment on your computer.

The built APK is written under `app/build/outputs/apk/`.
