Messenger LSPosed workspace for suppressing Messenger inbox ads and Meta AI entry points.

Scope:
- This project is independent from `Gesfix`.
- `Gesfix` is reference-only and is not part of this build.
- The current hook logic targets Messenger `556.0.0.59.68` (`com.facebook.orca`).

Layout:
- `app/`: LSPosed module source
- `analysis/`: pulled APKs and decoded output
- `reference/`: external reference code snapshots
- `scripts/`: repeatable pull/decode/build helpers
- `notes/`: version notes and signature observations

Quick start:
```bash
cd /home/or10n/Documents/MessengerLsposed
./scripts/pull-messenger-apks.sh
./scripts/decode-messenger-base.sh
./scripts/precheck-messenger.sh analysis/apk/base.apk
./scripts/build-module.sh
```

Current strategy:
- Disable Meta AI through Messenger mobile-config kill switches and AI plugin gates.
- Filter inbox lists to strip `InboxAdsItem` instances after Messenger assembles them.
- Avoid patching messaging/search core flows.

Compatibility strategy:
- Prefer string/signature discovery over hardcoded version checks.
- Log the live Messenger `versionCode` and resolved hook counts at startup.
- Use `scripts/precheck-messenger.sh <base.apk>` or `scripts/precheck-messenger.sh --device` before trusting a new Messenger update.
- Keep brittle class-name fallbacks only as a last resort.

Precheck output:
- `reports/latest.md`: human-readable summary for quick review in the IDE
- `reports/latest.html`: browser-friendly summary
- `reports/latest.json`: machine-readable output for automation
