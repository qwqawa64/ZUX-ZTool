# ZUI launcher wide grid: the geometry that matters

Verified against `com.zui.launcher` (Lenovo ZUX OS) with JADX. This is the host behaviour
`LauncherWideGridHook` depends on; it is why the hook rewrites `cellLayoutPaddingPx` and
not the workspace margin.

## 1. Who applies the side padding

```
Workspace#setInsets(Rect)                     // Insettable callback, called on insets change
  -> setPadding(deviceProfile.workspacePadding.left/top/right/bottom)
  -> H1() -> k1(Rect, CellLayout) -> cellLayout.setPadding(deviceProfile.cellLayoutPaddingPx)
```

`Workspace#setInsets` never re-derives `workspacePadding`; that Rect is built once by
`DeviceProfile#N()` from `desiredWorkspaceHorizontalMarginPx` (plus top/bottom paddings)
at profile construction. A runtime write to `desiredWorkspaceHorizontalMarginPx` is
therefore invisible until a profile rebuild, while a write to `cellLayoutPaddingPx` is
re-applied by the `H1()` pass inside every `setInsets` call.

## 2. Who decides the on-screen cell size

```
PagedView#onMeasure:  child width  = x(size) = ((size - insets - padL - padR) / panels) + padL + padR
                      child height = size2 - mInsets.top - mInsets.bottom
CellLayout#onMeasure: cellWidth  = calculateCellWidth(specW - paddingLeft - paddingRight, border.x, mCountX)
                      cellHeight = calculateCellHeight(specH - paddingTop - paddingBottom, border.y, mCountY)
```

* For one panel the workspace's own padding cancels out of the page width, so the page is
  `availableWidthPx` wide and `availableHeightPx` tall (`WindowBounds.availableSize =
  bounds - insets`).
* Workspace pages keep `f2158c/f2159d < 0`, so `onMeasure` recomputes both cell dimensions
  from the measured page size every layout (only `setCellDimensions` callers —
  `FolderPagedView`, `ZuiHotseat` — freeze them; `resetCellSize` resets them to -1).
* The page height is *not* reduced by the workspace padding, so `cellLayoutPaddingPx.top`
  and `.bottom` carry the search-bar/dock space of the vertical grid.

## 3. Why `DeviceProfile.cellWidthPx` is not the on-screen cell width

`cellWidthPx`/`cellHeightPx` are written only by `updateIconSize()`, called during
construction from `I(Context)` and later by profile rebuilds or icon-scale updates:

* the constructor leaves `cellLayoutPaddingPx = new Rect()` (all zero) and calls
  `I(context)` before assigning the real `cellLayoutPaddingPx` (from
  `Utilities.getCellLayoutPaddingVertical` / `0.08 * max(w,h) - margin`),
* for a scalable grid `updateIconSize` sets `cellWidthPx = minCellSize.x * min(widthFit,
  heightFit)` where `widthFit = availableWidthPx / (cols * cellWidth + borders + 2*margin)`,
  i.e. the value is consistent with a total side padding of `margin`, never with
  `cellLayoutPaddingPx`,
* `Workspace#setInsets` does not call it, so rewriting the padding at runtime leaves both
  fields frozen.

Read `CellLayout#getCountX/getCountY` and the page's measured size instead.

## 4. Consequence for the old square solve

The old solve `T = margin + cols * (cellWidthPx - cellHeightPx) / 2` used frozen inputs,
and `applySideInset` wrote the result into both the margin and the cell layout padding.
With `C = cellWidthPx`, `H = cellHeightPx`, `M = margin` and the reconstruction
`availableWidth = cols * C + borders + 2 * M`, every pass produced

```
T' = M + cols * (C - H) / 2 = T + cols * (C - H) / 2
```

a constant non-zero step, so the padding drifted instead of converging.

## 5. Working solve

`cellWidth` falls by `2px` per `1px` of side padding, `cellHeight` does not depend on the
side padding, so one pass is exact and idempotent:

```
side' = side + cols * (liveCellWidth - liveCellHeight) / 2
```

`liveCellWidth/liveCellHeight` are derived exactly like `CellLayout#onMeasure` from the
page's measured size, the page's current padding, `cellLayoutBorderSpacePx` and the page's
cell counts. Window insets are dispatched before the first measure, so the first pass may
have no measured page: it falls back to `availableWidthPx/availableHeightPx` and a
`postOnAnimation` re-check re-solves from live geometry once the layout has run.

## 6. Runtime field notes

`WideGrid: square solve|recheck …` logs the whole decision:

```
live=317x188 calc=317x188 page=2590x1618 pad=216,140,216,180 cols=8 rows=6 \
border=12,8 profile=216/216 side=216->732
```

* `live` — `CellLayout#getCellWidth/getCellHeight`, the size the page last laid out with.
* `calc` — the same size derived from `page` minus `pad` and `border`.
* `pad` vs `profile` — the page's actual padding versus `DeviceProfile.cellLayoutPaddingPx`.
  If they disagree, `Workspace#H1()` did not carry the Rect and the hook sets the page
  padding directly (logged as "pages did not carry cellLayoutPaddingPx").
* A `live` that stays constant while `calc` follows the padding means the cell size is
  pinned somewhere other than this path; the re-check then logs
  "square mode gave up, cell size does not follow padding" and stops instead of drifting.

