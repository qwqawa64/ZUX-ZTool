# ZUI launcher grid: externally readable sources

Reverse-engineering note for the desktop preview card on the launcher settings page
(`screens/launcher/LauncherPreviewCard.kt`). Everything here was verified against
`com.zui.launcher` (Lenovo ZUX OS, device TB710FU) with JADX plus on-device shell reads.

## 1. Where the "change desktop layout" choice goes

```
ZuiLauncherFragment.S()                     // ListPreference "container_title",
  -> Utilities.queryGridOptions(ctx)        //   dialog title @string/change_launcher_layout
  -> Utilities.changeDefaultGridOption(ctx, name)   // on selection
       ContentResolver.update("content://com.zui.launcher.grid_control/default_grid", {name})
         -> LauncherCustomizationProvider (ContentProviderProxy)
         -> GridCustomizationsProxy.update("/default_grid")
         -> InvariantDeviceProfile.setCurrentGrid(ctx, name)
              -> Utilities.setChangeGridOptionFlag(ctx, true)
              -> SettingsValue.setCurrentLayoutConfigKey(appCtx, name)
                   -> SharedPreferences "com.zui.launcher.permanent_settings"
                        key "current_layout_config_key"
                   -> Settings.System.putString(cr, "extra_new_layout_config", name)
              -> DeviceProfile recomputed on the main executor
```

`SettingsValue.CURRENT_LAYOUT_CONFIG_KEY` is `current_layout_config_key`; the
`Settings.System` mirror only gets written while the launcher holds `WRITE_SETTINGS`.
Reads go through `SettingsValue.getCurrentLayoutConfigKey` -> `Utilities.getCurrentGridName`.

## 2. What can be read from outside, and how

| Source | Access | Contents |
|---|---|---|
| `content://com.zui.launcher.grid_control/list_options` | exported, **no permission** | `name, grid_title, rows, cols, preview_count, is_default, grid_icon_id, can_be_shown` |
| `Settings.System["extra_new_layout_config"]` | no permission | current layout name, e.g. `6x4` |
| `/data/data/com.zui.launcher/shared_prefs/com.zui.launcher.permanent_settings.xml` | root | `current_layout_config_key` (absent after a data reset) |
| `/system/media/zui_launcher_config.xml` | no permission | feature flags only (blur, overview, wallpaper zoom). **No layout key on TB710FU**, despite the keys `DefaultConfigReader` declares |

`ZuiLauncherSettings.s()` force-enables the provider component
(`setComponentEnabledSetting(..., ENABLED, DONT_KILL_APP)`), so `list_options` works once
the user has opened the launcher settings panel at least once.

`GridCustomizationsProxy` also exposes `call("get_preview", …)`, which returns a
`SurfaceControlViewHost.SurfacePackage` rendering the real desktop (bundle keys:
`name, width, height, display_id, host_token, hide_bottom_row, wallpaper_colors,
skip_animations, layout_xml`; messages `7414` = update grid, `1337` = hide bottom row).
Not used by ZTool: embedding a child surface in a scrolling settings card is a much
larger commitment than the proportional mock.

## 3. Layout names encode the grid

`device_profiles.xml` (APK resources) names every profile `<numColumns>x<numRows>`:

```
name="4x5" numColumns="4" numRows="5"
name="4x6" numColumns="4" numRows="6"
name="5x5" numColumns="5" numRows="5"
name="5x6" numColumns="5" numRows="6"
name="6x5" numColumns="6" numRows="5"
```

The launcher displays the name verbatim (`getCurrentGridName().replace("x", "×")`), so a
name alone is a complete grid description. Confirmed on TB710FU: `6x4` = 6 columns x 4 rows.
Names may carry a prefix (`learning_6x4`), which the parser skips.

`device_profiles.xml` declares only *inputs* to the grid math (`minCellWidth`,
`borderSpaceHorizontal`, `horizontalMargin`, `hotseatBarBottomSpace`, `iconImageSize`, …)
in dp. The cell pixel size is computed at runtime by `DeviceProfile`, is persisted
nowhere, and is therefore unavailable to any non-hooked process.

## 4. The provider's numbers are polluted by our own grid hook

`CustomGridSize` intercepts the `InvariantDeviceProfile$GridOption` constructor and
overwrites `numColumns` / `numRows` on **every** profile with the user's custom values
(its enabled state is the `CustomGridSize` preference key, matching
`BaseHookModule.isEnabled()`).

Measured on TB710FU with the custom grid switch on (8 columns x 6 rows):

```
Row: 0 name=6x4,         rows=6, cols=8, is_default=true, canBeShown=true
Row: 1 name=6x5,         rows=6, cols=8, is_default=true, canBeShown=false
Row: 2 name=5x4,         rows=6, cols=8, is_default=true, canBeShown=true
Row: 3 name=learning_6x4, rows=6, cols=8, is_default=true, canBeShown=false
```

All entries report one grid, and `is_default` (computed by comparing the live
`InvariantDeviceProfile` against each option) degenerates to `true` everywhere. The names
stay correct; the numbers do not.

Consequence for the preview: while the custom grid switch is on, the real grid **is**
ZTool's own `customGridColumn/customGridRow`, and no external source is needed. When the
switch is off, the hook is not installed, the provider numbers are clean and may be used.

## 5. What ZTool actually reads

`data/launcher/LauncherPreviewRepository.kt` reads the `Settings.System` mirror and the
provider (both no-root). `data/launcher/LauncherGridResolver.kt` resolves the grid in this
order: ZTool custom grid -> provider numbers, but only while the catalog is
non-degenerate -> layout name as `<columns>x<rows>` -> fixed fallback. The name parse is
covered by `LauncherPreviewGridResolverTest`.
