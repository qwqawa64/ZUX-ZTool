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

The cell size that reaches the icon views is the one the page hands to its container, so
the solve runs *inside* `CellLayout#onMeasure`, where the page box is the MeasureSpec the
launcher passed in:

```
byHeight = (boxHeight - paddingTop - paddingBottom - borderY * (rows - 1)) / rows
byWidth  = (boxWidth - borderX * (cols - 1)) / cols          // with zero side padding
side     = min(byHeight, byWidth)                            // square cell, fits both axes
padding  = (boxWidth - (cols * side + borderX * (cols - 1))) / 2
```

`side` goes to `ShortcutAndWidgetContainer#setCellDimensions`, `padding` to the page (and
to `DeviceProfile.cellLayoutPaddingPx`). `PagedView#getChildWidth` cancels the page padding
for a single panel, so the box does not move when the padding changes — the pass converges
after one relayout and is then a no-op.

Two traps make this the only workable place:

* `CellLayout#onMeasure` calls `setCellDimensions` **before** `setMeasuredDimension`, so
  `page.getMeasuredWidth()` is still 0 (or stale) at that moment; the MeasureSpec is the
  only trustworthy page box.
* The container is seeded in `CellLayout`'s constructor with
  `setCellDimensions(f2156a=-1, f2157b=-1, …)` while the container still has no parent, so
  a container-level hook sees a detached view and never the real geometry.

## 6. The icon box is the container's cell size

`ShortcutAndWidgetContainer#measureChild` measures every workspace item with
`lp.width x lp.height`, and `CellLayoutLayoutParams#setup` derives those from the
container's cell size (`mCellWidth/mCellHeight`). Measured on device with a 10x6 grid:
`DoubleShadowBubbleTextView 294x232 lp=294x232 locked=true margin=0,0,0,0`, i.e. the box a
layout inspector reports is exactly the container cell size, not the pitch the page box
implies (`(3112 - 2*86)/10 = 294`, `(1482 - 90)/6 = 232`).

## 7. Runtime field notes

`WideGrid: page measure …` logs the decision:

```
page measure box=3112x1482 pad=86->396 side=232 cols=10 rows=6 border=0,0 \
children=[DoubleShadowBubbleTextView 294x232 lp=294x232 locked=true margin=0,0,0,0 pad=23,0,23,0]
```

* `box` — the page's MeasureSpec, i.e. the box the launcher measured the page with.
* `pad A->B` — the page padding before and after the solve.
* `side` — the square cell size handed to the container.
* `children=` — the first two grid items as they were actually measured (box, layout
  params, `isLockedToGrid`, margins, padding).

