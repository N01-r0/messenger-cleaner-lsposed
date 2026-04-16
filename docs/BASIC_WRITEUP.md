# Messenger Cleaner

Basic write-up for the Messenger LSPosed module project.

## Overview

Messenger Cleaner is an LSPosed/Xposed-style runtime module for `com.facebook.orca` that removes or suppresses unwanted Messenger surfaces while keeping core messaging intact.

Current focus areas:
- Meta AI entry points
- Inbox ads
- unwanted Messenger menu surfaces such as `Chat with AIs`, `Create an AI`, `Facebook Reels`, `Facebook Events`, and `Chat moments`

The module is designed to work at runtime rather than by permanently modifying the installed Messenger APK.

## Goals

- Keep direct messaging, thread navigation, and normal search working
- suppress Meta AI-related surfaces where possible
- suppress inbox-ad surfaces without interfering with normal chat lists
- make compatibility checking possible before trusting a new Messenger update

## Approach

The project uses a few different strategies rather than relying on one fragile hook:

1. Meta AI feature suppression

Meta AI removal is handled primarily through kill-switch and mobile-config hook points. This is more stable than trying to patch every individual AI UI element one by one.

2. Inbox ad filtering

Inbox ads are filtered after Messenger assembles the list data. This keeps the hook narrow and avoids changing normal thread or messaging logic.

3. UI surface cleanup

Some visible menu surfaces are removed or hidden at the UI level where a deeper feature gate is not exposed cleanly enough.

4. Version precheck

Before trusting a new Messenger APK, the project can run a structural compatibility check against that build and produce a visible report for review.

## Before / After

Add your own screenshots later in `docs/assets/` and replace these placeholders.

### Before Patch

- Add screenshot here
- Suggested filename: `docs/assets/before-patch.png`

### After Patch

- Add screenshot here
- Suggested filename: `docs/assets/after-patch.png`

## Compatibility Precheck

The project includes a precheck workflow so a Messenger version can be reviewed before relying on it.

Run against the device:

```bash
cd /home/or10n/Documents/MessengerLsposed
./scripts/precheck-messenger.sh --device
```

Run against a specific APK:

```bash
cd /home/or10n/Documents/MessengerLsposed
./scripts/precheck-messenger.sh /path/to/base.apk
```

Generated reports:
- [latest.md](/home/or10n/Documents/MessengerLsposed/reports/latest.md)
- [latest.html](/home/or10n/Documents/MessengerLsposed/reports/latest.html)
- [latest.json](/home/or10n/Documents/MessengerLsposed/reports/latest.json)

The precheck currently looks for:
- Meta AI kill-switch signatures
- inbox-ad processor candidates
- known Messenger UI/menu surface strings
- APK package and version metadata

## Current Project Layout

- `app/`: LSPosed module source
- `analysis/`: pulled APKs and decoded output
- `docs/`: write-ups and publish-facing notes
- `notes/`: build-specific observations
- `reference/`: external reference code
- `reports/`: generated compatibility precheck output
- `scripts/`: repeatable tooling for pull, decode, precheck, and build

## Current Status

- Messenger APK pull/decode workflow in place
- LSPosed module scaffolded and buildable
- device-side precheck report generation working
- compatibility logic improved to reduce dependence on a single Messenger version

## Install / Distribution

Normal users should install the prebuilt LSPosed module APK from the repository's `Releases` page.

Building from source is only necessary if you want to modify the hooks or test local changes yourself.
