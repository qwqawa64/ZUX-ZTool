# Big folder: hand tuning the geometry

Companion to `zui_vs_oplus_launcher_grid_and_big_folder.md` (host behaviour) and
`PLAN_launcher_grid_convergence.md` (why the geometry is solved, not tabulated). All the
knobs below live in `BigFolderAlignHook.kt`; values are **launcher-local pixels**, the same
space the `BigFolderAlign ...` log line prints (`cellW`, `width`, `offsetY`, `previewY`).

## The solved geometry

```
insetX   = backgroundInsetX(metrics) + TUNE_INSET_X_EXTRA        // host widgetPadding by default
topY     = rowInset + artInset + TUNE_OFFSET_Y_EXTRA
bgW      = spanX*cellWidth + (spanX-1)*borderX - 2*insetX
bgH      = (spanY-1)*cellPitchY + iconSize - 2*artInset + TUNE_HEIGHT_EXTRA
childN   = 3 per multi-cell axis (CHILD_COLS/CHILD_ROWS)
child    = folderIconSizePx * BigFolderConfig.CHILD_ICON_SCALE   // host constant
gapH     = max(0, (bgW - nH*child) / (nH+1) * TUNE_GAP_H_SCALE)
gapV     = max(0, (bgH - nV*child) / (nV+1) * TUNE_GAP_V_SCALE)
```

The host draws the child grid **centred inside the background** (`getOffsetX/getOffsetY`
receive the background centre and the gap), which is what makes the tuning below
predictable:

| Change | Background | Child grid |
|---|---|---|
| `TUNE_INSET_X_EXTRA` +1 | both edges move in by 1 | spacing shrinks, tighter and narrower |
| `TUNE_HEIGHT_EXTRA` +2k | grows by 2k **downwards** (top edge fixed) | moves down by k, `gapV` grows by 2k/(n+1) |
| `TUNE_OFFSET_Y_EXTRA` +k | moves down by k | moves down by k, sizes and gaps unchanged |
| `TUNE_GAP_H_SCALE` < 1 | unchanged | horizontal spacing tightens (outer margins grow instead) |
| `TUNE_GAP_V_SCALE` > 1 | unchanged | vertical spacing widens (outer margins shrink) |

## Matching the reported symptoms

| Observation | Knob | Direction |
|---|---|---|
| child horizontal spacing not tight enough | `TUNE_GAP_H_SCALE` (spacing only) or `TUNE_INSET_X_EXTRA` (spacing + background) | `< 1` / `> 0` |
| child grid should sit lower | `TUNE_OFFSET_Y_EXTRA` (group) or `TUNE_HEIGHT_EXTRA` (lower + spacing) | `> 0` |
| background not narrowed enough horizontally | `TUNE_INSET_X_EXTRA` | `> 0` |
| background too short vertically | `TUNE_HEIGHT_EXTRA` (or raise `artInset` less) | `> 0` |
| background taller, same child position | `TUNE_HEIGHT_EXTRA = 2k` with `TUNE_OFFSET_Y_EXTRA = -k` | see table above |

Guards: the child grid is clamped at `gap >= 0`, so pushing `TUNE_INSET_X_EXTRA` or a
negative `TUNE_HEIGHT_EXTRA` too far makes the child grid overflow the background instead
of shrinking further.

## Other implicit constants worth knowing

* `backgroundAlign` (`BackgroundAlign.HOST` / `SMALL_FOLDER` / `ICON_BOX`) picks the
  horizontal inset source; `HOST` is the host's own `widgetPadding`.
* `ART_INSET_RATIO = 0.11f` is only the fallback art inset when the host reports no
  `widgetPadding`; `artInset` feeds both the background top and its height.
* `CHILD_COLS` / `CHILD_ROWS = 3` set the child grid per multi-cell axis (C6).
* The child icon **size** is the host's `BigFolderConfig.CHILD_ICON_SCALE`, not ours.
* `DeviceProfile.rowInset` and `artInset` come from the host profile, so they move when the
  workspace grid changes; the tuning values above are added on top and stay constant.

A tuned build marks its log line with
`tune=insetX…,height…,offsetY…,gapH…,gapV…`, so a screenshot can be tied to exact values.
