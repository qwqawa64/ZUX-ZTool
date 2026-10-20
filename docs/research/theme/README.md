# Theme Reveal And Theme Switching Notes

Agent-facing notes for the theme layer. Code: `ui/theme/ThemeRevealProvider.kt`
(reveal controller and overlay), `ui/components/theme/` (shared settings UI),
`screens/ztoolsettings/theme/` (theme settings route), `ui/firstrun/FirstrunThemePage.kt`
(OOBE page).

## The reveal is faint by design — verify before calling it broken

`triggerReveal` snapshots the window with `PixelCopy`, dims the screen with
`lerp(Color.Black, primary, 0.50)` at alpha 0.45 fading out over 600 ms, and cuts a
circle into the snapshot that grows from the tap anchor. On light themes with a pale
primary both the dim layer and the circle edge are low contrast, so a style switch can
look like an instant swap.

- The reveal only runs for frontend-style switches (`Material3Expressive` ↔ `Miuix`).
  Theme mode, palette and every other theme row apply instantly, by design.
- `triggerReveal` returns silently while another reveal is running (`isRevealing`), and
  falls back to calling `onAction` directly when `PixelCopy` fails or no window exists.
  Callers must never assume the animation played; state changes belong inside
  `onAction`, and flags read by `transitionSpec` are set there too (OOBE navigation
  does this so a skipped reveal cannot leave `revealNavigation` stuck).
- When a user reports "the reveal is missing", ask them to watch once more before
  treating it as a regression: it is usually present but pale.

## Do not try to capture the ripple in a screenshot

`adb screencap` / `screenrecord` frame chasing is a dead end: the whole sequence is
600 ms plus `PixelCopy` latency, and a capture command costs a few hundred
milliseconds itself. Frames land either before the tap is processed or after the new
theme is already fully applied, and a frame showing the new theme says nothing about
whether the animation ran. Verify by reading the call path and confirming the end
state; let the person looking at the screen judge the animation.

## Flip the theme from a root shell

The app reads its theme from `shared_prefs/ztool_ui_theme_preferences.xml` (preference
file `ztool_ui_theme_preferences`). `ThemePreferencesRepository.observeSettings` hands
changes to `MainActivity`, which re-themes the whole app immediately.

1. `adb shell am force-stop com.qimian233.ztool` — SharedPreferences keeps the file
   cached in memory and would overwrite an external edit when the app exits.
2. Rewrite the **existing** file in place:
   `adb shell su -c "cat /data/local/tmp/theme.xml > /data/user/0/com.qimian233.ztool/shared_prefs/ztool_ui_theme_preferences.xml"`.
   Never delete and recreate the file as root: the new inode gets root ownership and a
   label without the app's MLS categories, so the app cannot read it and silently
   falls back to default theme values. Writing into the existing file keeps owner,
   mode and the `app_data_file` label; check with
   `adb shell su -c "stat -c '%C' <file>"`.
3. Relaunch the app. The rewrite is only a test shortcut — keep the values inside the
   enums and ranges the repository parses.

Keys: `frontend_style` (`Material3Expressive`/`Miuix`), `theme_mode`
(`FollowSystem`/`Light`/`Dark`), `material_color_spec` (`Spec2021`/`Spec2025`),
`material_palette` (`TonalSpot`, `Neutral`, `Vibrant`, `Expressive`, `Rainbow`,
`FruitSalad`, `MonoChrome`, `Fidelity`, `Content`), the booleans
`dynamic_color_enabled`, `amoled_black_enabled`, `use_alternative_icon`,
`predictive_back_gesture_enabled`, `manual_color_enabled`, `enable_floating_bottom_bar`,
`enable_floating_bottom_bar_blur`, and the long `manual_seed_color` (ARGB).

`adb shell run-as` cannot write app data on ZUXOS test devices (SELinux `runas_app`
denies even `files/`); use root or drive the UI.

## OOBE

The first-run theme page switches style through the same reveal controller, and page
navigation uses reveals too. Both the intro and the transitions depend on the
controller being idle: avoid triggering a style reveal and a page navigation in the
same instant, or one of them is silently dropped.
