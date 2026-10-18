# Pen Haptic Waveform Redirect — Promotion Plan

Goal: promote the test hook into a user-facing feature: switch + editable id→id mapping,
mapping editor UI with friendly waveform names, and the impact-waveform hook.

## Decisions

- One ordered rule list `source -> target`, stored as `"36:33,34:33"` in the shared
  `xposed_module_config` preference file.
- The catalog of ids/kinds/rule parsing lives in `data/pen/` and is shared by the app
  and the hook (no resources touched on the hook path).
- Rules may only map within the same family (continuous 32..41 / impact 1..7); the
  parser and the repository both drop cross-family, self and duplicate-source rules.
- UI lives in the existing Framework settings page (no new route/card): a section with
  the enable switch and an inline rule editor (add / retarget / remove).
- Hook rewrites `setHapticContinuousParams` and `setHapticImpactParams`; rules are read
  once at system_server start (scope restart is Reboot anyway).

## Steps

- [x] 1. `data/pen/PenHapticWaveforms.kt`: kinds, ids, names catalog, `parseRules`/`formatRules`.
- [x] 2. `PreferenceKeys`: enable Bool key + rules String key, registered in the type lists.
- [x] 3. `FrameworkSettingsRepository`: load/save enable + rules.
- [x] 4. `FrameworkSettingsViewModel`: UiState fields + add/retarget/remove actions.
- [x] 5. Strings (values + values-en-rUS): section, editor, 13 waveform labels.
- [x] 6. `FrameworkSettingsScreen`: section + `PenHapticRedirectEditor` composable.
- [x] 7. `SearchIndex`: entries for the switch and the rule editor.
- [x] 8. Hook: module name = enable key, continuous + impact rewrite, rule-driven.
- [x] 9. Verification: `assembleDebug`, `SearchIndexConsistencyTest`, string checker.
- [x] 10. Update `docs/research/lenovo-pen-haptic/waveform-kinds.md`; commit.

## Verification

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest --tests "com.qimian233.ztool.SearchIndexConsistencyTest"
python tools/check_unused_strings.py
```
