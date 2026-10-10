# The ZUI CTS/GTS test flag and the screenshot fallback

Reverse-engineering note for `hook/modules/systemui/statusbar/NativeNotificationIcon.kt`.
Verified against `com.android.systemui` 17.5.24 (ZUXOS 2.0.10.171, Android 16, TB711FU),
`framework.jar` and `services.jar` from the same build, plus the four-round on-device log
in the 2026-10-09 feedback bundle.

## 1. The flag

`com.android.systemui.util.XSystemUtil.isCTSGTSTest()` is a SystemUI-only helper:

```java
public static boolean isCTSGTSTest() {
    return "true".equals(SystemProperties.get("persist.sys.lenovo.is_test_mode"));
}
```

It is the ROM's "this device is running a CTS/GTS certification pass" switch, and roughly
30 SystemUI call sites flip to stock-AOSP behaviour when it returns true. The predicate
exists **only** in SystemUI: neither `framework.jar` nor `services.jar` contains
`isCTSGTSTest`, and the framework reads `persist.sys.lenovo.is_test_mode` directly where it
cares (`ActivityThread`, `Display`, `ViewRootImpl`, `WallpaperManager`,
`ResourcesImpl`, ...).

Two consequences matter for ZTool:

* Hooking `XSystemUtil.isCTSGTSTest()` changes SystemUI behaviour only. It can never make
  `android.app.Notification$Builder` take a different template, because the framework never
  calls it.
* The framework-side CTS predicate the module's hook 6 looks for does not exist on this
  build. `framework.jar` contains neither `isCtsGtsTest` nor `isCTSandGTS`; `Notification`
  picks the ZUI action layout/colour from
  `com.lgsi.config.LgsiFeatures.NOTIFICATION_CUSTOMIZE`, not from the test flag.

## 2. What reads the flag

| Consumer | Effect of `true` |
|---|---|
| `DaggerReferenceGlobalRootComponent.interactiveScreenshotHandlerFactory()` | injects the AOSP `InteractiveScreenshotHandler.Factory` instead of the ZUI one |
| `TakeScreenshotExecutorImpl` / `ScreenshotView` (x3) | skips `handleLenovoScreenRequest`, uses the AOSP preview/edit path |
| `ThemeOverlayController`, `ZuiThemeOverlayController` | disables the ZUI monet/theme overlay paths |
| `DarkIconDispatcherImpl`, `NotificationIconContainerViewBinder$bind$2` | tints status-bar notification icons instead of keeping their own colours |
| `NotificationShelf.updateResources$5` | `mShelfIcons.mSetIconNoColor = false` |
| `NotificationEntry`, `NotificationInfo`, `NotificationMenuRow`, `NotificationSwipeHelper`, `ExpandableNotificationRow`, `StackScrollAlgorithm` | AOSP dismissal/gesture/stacking behaviour |
| `BatteryMeterView`, `KeyguardDisplayManager`, `PcModeManager`, `PrivacyDialog`, `ScreenRecordTile`, `CustomTile`, `DeviceControlsTile`, `FooterActionsInteractorImpl` | AOSP variants |

## 3. Why the screenshot broke

`DaggerReferenceGlobalRootComponent` decides the screenshot implementation at injection
time:

```java
if (!SystemUIFeatures.getInstance().isEnabled("feature_zui_screenshot")
        || XSystemUtil.isCTSGTSTest()) {
    factory = aospFactory;      // anonymousClass139
}
```

With the flag stuck at true, `TakeScreenshotExecutorImpl` takes the generic entry and
`PolicyRequestProcessor` still runs the Lenovo policy, which deliberately reports
`bitmap = null` as an intermediate state for the dedicated controller that now never runs:

```text
PolicyRequestProcessor: modify: ScreenShotFeatures
Screenshot request: ScreenshotData(... bitmap=null ...)
E Screenshot: handleScreenshot: Screenshot bitmap was null
```

This is exactly the reported "falls back to the AOSP infrastructure", and it is why the
symptom tracks the `NativeNotificationIcon` switch: that module is the only thing in ZTool
that hooks the predicate.

## 4. The defect: an ungated second hook

`NativeNotificationIcon` installs the predicate hook twice.

* Hook 1 (`is_ctsgts_test`) is gated: it returns the `isCtsMode` ThreadLocal when set and
  otherwise proceeds to the original.
* Hook 2 (`update`) sets that ThreadLocal around `NotificationShelf.updateResources$5`, so
  only the shelf icon-colour decision sees CTS mode. This is the intended design.
* Hook 6 (`method_to_replace`) hooked the *same* `XSystemUtil.isCTSGTSTest` method with an
  unconditional `{ true }`. It is the fallback taken whenever
  `android.app.Notification$Builder.isCtsGtsTest` is missing, which is every time on this
  build.

Because the second hook never calls `chain.proceed()`, the result is `true` no matter how
the two hooks are ordered, so hook 1's gate and hook 2's scoping are both dead. The logs
confirm both installs in the screenshot process:

```text
[NativeNotificationIcon] Successfully hooked com.android.systemui.util.XSystemUtil. [1/6]
[NativeNotificationIcon] Unable to hook method isCtsGtsTest! Try alternate way.
[NativeNotificationIcon] Successfully hooked android.app.Notification$Builder with alternate way.[6/6]
```

and the flag reading true outside any notification-icon code, in a process that never
created a shade:

```text
DismissOngoing: isDismissableForState : packageName = com.android.systemui , ... , isCTSGTSTest = true
```

## 5. Fix

Hook 6 keeps its framework-side target (still correct on builds that have it) and no longer
falls back to the SystemUI predicate. The gated hook 1 is the only path to the flag, so the
AOSP notification-icon behaviour stays scoped to the shelf and the screenshot, theme,
keyguard, battery and QS consumers stop seeing a false CTS/GTS verdict.

Residual: on Android 16 the status-bar icon tint path
(`NotificationIconContainerViewBinder$bind$2` via `StatusBarIconViewBinder.bindIconColors`,
and `DarkIconDispatcherImpl.applyDarkIntensity`) also reads the flag, and no gate covers it.
If the status-bar icons visibly revert to ZUI colours, gating
`DarkIconDispatcherImpl.applyDarkIntensity(float)` with the same ThreadLocal is the next
step; the viewbinder collector is inlined into a per-site lambda
(`NotificationIconContainerViewBinder$bind$2$1$2`) and cannot be gated by wrapping its
enclosing suspend function, which never returns.
