# Implementation Plan - Fix Deprecation Warnings in CarPropertyHelper

Fix the deprecation warnings related to `CarPropertyManager.registerCallback` by migrating to the recommended `subscribePropertyEvents` API introduced in Android 13 (API 33).

## Proposed Changes

### [Component] Car Property Integration

#### [MODIFY] [CarPropertyHelper.kt](file:///D:/Hackathon/cockpit-ui/app/src/main/java/com/wheelchair/cockpit/CarPropertyHelper.kt)

Replace deprecated `registerCallback` calls with `subscribePropertyEvents`. Note that the parameter order is different: `(propId, rate, callback)` instead of `(callback, propId, rate)`.

```diff
-            manager.registerCallback(
-                vhalCallback,
-                VehiclePropertyIds.PERF_VEHICLE_SPEED,
-                CarPropertyManager.SENSOR_RATE_NORMAL
-            )
+            manager.subscribePropertyEvents(
+                VehiclePropertyIds.PERF_VEHICLE_SPEED,
+                CarPropertyManager.SENSOR_RATE_NORMAL,
+                vhalCallback
+            )

-            manager.registerCallback(
-                vhalCallback,
-                VehiclePropertyIds.HVAC_AC_ON,
-                CarPropertyManager.SENSOR_RATE_ONCHANGE
-            )
+            manager.subscribePropertyEvents(
+                VehiclePropertyIds.HVAC_AC_ON,
+                CarPropertyManager.SENSOR_RATE_ONCHANGE,
+                vhalCallback
+            )
```

## Verification Plan

### Automated Tests
- Run `./gradlew :app:compileDebugKotlin` to verify that the warnings are resolved and the code compiles successfully.
- If possible, deploy to an emulator to verify that property updates are still received (manual verification).

### Manual Verification
- Confirm that the `vehicleSpeed` and `isHvacOn` states in `MainActivity` still update correctly when simulating VHAL changes (requires emulator).
