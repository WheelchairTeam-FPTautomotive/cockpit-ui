# Implementation Plan - Driver Distraction Optimization (CarUxRestrictions)

Optimizing the Wheelchair Copilot app for Android Automotive OS Driver Distraction Guidelines. The goal is to allow voice interactions while driving but restrict manual UI interactions (typing, settings, complex buttons).

## User Review Required

> [!IMPORTANT]
> The current `AndroidManifest.xml` already contains `distractionOptimized=true`. If the app is still blocked with a "Close app" overlay, it might be due to a system policy or how the emulator is configured. However, we will proceed with UI-level restrictions to ensure compliance once the overlay issue is resolved (or as part of the resolution).

> [!WARNING]
> We will be hiding or disabling several UI elements during driving. This includes the manual text input, settings button, and quick action cards. Ensure this aligns with the desired UX.

## Proposed Changes

### [Component Name] UI Components

#### [MODIFY] [MainActivity.kt](file:///D:/Hackathon/cockpit-ui/app/src/main/java/com/wheelchair/cockpit/MainActivity.kt)
- Pass `isDrivingRestricted` to `AutomotiveTopBar`, `QuickActionCard`s, and `AutomotiveBottomDock`.

#### [MODIFY] [ManualInputBar.kt](file:///D:/Hackathon/cockpit-ui/app/src/main/java/com/wheelchair/cockpit/ui/components/ManualInputBar.kt)
- When `isDrivingRestricted` is true, hide the `TextField` and show a "Voice Mode Only" indicator.
- Disable the "Send" button completely.

#### [MODIFY] [QuickActionCard.kt](file:///D:/Hackathon/cockpit-ui/app/src/main/java/com/wheelchair/cockpit/ui/components/QuickActionCard.kt)
- Add `enabled: Boolean = true` parameter.
- When `enabled` is false, disable the `clickable` modifier and lower the opacity of the card to indicate it's inactive.

#### [MODIFY] [AutomotiveTopBar.kt](file:///D:/Hackathon/cockpit-ui/app/src/main/java/com/wheelchair/cockpit/ui/components/AutomotiveTopBar.kt)
- Add `isDrivingRestricted: Boolean = false` parameter.
- Disable/Hide the Settings icon button when restricted.

#### [MODIFY] [AutomotiveBottomDock.kt](file:///D:/Hackathon/cockpit-ui/app/src/main/java/com/wheelchair/cockpit/ui/components/AutomotiveBottomDock.kt)
- Add `isDrivingRestricted: Boolean = false` parameter.
- Disable/Hide the Settings navigation item when restricted.

## Verification Plan

### Automated Tests
- N/A (UI behavior based on system state is best verified manually in emulator).

### Manual Verification
- Use the Android Automotive Emulator "Extended Controls".
- Set **Gear = D** and **Car Speed > 0**.
- Verify:
  - The manual input bar changes to "Voice Mode Only".
  - "Quick Action" cards (Maps, Music) are dimmed and non-clickable.
  - The "Settings" icon in the top bar is hidden or disabled.
  - The "Settings" tab in the bottom dock is hidden or disabled.
  - The Voice Assistant ("Hey Car" and mic button) still works.
  - The overlay "Close app" should no longer appear if the manifest is correctly picked up.
