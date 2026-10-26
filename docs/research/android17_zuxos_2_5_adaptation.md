# ZUXOS 2.5 (Android 17) hook adaptation notes

Reference targets decompiled with jadx 1.5.6 from
`F:\USER\Jadx项目\Lenovo\android-17`:

| Artifact | Content |
| --- | --- |
| `系统界面_17.apk` | SystemUI (`com.android.systemui`) |
| `ZUX 桌面_18.6.0.0927.apk` | ZUI Launcher (`com.android.launcher3`, `com.zui.launcher`) |
| `services.jar` | system_server |
| `framework.jar` | `android.*`, `com.android.internal.*` |
| `软件包安装程序_7.3.0.1.0635.apk` | `com.android.packageinstaller` |

Sources: `<jar>/jadx-out/<name>/sources`. Decompiled dex is authoritative when
jadx shows a Kotlin `metadata` display name that differs from the runtime name
(see `WorkspaceProfile.isItemsLabelHidden` below).

## System framework (system_server)

- `com.android.server.wm.OneModeService` is gone. `android.os.OneModeManager` in
  framework.jar only binds a vendor `onemodeservice`; the split-screen decision
  now lives in `ActivityTaskManagerService.isSupportSplitScreen` ->
  `OvCommonService.supportOvSplitOnPkg`/`supportSplitFreeform`, and
  `isSupportSplitScreen` has no caller in services.jar. The forced split-screen
  hook therefore only keeps its legacy path and skips with one warning.
- `ParseInput` is an interface without `reset()`; the implementation
  `ParseTypeImpl` still has `public ParseInput reset()` and implements
  `ParseResult<Object>`. V1 signature recovery must resolve `reset` on the
  runtime class (`ParseTypeImpl`). `ApkSignatureVerifier.loadCertificates` and
  `convertToSignatures` are private static and need `setAccessible(true)`.
- `PhoneWindowManager.mZuiGameAppStateListener` is an anonymous
  `ZuiModeManager.GameAppStateListener` (anonymous class number changes between
  builds; `$2` is a `UEventObserver`). Resolve the runtime class from the field
  value instead of a hardcoded `$N` name.

## SystemUI

- Screenshot: `LegacyScreenshotController` no longer exists; the merged
  `ScreenshotController` keeps `handleScreenshot(ScreenshotData, Consumer,
  RequestCallbackImpl)`, `mCanBeLongScreenshotMotoFlag`, `window`, `context` and
  `scrollCaptureExecutor`. `ScreenshotView.canLongScreenshot` now takes a
  `Context`. The AOSP scroll-capture pipeline is a stub in this build
  (`ScrollCaptureExecutor`, `ScrollCaptureClient`, `ScrollCaptureController`,
  `ImageTileSet` are empty; `scroll/LongScreenshot` is missing), so the AOSP
  reroute cannot be restored; the stock long screenshot goes through the Moto
  `QuickScreenshotImpl`/`LongScreenshotOverlay` path.
- Face auth: `ZuiFaceAuthDelegate` and `KeyguardFaceUnlockManager` are gone.
  `ZuiFaceAuthAIDLDelegate` only reads enrollment/vibration state and delegates
  detection to `DeviceEntryFaceAuthRepositoryImpl`/`faceManager.detectFace`.
  The timeout gates are on the platform side: `LockSettingsStrongAuth`
  (24h non-strong-biometric, idle timeout using BackupManager constants) and
  `DevicePolicyManager.DEFAULT_STRONG_AUTH_TIMEOUT_MS` (72h). No 12h constant
  exists in the three decompiled trees; the ZUI 12h gate either disappeared with
  the delegate or lives in `com.zui.uface`/vendor code that is not in this set.
- Volume panel: `SystemUIDialog.updateWindowSize` takes a `Window`.
- Media output: `MediaOutputBaseDialog` is gone; `MediaOutputDialogManager`
  exposes `createAndShow(UserHandle, String, boolean, Controller, boolean,
  Token, MediaSwitchingType, boolean)` (package at index 1,
  `includePlaybackAndAppMetadata` at 4, `useSystemColors` at 7) and delegates
  dialog creation to `MediaOutputDialogDelegate.createDialog()`. The 17 build
  already centres the dialog (`onCreate` sets `gravity = 17`); `mCustomGravity`
  is applied by `SystemUIDialog.updateWindowSize`.
