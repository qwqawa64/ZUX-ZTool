# ZUX launcher vs OPlus launcher: workspace cell size and big folder

Companion to `oplus_launcher_grid_and_big_folder.md` (OPlus side) and
`zui_launcher_wide_grid_geometry.md` (ZUI wide-grid/insets). ZUI facts below were read from
`com.zui.launcher` **18.1.9** (versionCode 18100900, compileSdk 35); single-letter members
are named as JADX prints them.

## 1. Workspace cell size — design values

| | ZUI (`com.zui.launcher`) | OPlus |
|---|---|---|
| entry point | `DeviceProfile.updateIconSize(float scale, Context)` | `CellLayoutParam` ctor (+ `applyPhoneViewportCellHeightCapIfNeeded`) |
| layout params | `DeviceProfile` fields directly (`cellWidthPx`, `cellHeightPx`, `cellLayoutPaddingPx`, `cellLayoutBorderSpacePx`, `cellYPaddingPx`) | `ConfigParam` -> `IconParam` / `WorkspaceParam` / `CellLayoutParam` objects |
| paths | 3: (A) responsive spec, (B) scalable `minCellSize`, (C) legacy content-derived | 1 formula, tablet/phone/fold variants inside it |
| width | (A) `f2247g.getCellSizePx()`; (B) `pxFromDp(inv.minCellSize.x, scale)`; (C) `iconDrawablePadding + iconSize` | `(cellLayoutWidth - padL - padR - (cols-1)*borderX) / cols` |
| height | (A) `f2248h.getCellSizePx()`; (B) `pxFromDp(inv.minCellSize.y, scale)`; (C) `ceil(iconSize*1.125) + padding + textHeight` | `max(2*cellPaddingTopMin + contentHeight, cellWidth + diffCellHeightWithCellWidth)` |
| fit rule | width/height shrink in three steps: push border space, then raise the cell, then shrink icon/text/padding; leftover height becomes `cellYPaddingPx = max(0, (cellHeight - content)/2)` (content vertically centred) | height is never below the icon+label block (first term of `max`) and never below `cellWidth + diff`; icon padding bottom is `(1 - iconFactor) * contentHeight` |
| cell layout box | `getCellLayoutWidth() = (availableWidthPx - workspacePadding.x) / panelCount`, `getCellLayoutHeight() = availableHeightPx - workspacePadding.y` | `(baseSize - workspacePadding.x) / panelCount`, same shape (tablet landscape uses `max(w,h)`) |
| icon size | `iconImageSize` dp x `scale` x `inv.customIconScale` (responsive path uses `CalculatedCellSpec`, `matchWorkspace` inherits the workspace values) | `LauncherIconConfig` per-device UX array, independent of the cell |
| side space | `desiredWorkspaceHorizontalMarginPx` (`...Original * scale`) -> `workspacePadding.left/right`; bottom = hotseat + page indicator + nav; the whole rect is built once in `DeviceProfile.N()` | `cellLayoutPadding` left/right (`workspacePaddingLRDp` on tablets) + workspace padding top/bottom |
| odd cases | `N()` zeroes `cellLayoutPaddingPx.bottom` for the stock 7x5 grid; `y()` splits padding between page padding and workspace padding on the scalable path | `ConfigParam.isNewCellSize` marks 5x7 / 5x9 as a separate design generation; tablet `diff` only for 6x5 / 6x4 |

