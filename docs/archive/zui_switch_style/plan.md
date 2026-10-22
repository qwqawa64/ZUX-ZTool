# ZUI Switch restyle — implementation plan

Status: implemented (`ZuiSwitchStyleHook` + the "开关" settings section). Follow-ups landed
during device testing: the anchor moved to the platform `android.widget.Switch` (covers the
AOSP Wi-Fi detail switch), the M3 thumb is a constant 24dp so the marks match, and the track
colour follows the stock ZUI switch palette or a user ARGB. Verified on device: dark and
light themes, indicator check, M3 check/cross/both, custom colour, and the network-detail
switch; RTL and the launcher/desktoplauncher scopes are still to be exercised.

Implements work-order item G-01 per `docs/research/zui_switch_style.md`.
Anchor: every declared constructor of `android.widget.Switch` (covers `zui.widget.Switch`).
Two user-selectable styles plus a mark and colour option:

* `indicator` — keep the ZUI track; wrap the thumb drawable and draw
  check-when-on / cross-when-off according to the mark option.
* `m3` — force a 52dp box, transparent 24dp thumb, and a track drawable that
  paints the whole Material 3 switch (track + constant-size sliding thumb), with
  the same optional mark inside the thumb.

## Preference keys (`PreferenceKeys.kt`)

| Key | Type | Default |
|---|---|---|
| `zui_switch_style` | Bool (master) | `false` |
| `zui_switch_style_kind` | String (`indicator` / `m3`) | `indicator` |
| `zui_switch_mark` | String (`none` / `check` / `cross` / `both`) | `check` |
| `zui_switch_follow_system_colors` | Bool (`m3` track accent) | `true` |
| `zui_switch_track_color` | Int ARGB (`m3` accent override) | `0xFF3482FF` |

## Files

1. `data/keys/PreferenceKeys.kt` — three keys, bool/string lists.
2. `data/zuiui/SwitchStyle.kt` — `SwitchStyle`, `SwitchMark` enums + `fromPref`.
3. `data/zuiui/ZuiUiComponentsRepository.kt` — load/save the three values.
4. `viewmodel/ZuiUiComponentsViewModel.kt` — UiState fields + setters.
5. `screens/zuiui/ZuiUiComponentsScreen.kt` — new "Switch" section: master
   switch, style dropdown, mark dropdown; callback plumbing.
6. `hook/modules/zuiui/ZuiSwitchStyleHook.kt` — constructor hook + restyle.
7. `hook/modules/zuiui/switch/SwitchStyleDrawables.kt` — `GlyphThumbDrawable`,
   `M3SwitchDrawable`, shared glyph/color helpers, transparent thumb drawable.
8. `hook/base/HookManager.kt` — register in `registerZuiWidgetModules()`.
9. `res/values/strings.xml`, `res/values-en-rUS/strings.xml` — new UI strings.
10. `search/SearchIndex.kt` — three entries with matching `SettingItem.key`.
11. `docs/research/zui_ui_hook_work_order.md` — G-01 status note.

## Verification

* `.\gradlew.bat assembleDebug`
* `python tools/check_unused_strings.py`
* `.\gradlew.bat testDebugUnitTest --tests "com.qimian233.ztool.SearchIndexConsistencyTest"`
* Device: install the debug APK over the debug-signed install, enable the
  feature from the UI, hot reload, screenshot Settings WLAN + Display switches
  for `indicator` (check / cross / both) and `m3` (none / check / cross); verify
  light/dark. Restore the feature to off afterwards.

## Out of scope

Colour pickers (theme colours are used), track-drawn dual glyphs, and stock
`android.widget.Switch` / `SwitchCompat` / `MaterialSwitch` instances.
