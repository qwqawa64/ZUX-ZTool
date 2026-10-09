# Big folder: hand tuning the geometry

Companion to `zui_vs_oplus_launcher_grid_and_big_folder.md` (host behaviour) and
`PLAN_launcher_grid_convergence.md` (why the geometry is solved, not tabulated). The six
values live in the remote preferences (`launcher_big_folder_tune_*`, declared in
`PreferenceKeys.kt`) and are edited from **Settings -> Launcher -> Big folder alignment**,
where six signed sliders appear while that switch is on (range -200..200, step 4). Units
are **launcher-local px**, the same space the `BigFolderAlign ...` log line prints
(`cellW`, `width`, `offsetY`, `previewY`).

Changing a slider takes effect after the launcher scope restarts; the hook also re-reads
the six values on every folder layout, so a relayout can pick them up earlier.

## Six independent knobs

| Knob (key suffix) | Drives | Positive | Negative |
|---|---|---|---|
| `gap_h` | child horizontal gap | wider spacing | tighter spacing |
| `gap_v` | child vertical gap | wider spacing | tighter spacing |
| `bg_x` | background width | narrower (both edges in) | wider |
| `bg_y` | background height | shorter (both edges in) | taller |
| `shift_x` | child grid position | icons right | icons left |
| `shift_y` | child grid position | icons down | icons up |

Each value reaches exactly one quantity: the gaps are solved from the **untuned** box, so
the background insets never move the spacing; the background insets move both of its edges
symmetrically, so they never move the child grid's centre; the shifts move the icons only.

## The solved geometry

```
insetX  = backgroundInsetX(metrics)                       // host widgetPadding by default
baseW   = spanX*cellWidth + (spanX-1)*borderX             // gap reference, untuned
baseH   = (spanY-1)*cellPitchY + iconSize - 2*artInset    // gap reference, untuned
bgW     = baseW - 2*insetX - 2*bg_x                      // written to PreviewBackground.o
bgH     = baseH - 2*bg_y                                 // written to PreviewBackground.previewSizeY
offsetX = insetX + bg_x
offsetY = rowInset + artInset + bg_y
child   = folderIconSizePx * BigFolderConfig.CHILD_ICON_SCALE   // host constant, not ours
gapH    = max(0, (baseW - nH*child) / (nH+1) + gap_h)
gapV    = max(0, (baseH - nV*child) / (nV+1) + gap_v)
centerX = bgW/2 + shift_x                                // passed to getOffsetX
centerY = bgH/2 + hostExtra + shift_y                     // passed to getOffsetY
```

`nH`/`nV` come from the child grid (`CHILD_COLS`/`CHILD_ROWS`, 3 per multi-cell axis).

## Reading the current values off the log

* `hGap(2,2) A -> B (n=3 bg=… child=… delta=…)` — `A` is the host's stock gap, `B` the
  effective one, `delta` the slider value currently applied. Set the gap h slider to
  `target - (B - delta)` to reach a target spacing.
* `BigFolderAlign span=2x2 width A->B offsetX … offsetY … previewY … bgBottom=… cellW=…
  cellH=… rowInset=…` — the background box and the child-grid metrics in one line.
* With any value off zero the log line appends `tune=gapH…,gapV…,bgX…,bgY…,shiftX…,shiftY…`, so a screenshot can be
  tied to exact values.

## Limits

* Gaps clamp at 0; a large negative gap slider collapses the icons onto each other
  but no further.
* The child grid can overflow the background if the background is pushed in (or the shifts
  pushed out) beyond the grid extent; the background does not clip the icons.
* `rowInset`, `artInset`, `cellWidth`, `cellPitchY` come from the host profile, so they move
  with the workspace grid and the square-mode/padding settings; the six knobs do not.

## Other implicit constants worth knowing

* `backgroundAlign` (`BackgroundAlign.HOST` / `SMALL_FOLDER` / `ICON_BOX`) picks the
  horizontal inset source; `HOST` is the host's own `widgetPadding`.
* `ART_INSET_RATIO = 0.11f` is only the fallback art inset when the host reports no
  `widgetPadding`; `artInset` feeds the background top and its height.
* The child icon **size** is the host's `CHILD_ICON_SCALE`; it is not exposed as a slider, change it only via
  `BigFolderConfig` if the icon size itself must move.

## Host values behind those constants

Read from `com.zui.launcher` with JADX, for the preview's benefit:

```
CHILD_ICON_SCALE   = big_folder_child_icon_scale / 10000      (0.8235 stock), then
                     /= DeviceProfile.inv.customIconScale
folderIconSizePx   = round(sqrt(folder_icon_size_scale * iconSizePx^2 * 0.6597222 / PI))
folderIconOffsetYPx= (iconSizePx - folderIconSizePx) / 2
child grid (2x2)   = BigFolderConfig style table: 4 columns x 3 rows
                     (getBigFolderIconChildCount overwrites from the last matching style)
background         = width = cellW * spanX - 2 * widgetPadding.left
                     height = cellH * spanY - widgetPadding.top - widgetPadding.bottom
                     offset = widgetPadding.left / widgetPadding.top
background radius  = R.dimen.big_folder_icon_radius (a fixed dimension, not a ratio)
```

The hook then overrides the background width/offset and the preview mirrors that. Measured on
device (`iconSize=190 folderIcon=158 folderOffsetY=16 widgetPad=40,40`): `folderIconSizePx` is
0.832 of the icon size, `widgetPadding` is 0.21 of it, and `ChildIconScale` is 0.8235. The
preview carries those three ratios in `LauncherPreviewCard.kt`.
