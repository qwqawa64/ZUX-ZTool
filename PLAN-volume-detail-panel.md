# Plan: Volume Slider Long-Press Detail Panel (VolumeDetailPanel)

Agent-oriented implementation plan. Research basis is recorded in the conversation:
long-pressing the control-center volume slider must open a BrightnessDetailDialog-style
controller containing media / ring / app-per-app volume sliders and three QS tiles
(mute, DND, vibrate-only-when-supported).

## Background facts (verified against decompiled ZUI SystemUI)

- Trigger: `ControlCenterLongPressHook` already runs the squish detector on the QS volume
  slider (`zui.widget.SeekBarNps.onTouchEvent` path). Its volume callback is currently
  "animation only" and the header comment already reserves `VolumeSliderLongPressHook`
  as the dialog owner. We wire that reserved hook in.
- Dialog container: `com.android.systemui.statusbar.phone.SystemUIDialog` has a public
  3-arg constructor `(Context, int themeRes, boolean registerDismissReceiver)` usable via
  reflection; it self-wires Dependency-based singletons, SCREEN_OFF/CLOSE_SYSTEM_DIALOGS
  dismissal, and system window flags. Module code must NOT subclass SystemUI classes
  (module classloader cannot resolve SystemUI superclasses) — everything is reflection.
- Tiles: NOT taken from `QSHostAdapter` (no `getTile(spec)`; mute/vibrate/zen are not in
  the brightness-related collection). Instead we render `CustomizeTileView` views fed
  with reflectively-constructed `QSTile.BooleanState` objects and drive the state logic
  ourselves:
  - mute: `AudioManager.getRingerModeInternal() == 0`, click toggles 0 <-> 2 (mirrors QMuteTile)
  - vibrate: `vibrate_on==1 && (ring_vibration_intensity==2 || notification_vibration_intensity==2)`,
    click rewrites those Settings.System keys (mirrors QVibrateTile); gated by
    `Vibrator.hasVibrator()`
  - DND: zen via `NotificationManager.setInterruptionFilter` (public API),
    `PRIORITY <-> ALL`
  - long-click any tile -> `android.settings.SOUND_SETTINGS`
- Sliders:
  - media = `STREAM_MUSIC` (3), ring = `STREAM_RING` (2): plain AudioManager get/set.
  - app volume = per-uid relative volume: list of active apps from
    `com.android.systemui.volume.appvolume.AudioSystemHelper.registerAudioAppListCallback`
    (public interfaces `OnAudioListChangeCallback` / `OnErrorCallback`, registered via a
    `java.lang.reflect.Proxy` because module classes cannot implement SystemUI
    interfaces); value commit via `RelativeVolumeHelper.setRelativeVolumeInternal(double, int)`
    (`AudioManager.setParameters("uid=<uid>;app_volume=<v>")`), persistence to
    `Settings.System "zui_app_volume"` (`uid/pct;uid/pct`) implemented by us with public
    Settings APIs. Up to 3 app rows.
- Visuals: same slider look as the control center by applying the
  `brightness_progress_selector` progress drawable (same resource the stock
  ToggleSliderView uses) with thumb null; container styled from theme
  `Theme_SystemUI_Dialog_GlobalActionsLite` with a rounded `colorBackgroundFloating`
  background; dialog theme switched to `Theme_SystemUI_QuickSettings` before tile view
  creation (mirrors BrightnessDetailDialog.onStart).

## Files

New:
- `app/src/main/java/com/qimian233/ztool/hook/modules/systemui/qs/VolumeSliderLongPressHook.kt`
  — AppHookModule; module name `volume_long_press_panel`; target `ScopeKeys.SYSTEM_UI`.
  Contains the dialog host logic (all reflection). Exposes
  `onVolumeSliderLongPress(view)` consumed by ControlCenterLongPressHook.

Modified:
- `app/src/main/java/com/qimian233/ztool/data/keys/PreferenceKeys.kt` — add
  `VOLUME_LONG_PRESS_PANEL = BoolKey("volume_long_press_panel", false)` next to
  CONTROL_CENTER_LONG_PRESS and register it in the key list.
- `app/src/main/java/com/qimian233/ztool/hook/modules/systemui/qs/ControlCenterLongPressHook.kt`
  — volume slider trigger callback now also calls
  `VolumeSliderLongPressHook.onVolumeSliderLongPress(view)` (dialog construction stays
  out of the detector, per existing header comment).
- `app/src/main/java/com/qimian233/ztool/hook/base/HookManager.kt` — register
  `VolumeSliderLongPressHook()` after `ControlCenterLongPressHook()`.
- Frontend: `ControlCenterSettingsUiState.kt` (field), `ControlCenterSettingsViewModel.kt`
  (setter), `ControlCenterSettingsRepository.kt` (load/save), `ControlCenterSettingsScreen.kt`
  (switch under the long-press switch), `SearchIndex.kt` (entry id `volume_long_press_panel`),
  `res/values/strings.xml` + `res/values-en-rUS/strings.xml`.

## Steps

1. [ ] PreferenceKeys: add + register `VOLUME_LONG_PRESS_PANEL`.
2. [ ] Implement `VolumeSliderLongPressHook.kt`:
   - handleLoadPackage reads remote pref, stores singleton in companion.
   - `onVolumeSliderLongPress`: re-entrancy guard, build dialog, show.
   - Dialog host: reflection helpers (`findClassOrWarn`, resource id lookup by name),
     container layout, media/ring slider rows, app-volume section (Proxy callback
     registered once, static, refreshable), tile row (mute/DND/vibrate) with
     ContentObserver-driven refresh while dialog is showing.
3. [ ] Wire trigger call in `ControlCenterLongPressHook` volume callback.
4. [ ] Register in HookManager.
5. [ ] Frontend switch end-to-end (UiState/Repo/VM/Screen/SearchIndex/strings zh+en).
6. [ ] `./gradlew.bat assembleDebug`, fix errors.
7. [ ] Commit.

## Notes / constraints

- Requires `control_center_long_press` to be enabled too (that switch owns the slider
  long-press detector). Documented in the setting summary.
- Guard conditions from `BrightnessDetailDialogController.showOrHideDialog` (shade
  expanded, no other detail dialog) are approximated by the trigger context: the QS
  volume slider is only touchable while the shade is expanded; re-entrancy guard prevents
  stacking. V1 accepts this.
- No DexKit index needed: all class names are non-obfuscated stock/ZUI names, same as
  neighbouring qs hooks.
- All new code UTF-8, Kotlin only.
