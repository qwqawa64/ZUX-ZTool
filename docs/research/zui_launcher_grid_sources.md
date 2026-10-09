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

## 5. Measured desktop proportions (TB710FU, 2590x1619 screenshot)

Used as the ratio constants of the preview renderer:

| Element | Measured | Ratio |
|---|---|---|
| Status-bar band above the wallpaper | 160 px | 9.9 % of the height |
| Workspace side dead space (wide grid on, inset 16 dp) | 32 px | 1.2 % of the width |
| Icon plate | 120 x 120 px | 4.6 % of the width |
| Column pitch (8 columns) | 317 px | icon / cell width = 0.38 |
| Row pitch (6 rows) | 188 px | icon / cell height = 0.64 |
| Icon + label block | 166 px | 0.88 of the row pitch |
| Dock pill | 1445 x 195 px, centered | 55.8 % of the width, 12 % of the height |
| Dock slots | 8 (5 icons, separator, 3 icons) | icons the size of desktop icons |

The 32 px side dead space equals `wideGridSideInset` 16 dp at density 2, i.e. the
wide-grid hook applied; a square-mode run would instead pad by
`(availableWidth - columns * cellHeightPx) / 2`.

Measurements were taken from a screenshot the user provided; the pixel columns were read
with a run-length scan rather than by eye.

## 6. What ZTool actually reads

`data/launcher/LauncherPreviewRepository.kt` reads the `Settings.System` mirror and the
provider (both no-root). `data/launcher/LauncherGridResolver.kt` resolves the grid in this
order: ZTool custom grid -> provider numbers, but only while the catalog is
non-degenerate -> layout name as `<columns>x<rows>` -> fixed fallback. The name parse is
covered by `LauncherPreviewGridResolverTest`.

## 7. How the preview mirrors the hooks

`screens/launcher/LauncherPreviewCard.kt` reproduces the solver in
`LauncherWideGridHook` / `LauncherGridMetrics`, in the same order the launcher uses:

```
page box          = the whole frame; the top band (9.9 %) and bottom band (20.4 %) are the
                    vertical paddings, which is what cellLayoutPaddingPx.top/bottom carry
host cells        = (box - side padding) / columns  x  (box - bands) / rows   (borders are 0)
square mode       = only when the host cell is wider than 1.2 : the side is the banded
                    height, and the padding is whatever centres that side
```

`squareCells` in the UI state is the `wideGrid && wideGridSquare` pair, matching the hook's
own gate. Icon diameter is 0.65 of the cell's short side (measured 120/188 and 122/184),
labels take 0.14 of the row pitch plus a 0.10 gap, and the update dot sits ahead of the app
name.

The big folder is drawn inside the desktop grid at the top-left 2x2 cells, with
`BigFolderAlignHook`'s solved geometry: untuned `baseW/baseH`, the background box from the
`bg_x`/`bg_y` knobs, the child grid whose gaps come from `gap_h`/`gap_v`, and the
`shift_x`/`shift_y` centring.

Its child grid follows the alignment switch. Hook off: the host style table, which declares
**4 columns x 3 rows** for a 2x2 span on the 8x6 grid
(`BigFolderConfig.getBigFolderIconChildCount`). Hook on: the **3x3** grid
`BigFolderAlignHook` rewrites every multi-cell span to (`CHILD_COLS`/`CHILD_ROWS`), which is
what the live folder shows.

`ChildIconScale` is the host's `CHILD_ICON_SCALE` (0.8235, divided by `customIconScale` at
init). `FolderIconToIcon` and `ArtInsetToIcon` stay provisional: `folderIconSizePx` comes from
`folder_icon_size_scale` (a float resource the app cannot read) and `widgetPadding` from the
framework's default widget padding. One `BigFolderAlign span=2x2 … folderIcon=… icon=…
widgetPadL=…` log line pins both.
