# Big folder: hand tuning the geometry

Companion to `zui_vs_oplus_launcher_grid_and_big_folder.md` (host behaviour) and
`PLAN_launcher_grid_convergence.md` (why the geometry is solved, not tabulated). All knobs
live in `BigFolderAlignHook.kt` and are **launcher-local px**, the same space the
`BigFolderAlign ...` log line prints (`cellW`, `width`, `offsetY`, `previewY`).

## Six independent knobs

| Knob | Drives | Positive | Negative |
|---|---|---|---|
| `TUNE_GAP_H_DELTA` | child horizontal gap | wider spacing | tighter spacing |
| `TUNE_GAP_V_DELTA` | child vertical gap | wider spacing | tighter spacing |
| `TUNE_BG_INSET_X` | background width | narrower (both edges in) | wider |
| `TUNE_BG_INSET_Y` | background height | shorter (both edges in) | taller |
| `TUNE_CHILD_SHIFT_X` | child grid position | icons right | icons left |
| `TUNE_CHILD_SHIFT_Y` | child grid position | icons down | icons up |

Each value reaches exactly one quantity: the gaps are solved from the **untuned** box, so
the background insets never move the spacing; the background insets move both of its edges
symmetrically, so they never move the child grid's centre; the shifts move the icons only.

## The solved geometry

```
insetX  = backgroundInsetX(metrics)                       // host widgetPadding by default
baseW   = spanX*cellWidth + (spanX-1)*borderX             // gap reference, untuned
baseH   = (spanY-1)*cellPitchY + iconSize - 2*artInset    // gap reference, untuned
bgW     = baseW - 2*insetX - 2*TUNE_BG_INSET_X           // written to PreviewBackground.o
bgH     = baseH - 2*TUNE_BG_INSET_Y                      // written to PreviewBackground.previewSizeY
offsetX = insetX + TUNE_BG_INSET_X
offsetY = rowInset + artInset + TUNE_BG_INSET_Y
child   = folderIconSizePx * BigFolderConfig.CHILD_ICON_SCALE   // host constant, not ours
gapH    = max(0, (baseW - nH*child) / (nH+1) + TUNE_GAP_H_DELTA)
gapV    = max(0, (baseH - nV*child) / (nV+1) + TUNE_GAP_V_DELTA)
centerX = bgW/2 + TUNE_CHILD_SHIFT_X                     // passed to getOffsetX
centerY = bgH/2 + hostExtra + TUNE_CHILD_SHIFT_Y         // passed to getOffsetY
```

`nH`/`nV` come from the child grid (`CHILD_COLS`/`CHILD_ROWS`, 3 per multi-cell axis).

## Reading the current values off the log

* `hGap(2,2) A -> B (n=3 bg=… child=… delta=…)` — `A` is the host's stock gap, `B` the
  effective one, `delta` the knob currently applied. Set `TUNE_GAP_H_DELTA` to
  `target - (B - delta)` to reach a target spacing.
* `BigFolderAlign span=2x2 width A->B offsetX … offsetY … previewY … bgBottom=… cellW=…
  cellH=… rowInset=…` — the background box and the child-grid metrics in one line.
* A tuned build appends `tune=gapH…,gapV…,bgX…,bgY…,shiftX…,shiftY…`, so a screenshot can be
  tied to exact values.

## Limits

* Gaps clamp at 0; a large negative `TUNE_GAP_*_DELTA` collapses the icons onto each other
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
* The child icon **size** is the host's `CHILD_ICON_SCALE`; change it only via
  `BigFolderConfig` if the icon size itself must move.
