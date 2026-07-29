# Implementation Plan - Fix Speedometer and App Locking Issues

The goal is to fix the `NullPointerException` when registering VHAL listeners (which prevents the speedometer from working) and ensure the app is correctly recognized as "distraction optimized" by the system to prevent the locking overlay.

## User Review Required

> [!IMPORTANT]
> The logs show a `NullPointerException` in `CarPropertyManager` and a permission error for `PERF_VEHICLE_SPEED`. We will unify the Car Service connection logic and ensure permissions are correctly handled.

> [!WARNING]
> We will be consolidating the `Car` object creation. Currently, multiple instances are being created, which causes service connection leaks and potential conflicts.

## Proposed Changes

### [Component Name] Car Service & VHAL

#### [MODIFY] [MainActivity.kt](file:///D:/Hackathon/cockpit-ui/app/src/main/java/com/wheelchair/cockpit/MainActivity.kt)
- Remove the redundant/deprecated `Car.createCar(this)` call.
- Modify `CarPropertyHelper` to return the `Car` instance or provide access to the `CarUxRestrictionsManager`.
- Initialize `CarUxRestrictionsManager` inside the `CarPropertyHelper` connection callback to ensure the service is ready.
- Add an explicit `Handler` to `Car.createCar` to avoid internal null pointers.

#### [MODIFY] [CarPropertyHelper.kt](file:///D:/Hackathon/cockpit-ui/app/src/main/java/com/wheelchair/cockpit/vhal/CarPropertyHelper.kt)
- Update `connectCarService` to use a main thread `Handler`.
- Provide a way to register for UX restrictions through the unified `Car` instance.
- Update `registerVhalListeners` to be more robust.

#### [MODIFY] [AndroidManifest.xml](file:///D:/Hackathon/cockpit-ui/app/src/main/AndroidManifest.xml)
- Add `distractionOptimized="true"` to the `<application>` tag as well to ensure full system recognition.
- Ensure all required car permissions are present (already looks okay, but will double-check).

## Verification Plan

### Automated Tests
- N/A

### Manual Verification
- Deploy the app to the emulator.
- Check logs for "Car Service connected successfully" and "Registered PERF_VEHICLE_SPEED listener: true".
- Use "Extended Controls" to change speed and verify the UI speedometer updates.
- Verify the "Close app" overlay does not appear when driving.