(A) is AOSP's `com.android.launcher3.responsive` system: a `ResponsiveSpec` per axis is
resolved from `@xml/spec_*` resources referenced by the grid option
(`workspaceSpecsId`, `workspaceCellSpecsId`, `folderSpecsId`, ...; only
`fixed_landscape_mode` carries them in this APK's `res/xml/device_profiles.xml`), then
`CalculatedResponsiveSpec.a()` hands the **remainder space** back to
padding/gutter/cell. (B) is the classic scalable grid (`isScalable="true"` options).
(C) is the legacy fixed grid where the cell is derived from the icon content.

## 2. Workspace cell size — what the icons actually get

Both hosts are identical here:

```
CellLayout.onMeasure(specW, specH):
    boxW = specW - paddingLeft - paddingRight       // page padding = DeviceProfile.cellLayoutPaddingPx
    boxH = specH - paddingTop  - paddingBottom
    cellW = (boxW - (cols-1)*borderX) / cols        // DeviceProfile.calculateCellWidth
    cellH = (boxH - (rows-1)*borderY) / rows
    ShortcutAndWidgetContainer.setCellDimensions(cellW, cellH, cols, rows, borderSpace)

CellLayoutLayoutParams.setup(...):
    width  = cellHSpan * cellW + (cellHSpan-1) * borderX
    height = cellVSpan * cellH + (cellVSpan-1) * borderY
    x      = cellX * (cellW + borderX) + leftMargin
    y      = cellY * (cellH + borderY) + topMargin
```

So in both launchers the *measured* cell wins over the design value, and a 2x2 item (big
folder) gets `2*cellW + borderX` by `2*cellH + borderY`. ZUI re-applies
`cellLayoutPaddingPx` to every page from `Workspace#setInsets` (see
`zui_launcher_wide_grid_geometry.md`); OPlus consumes
`DeviceProfile.cellLayout().getPadding()` in the same measure pass.

## 3. Big folder

| | ZUI | OPlus |
|---|---|---|
| definition | `BigFolderConfig.isBigFolder(spanX, spanY)` = `spanX > 1 \|\| spanY > 1` | `FolderInfo.hasGrid2x2()` = `spanX == 2 && spanY == 2` (max span 2) |
| spans | default 2x2, max 3x3; layout styles `(1,2) (2,1) (2,2) (3,2) (3,3)`, enforced by `AppWidgetResizeFrame` via `isSupportLayoutStyle` | folders may only be 1x1 or 2x2 (`getMaxSpanX/Y = 2`) |
| inner grid | per-workspace-grid style table `BigFolderConfig.big_folder_<cols>x<rows>_style<N>_{landscape,portrait}` = int list `(spanX, spanY, childCols, childRows, hGapDp, vGapDp)`; the matching row for the span supplies the child grid **and** the gaps | `FolderInfo` option bits: `8` = 3x3 preview, `16` = 2x2, `32` = highlight grid (9-3); `BigFolderGridOrganizer` hardcodes 3x3 with the 9th cell stacking 4 items |
| child icon size | `iconSize * BigFolderConfig.CHILD_ICON_SCALE / inv.customIconScale`; `CHILD_ICON_SCALE` = `big_folder_child_icon_scale / 10000`, with a per-grid override for 7x5 and a learning-mode variant | solved per item: `getPreviewIconSize(cellW, cellH, spanX, spanY, fourGrid, folder)` = `(box - contentPaddingFactor*padding) / N` with `N = 1/2/3` |
| gap | read from the style table (dp), used by the centered layout `getOffsetX/getOffsetY`; the phone path `d()` is a hardcoded 4-column arrangement | solved: `gap = (available - 2*padding - n*childSize) / (2n)` |
| background/box | `PreviewBackground.setup`: `width = cellW*spanX - 2*widgetPadding.left`, `height = cellH*spanY - widgetPadding.top - widgetPadding.bottom` (legacy height variant adds `max(0,(cellH-cellHeightPx)/2) + folderIconOffsetYPx + folderIconSizePx`); offset = `widgetPadding.left/top`; big-folder corner radius = `R.dimen.big_folder_icon_radius` | no separate background view; the icon tile is the span box, corner/backdrop handled by the icon renderer |
| small folder | `width = folderIconSizePx` (normalized circle size), `x = (pageWidth - width)/2`, child scale `(availableWidth/iconSize) * 0.2` | preview grid 3x3 (or 2x2 four-grid), child size solved from the single cell box |
| label | `FolderIcon.z` sets the label `topMargin`: small folder `iconSize + drawablePadding`; big folder `(spanY-1)*(cellH+borderY) + max(0,(cellH-cellHeightPx)/2) + iconSize + drawablePadding` | text height is part of `contentHeight` inside the cell; no separate label offset |
| opened folder | `DeviceProfile.J/K` shrink-fit: `scale = min((availW - padX)/(cols*cellW + (cols-1)*borderX + 2*contentPadding), (availH - padY)/(rows*cellH + (rows-1)*borderY + footer + contentPadding))` | `FolderParam.updateAvailableFolderCellDimensions`, tablet height fixed by `folder_cell_fix_height_tablet` |

## 4. How ZTool's two hooks line up

`LauncherWideGridHook` (square grid / side inset) rewrites the **actual** cell in
`CellLayout.onMeasure`: `side = min(byHeight, byWidth)`,
`padding = (boxW - (cols*side + borderX*(cols-1))) / 2`, then writes page padding,
`DeviceProfile.cellLayoutPaddingPx.left/right` and
`ShortcutAndWidgetContainer.setCellDimensions`.

* Neither host forces square cells. ZUI guarantees `height >= contentHeight` and then
  centres the content with `cellYPaddingPx`; OPlus guarantees
  `height >= max(content, width + diff)`. So for ZUI a square cell mainly changes the
  **horizontal** pitch; the icon's vertical placement is still driven by `cellYPaddingPx`,
  which the hook does not recompute.
* Because the hook writes `cellLayoutPaddingPx` (also read by `PreviewBackground.setup`,
  `isUpdatePreviewSize`, the folder scale math and `DeviceProfile.F()`), a changed side
  inset propagates into the big folder background width — the coupling the OPlus side
  expresses through `CellLayoutParam` is spread over several ZUI call sites.

`BigFolderAlignHook` normalises a 2x2 big folder: child grid -> 3x3, gaps solved from
the same background box the background uses (`gap = (bgAxis - n*childSize)/(n+1)`, so the
two outer margins equal the inner gaps), background width ->
`spanX*cellW + (spanX-1)*borderX - 2*inset` with
`inset = (cellW - folderIconSizePx)/2` (small-folder circle), background
`offsetY/previewSizeY` from `cellPitchY` plus the host's own `widgetPadding` art inset,
the label `topMargin` from the same pitch, and the phone path `c()` rerouted to the
generic `getOffsetX/getOffsetY`. All of them read `LauncherGridMetrics` (the page's
measured cell), so square mode and the big folder can no longer disagree.

* ZUI's stock design takes the child size and the gaps from the per-grid style table and
  lets them be constants; the hook instead keeps `CHILD_ICON_SCALE` and solves the gaps
  from the background, which reproduces OPlus's "solve the grid from the box" idea while
  leaving the child icon size stock.
* Stock `CHILD_ICON_SCALE` is a per-workspace-grid constant (and is divided by
  `inv.customIconScale`, i.e. the icon-size feature), whereas OPlus solves the child size
  from the two cells. With ZTool's custom grids the stock table has no entry — `BigFolderConfig.init()`
  falls back to the 6x4 table in its `else` branch, which is exactly the "big folder looks
  different on every grid" symptom the hook is fixing.
* The art inset is now `DeviceProfile.widgetPadding` (the value the host itself uses in
  `PreviewBackground.setup` and `computeBigFolderAvaliableWh`); the previous measured
  constant (`ART_INSET_RATIO = 0.11`) survives only as a fallback for platforms that
  report no widget padding.

## 5. Consistency across grid layouts

* ZUI keys every big-folder quantity by `(inv.numColumns, inv.numRows)` at
  `BigFolderConfig.init()` time and has no entry for arbitrary grids, while OPlus derives
  child geometry from the measured box and is grid-agnostic. For ZTool the cheapest route to
  "same look on every grid" is the OPlus route (compute from cell box + span), optionally
  keeping ZUI's style table when an entry exists.
* ZUI's `updateIconSize` clamps cell/icon/border when the grid changes; a hook that only
  pushes `setCellDimensions` can leave `cellHeightPx`/`cellYPaddingPx` stale, so the icon
  may sit off-centre (or exceed the cell) on grids where the square side is much smaller
  than the stock cell height. The existing telemetry logs (`cellHeightPx`, `iconSizePx`,
  `cellW`, `gap`, `widgetPad`) are enough to check this on device.

## 7. Measured 10x6 tablet geometry (2590x1619 screenshots, TB710FU)

Read per pixel from a ZUX screenshot (10x6, CustomGridSize, square mode on) and a ColorOS
reference (10x6):

| | ZUX + square hook | ColorOS |
|---|---|---|
| icon plate | 122 px | ~112 px |
| column pitch | 184 px | ~212 px |
| row pitch | 184 px (forced equal to the column pitch) | ~181 px |
| grid width | 1840 px (71 % of 2590) | 2120 px (82 %) |
| side margin | ~375 px | ~235 px |
| label gap under icon | ~30 px | ~25-30 px |

The available grid height (1104 px over 6 rows) allows a square side of only 184 px, while
the width budget is 259 px per column: **square cells and ColorOS's width fill cannot both
hold on a 16:10 tablet at 6 rows**.

### 7.1 What the icon box actually is

On workspace pages the icon box is the cell `CellLayout.onMeasure` hands to
`ShortcutAndWidgetContainer.setCellDimensions`, i.e. `CellLayout.getCellWidth/Height`:
the cell is **not** frozen for the workspace (`CellLayout.setCellDimensions(int,int)`, the
freezing setter, is only called by `FolderPagedView` and `ZuiHotseat`, and `setFixedSize`
only by `Folder`/`FolderPagedView`). A hook that re-derives the cell from the raw
MeasureSpec therefore disagrees with the launcher whenever the page has padding: on the
6x4 report the raw-box arithmetic gave 520 px while the real cell was 448 px.

`LauncherWideGridHook` now reads `CellLayout.getCellWidth/getCellHeight` for the decision
and squares the cell only when that cell is clearly wide (width/height above
`squareAspectThreshold = 1.2`; measured 10x6 ~1.16 -> kept per-axis, 6x4 ~1.29 -> squared).
The square side and the centring padding are taken from the height budget and the raw box
respectively, so the pass converges instead of drifting.

## 8. Host crash: big-folder blur on long-press

`FolderIcon.getBlurAreaData()` calls `FolderIcon.A()`, which dereferences the drag object
whenever the icon's own drag flag is set:

```java
if (this.S) {                                    // icon-side "dragging" flag
    DragController dc = launcher.getDragController();
    if (dc.getDragObject().dragView != null) {   // NPE when no drag object exists yet
```

`DragController.getDragObject()` is still null while the workspace builds the drag preview
(`beginDragShared` -> `DragPreviewProvider.createDrawable` -> `getSourceVisualDragBounds` ->
`FolderIcon.getPreviewBounds` -> `PreviewItemManager.recomputePreviewDrawingParams` ->
`updateBgBlur` -> `Launcher.updateAreaBlur` -> `getBlurAreaData`), so long-pressing a big
folder with dynamic blur enabled can crash the launcher. `BigFolderAlignHook` guards it: `FolderIcon.S` is cleared for the duration of `A()` when
the controller has no drag object (the idle branch computes the blur rect from the icon's
own bounds instead), and any remaining null-pointer failure inside that cosmetic update is
suppressed and logged. The guard now lives in its own module
(`LauncherBigFolderBlurGuardHook`, key `launcher_big_folder_blur_guard`, default on) so it
also protects users who do not enable the big-folder alignment feature.

## 9. Child grids and background insets (ZTool implementation)

* The child grid is derived from the span for every big-folder layout
  (`3 x 3`; a capsule folder's single axis stays 1) rather than only for 2x2, because the
  host's per-grid style table has no entry for custom workspace grids.
* The background's horizontal inset defaults to the host's own `widgetPadding`, and
  `backgroundWidth` / `backgroundHeight` are shared by the background write and the child
  gap solve, so the grid stays centred in exactly the box the background covers.

## 10. Open items

* Which ZUI cell branch is live on the tablet (`f2246f` responsive spec vs `f2244d`
  scalable vs legacy) — readable from the `DeviceProfile.updateIconSize` telemetry line.
* Numeric values of `big_folder_*_style*` strings and `big_folder_child_icon_scale`
  (compiled into `resources.arsc`; ZTool currently learns the child grids at runtime).
* OPlus page-padding caller (see the OPlus document).
