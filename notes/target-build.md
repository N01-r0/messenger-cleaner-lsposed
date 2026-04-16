Target package: `com.facebook.orca`

Observed on device:
- Version name: `556.0.0.59.68`
- Version code: `341213534`
- APK source: pulled from the attached device on `2026-04-16`

Current hook anchors:
- Meta AI kill switch strings:
  - `SearchAiagentImplementationsKillSwitch`
  - `AiAgentPluginsKillSwitch`
- Inbox ad item class:
  - `com.facebook.messaging.business.inboxads.common.InboxAdsItem`
- Current inbox list processor fallback:
  - `X.2Mq#Czo`

Rationale:
- Keep the hook surface narrow.
- Disable AI feature gates through config checks rather than patching core chat logic.
- Strip ad items after list assembly so thread/search/message flows are left alone.

Forward-compat notes:
- Meta AI removal is already signature-based through kill-switch tokens and mobile-config flag prefixes.
- Inbox-ad removal now prefers APK-signature discovery over the previous single-class fallback.
- Future-version checking should use `scripts/check-messenger-compat.py` against a pulled `base.apk`.
