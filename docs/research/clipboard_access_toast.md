# Clipboard access toast ("pasted from your clipboard")

Findings behind `hook/modules/systemframework/ClipboardAccessToastHook.kt`.

## Symptom

Pasting into an app shows a system toast such as
`已将剪贴板中的内容粘贴到通讯录` / `<App> pasted from your clipboard`.

## Where it does not live

`com.android.systemui` only *renders* system toasts (ToastUI); its APK contains neither
the text nor the resource id. Searching SystemUI's `strings.xml` for the message is a dead end.

## Resource

`/system/framework/framework-res.apk`:

```
resource 0x01040793 string/pasted_from_clipboard
  (zh-rCN) 已将剪贴板中的内容粘贴到%1$s
  (en)     %1$s pasted from your clipboard
```

The pseudo-locales (`en-rXA`, `ar-rXB`, …) show it is an upstream AOSP string, not a ZUI addition.

## Producer

`services.jar` → `com.android.server.clipboard.ClipboardService`:

```
showAccessNotificationLocked(String sourcePackage, int uid, int userId, Clipboard clipboard, int deviceId)
```

Called from `ClipboardImpl.getPrimaryClip(callingPackage, attributionTag, userId, deviceId)`,
so `sourcePackage` is the **reading (pasting) app**, which is what a per-app whitelist filters on.

Guards, in order: empty primary clip → `Settings.Secure["clipboard_show_access_notifications"]`
(per user, default from `getDefaultClipboardAccessNotificationsSetting()`) → same-app copy
(`UserHandle.isSameApp`) → default IME → content-capture service → augmented autofill service →
`android.permission.SUPPRESS_CLIPBOARD_ACCESS_NOTIFICATION` (protectionLevel
`signature|privileged|role`, so it cannot be granted via `pm grant`) → virtual-device owner →
`mNotifiedUids` (one toast per clip per uid). Then
`lambda$showAccessNotificationLocked$5` resolves the app label and shows the toast,
using `Toast.makeCustomToastWithIcon` when
`SafetyProtectionUtils.shouldShowSafetyProtectionResources` is true, else `Toast.makeText`;
it also emits `Slog.i("ClipboardService", message)`.

## Reverse-engineering notes

`R.string.*` is a compile-time `static final int`, so the field name never appears in the
consumer's dex — name searches in Jadx miss it, and Jadx may reverse-map the same constant to
`R.string.pasted_from_clipboard` at unrelated call sites (e.g.
`DevicePolicyManagerService.getNetworkLoggingTitle`), which are false positives.

Locating it reliably: pull the dex, parse `class_defs` → `code_item` ranges, and look for the
little-endian literal `93 07 04 01` (`0x01040793`) inside a method body.

## Global switch

```powershell
adb shell settings put secure clipboard_show_access_notifications 0
adb shell settings delete secure clipboard_show_access_notifications
```

Verified on TB710FU / ZUXOS 1.5.04.509 (Android 16): value `0` suppresses the toast, deleting
restores the default. `com.android.settings` ships no UI for this key.
