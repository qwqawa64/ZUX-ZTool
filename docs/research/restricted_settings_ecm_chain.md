# Restricted settings (Enhanced Confirmation Mode) on ZUX OS 1.5 / Android 16

Analysis target: Lenovo TB710FU, `TB710FU_CN_OPEN_USER_Q00019.0_A16_ZUXOS_1.5.04.509_ST_260911`,
Android 16 (SDK 36). Artifacts pulled to `F:\USER\Jadx 项目\Lenovo` (ZuiSettings,
ZuiPermissionController) and `F:\USER\Jadx 项目\_framework` (framework.jar, services.jar,
`/apex/com.android.permission/javalib/*`).

## Symptom

A sideloaded app (ZTool itself is the example) cannot be granted "restricted" settings
such as usage access (`android:get_usage_stats`), display over other apps
(`android:system_alert_window`), notification access (`android:access_notifications`),
accessibility (`android:bind_accessibility_service`), SMS permissions or the dialer/SMS
roles. Settings shows the row but disables it, and the user is expected to walk to the
app detail page and pick "允许受限设置" first.

`dumpsys appops --package com.qimian233.ztool` shows the backing state:

```
ACCESS_RESTRICTED_SETTINGS (default): rejectTime=...
```

while an app that was manually unlocked shows `ACCESS_RESTRICTED_SETTINGS (allow)`.

## The app op is only the storage, not the gate

`AppOpsManager.OP_ACCESS_RESTRICTED_SETTINGS = 119`, op string
`android:access_restricted_settings`. On this ROM the app op is written/read only by the
ECM service (below) and by the legacy Settings code paths that are dead when the ECM
aconfig flags are on.

Both relevant aconfig flags are **enabled** on this device
(`aflags list`, needs root):

```
android.permission.flags.enhanced_confirmation_mode_apis_enabled  enabled
android.security.extend_ecm_to_all_settings                       enabled
```

With `extend_ecm_to_all_settings` enabled, `RestrictedLockUtilsInternal` takes the ECM
branch for every protected setting and never consults app op 119 again.

## Chain

Settings side (`com.android.settings` → `ZuiSettings.apk`, `/system/priv-app/ZuiSettings`):

- `com.android.settings.applications.UsageAccessDetails#refreshUi` (the screen that was
  open during analysis, fragment args `package` + `uid`) calls
  `RestrictedSwitchPreference.checkEcmRestrictionAndSetDisabled("android:get_usage_stats", pkg)`.
- `com.android.settingslib.RestrictedPreferenceHelper#checkEcmRestrictionAndSetDisabled`
  → `RestrictedLockUtilsInternal.checkIfRequiresEnhancedConfirmation(ctx, id, pkg)`.
- `RestrictedLockUtilsInternal`:
  - `checkIfRequiresEnhancedConfirmation` → `EnhancedConfirmationManager.isRestricted(pkg, id)`
    and, if restricted, `createRestrictedSettingDialogIntent(...)` to explain the block.
  - `isEnhancedConfirmationRestricted` → same `isRestricted` query.
- The ZUI Compose app-detail page
  `com.android.settings.spa.app.appinfo.AppInfoSettingsMoreOptionsKt` behaves the same:
  `allowRestrictedSettings` calls `EnhancedConfirmationManager.clearRestriction(pkg)`, and
  `shouldShowAccessRestrictedSettings` calls `isClearRestrictionAllowed(pkg)`.
- `AppInfoDashboardFragment` mirrors both (`setMode(119, uid, pkg, 0)` /
  `noteOpNoThrow(119, ...)`) but only in the flag-off branch.

Framework / service side:

- `android.app.ecm.EnhancedConfirmationManager` (framework client, from
  `/apex/com.android.permission/javalib/framework-permission-s.jar`) forwards to the
  `ecm_enhanced_confirmation` system service (`android.app.ecm.IEnhancedConfirmationManager`).
- The service is published by `com.android.ecm.EnhancedConfirmationService` from
  `/apex/com.android.permission/javalib/service-permission.jar`, which is on
  `SYSTEMSERVERCLASSPATH` — i.e. **it runs inside system_server**, not in the
  PermissionController app. The app only ships `EnhancedConfirmationDialogActivity`
  (`com.android.permissioncontroller.ecm`).
- `EnhancedConfirmationService$Stub`:
  - `getRestrictionReason(pkg, settingId, userId)` — the single decision point. Returns
    `null` when unrestricted, otherwise `package_restricted` / `phone_state`.
  - `isRestricted(...)` is literally `getRestrictionReason(...) != null`.
  - `isPackageEcmGuarded` → `getAppEcmState` = `noteOpNoThrow("android:access_restricted_settings")`;
    state 0 (`allow`) means unrestricted, state 1/2 means guarded.
  - `clearRestriction(pkg, userId)` writes app op 119 to `allow`.
  - `isClearRestrictionAllowed(pkg, userId)` is `getAppEcmState == 1`, used only for menu
    visibility of the "允许受限设置" entry.
  - `PER_PACKAGE_PROTECTED_SETTINGS` holds the protected identifiers, including
    `android:get_usage_stats`, `android:loader_usage_stats`, `android:system_alert_window`,
    `android:access_notifications`, `android:bind_accessibility_service`, the SMS
    permission set and `android.app.role.DIALER` / `android.app.role.SMS`.

## Hook consequence

Short-circuiting `EnhancedConfirmationService$Stub.getRestrictionReason` to `null` in
system_server makes `isRestricted` false for every package and setting, so no consumer —
Settings UI, PermissionController, or system_server itself — can disable a protected
setting or raise the ECM dialog. `isClearRestrictionAllowed` is forced to `false` as well
so the now-pointless "允许受限设置" menu entry disappears.

This is the `AllowAllRestrictedSettings` module (`hook/modules/systemframework/`), scoped to
`ScopeKeys.SYSTEM_SERVER` (`system`, `HowToRestart.Reboot`). `scope.list` already declares
`system`.

The hook only intercepts the query; it never writes app op 119, so turning the switch off
restores the original restriction state exactly.

## Empirical confirmation (TB710FU, ZUX OS 1.5)

`com.android.settings.applications.UsageAccessDetails` for `com.qimian233.ztool`
(`PACKAGE_USAGE_STATS` declared, `GET_USAGE_STATS` not granted):

| `ACCESS_RESTRICTED_SETTINGS` | switch | extra summary row |
| --- | --- | --- |
| `default` (shipped state) | `enabled=true` | — |
| `ignore` (forced for the test) | `enabled=false` | `由受限设置控制` |

So the app op state really is what drives the UI, and `default` is resolved through the
install-source branch of `isPackageEcmGuarded` — on this ROM the trusted-installer list is
empty, so a package whose `InstallSourceInfo.getPackageSource()` is not
`DOWNLOADED_FILE`/`OTHER` ends up unrestricted even at `default`. Apps installed from a
downloaded file are the ones that hit the wall.

## Legacy path (flag-off ROMs)

On a ROM where `extend_ecm_to_all_settings` is disabled, `RestrictedLockUtilsInternal`
falls back to app op 119 for `android:bind_accessibility_service` and
`RestrictedSwitchPreference.updateState` uses
`noteOpNoThrow(119) == MODE_ALLOWED || MODE_DEFAULT` together with the
`config_enhancedConfirmationModeEnabled` bool resource. Those ROMs are not covered by this
module; covering them would need a separate `com.android.settings` hook.