- Freeform/window decor:
  - `DragPositioningCallbackUtility.changeBounds` is static with nine arguments
    ending in `WindowDecorationWrapper` and `ChangeBoundsResult`; the utility is
    reached through `WindowDecorationWrapper.ovWindowDecor` ->
    `OvWindowDecoration.ovfDpcUtility`.
  - `DragResizeWindowGeometry.calculateCtrlType` became
    `calculateEdgeResizeCtrlType(float, float)`; `OvcShellUtils.isUnderPcMode()`
    became `OvcPcModeUtils.isUnderPcMode()` (`OvcShellUtils` keeps an `int`
    overload).
  - `WindowDecoration$WindowDecorationInsets` is now the top-level
    `com.android.wm.shell.windowdecor.WindowDecorationInsets`. Caption handling
    moved to `caption.CaptionController`
    (`updateCaptionInsets(WindowDecoration2.RelayoutParams, List, Context,
    WindowContainerTransaction, int, Rect, Rect)`,
    `updateViewHierarchy(WindowDecoration2.RelayoutParams, WindowDecorViewHost,
    View, int, int, Transaction, Region)`, `addCaptionInset(WCT)`).

## Launcher 18.6

`DeviceProfile` delegates geometry to profile objects; the old fields are gone:

- `WorkspaceProfile` (getter `DeviceProfile.getWorkspaceProfile()`, setter
  `setWorkspaceProfile(WorkspaceProfile)`): `cellLayoutPaddingPx`,
  `cellLayoutBorderSpacePx`, `cellWidthPx`, `cellHeightPx`, `iconSizePx`,
  `iconTextSizePx`, `iconDrawablePaddingPx`, `cellYPaddingPx`, `widgetPadding`,
  `desiredWorkspaceHorizontalMarginPx`. Writes go through the 42-parameter
  `copy`; the `isItemsLabelHidden` getter is `isItemsLabelHidden()` in dex even
  though jadx renames it from `component28`.
- `FolderProfile` (via `DeviceProfile.getFolderProfile()`): `folderIconSizePx`.
- `HotseatProfile`: `numShownIcons`, `columnSpan`, `borderSpace`; built by
  `recalculateHotseatWidthAndBorderSpace(InvariantDeviceProfile, DeviceProfile,
  int)` and `DeviceProfile.recalculateHotseatWidthAndBorderSpace(int)`.
- Hotseat count: `Hotseat.getMaxCount` removed; `Hotseat.resetLayout` uses
  `getHotseatProfile().getNumShownIcons()`. `InvariantDeviceProfile.
  getNumDatabaseHotseatIcons` removed, `numDatabaseHotseatIcons` is a public
  instance field.
- `LoaderCursor.checkItemPlacement(ItemInfo)` lost the boolean parameter (the
  dimension check is now `m(ItemInfo)`); `checkAndAddItem` takes four arguments.
  `LauncherModel.addOrMoveItemInDatabase` moved to `ModelWriter`. `Launcher.
  addToWorkspace(ItemInfo, boolean)` was removed (the same name on
  `LauncherAccessibilityDelegate` is an unrelated path).
- `com.zui.launcher.GlobalSearchView` moved to
  `com.zui.launcher.globalsearch.GlobalSearchView`; hot word inflation is
  `hotWordView()`, data population `handleWordView(List<HotWordData>)`, and
  `setHotWordHint()` only sets the search box hint.
- `CloudUtils.showCloudFolderAuthorizationDialog` became
  `showCloudFolderAuthorizationDialogPrc(Launcher, Runnable, Runnable)`.

## Package installer

`PackageInstallerActivityExtra.onDirectInstall()` and
`handleDirectInstallInFindSameAppCase()` are `private` in the dex (jadx shows
them as `public` after promoting access); callers must resolve them with
`isAccessible = true`. `onResume` is declared in the same class.
