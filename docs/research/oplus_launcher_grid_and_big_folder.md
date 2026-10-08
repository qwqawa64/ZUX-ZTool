# OPlus launcher: workspace cell size and big-folder geometry

Reverse-engineered with JADX from the OPPO/OnePlus launcher APK (`com.android.launcher3`
base + `com.oplus.*` extensions, build tag `OplusLauncher_OPPOPallDomesticAallRelease`).
This is the reference behaviour ZTool's square-grid and big-folder alignment features
target; the ZUI (`com.zui.launcher`) counterpart lives in
`zui_launcher_wide_grid_geometry.md`.

All grid math moved out of `DeviceProfile` into `com.android.launcher.layoutparam.*`:
one parameter object per UI area, all built in `DeviceProfile.<init>`:

```
ConfigParam(context, inv, info, windowBounds, …)
  -> IconParam        (icon size / text size / drawable padding)
  -> WorkspaceParam   (workspace padding, workspace height)
  -> CellLayoutParam  (cell size, cell-layout padding, border space)
  -> HotseatParam / TaskBarParam / AllAppsParam / FolderParam / PageIndicatorParam
DeviceProfile.mWidgetSizeConfig : SizeSpacingConfig   (per-cell icon/背景 geometry)
```

## 1. What counts as "the grid"

`InvariantDeviceProfile` parses `res/xml/device_profiles.xml` (only two entries in this
APK: `General` = 4 cols x 6 rows with a 3x4 folder and 3 preview columns, `Simple` =
3 cols x 4 rows with a 2x3 folder and 2 preview columns) plus OPlus-injected attributes
(`injectInitGrid`, `injectInitGridForCustomAttr`, `initSupportedProfiles`). The current
grid is stored by name (`getCurrentGridName` / `setCurrentGrid` / `setCurrentGridManual`).

`numColumns` / `numRows` for the profile in use come from `ConfigParam`, which may
transpose them when rotation switches the layout:

```
ConfigParam.<init>:  numColumns = max(inv.cols, inv.rows) if landscape else min(...)   // when rotation-switch is supported
                     numRows    = the other one
```

`ConfigParam.isNewCellSize(c, r)` is true only for `5x7` and `5x9` — those two grids are
treated as a distinct generation of cell design.

## 2. Design cell size (`CellLayoutParam`)

Computed once per profile rebuild (constructor) and on `updateIconSize`:

```
cellLayoutWidth = (baseSize - workspace.getTotalWorkspacePadding(c,r,landscape,expanded).x) / panelCount
                  baseSize = tablet+landscape ? max(widthPx,heightPx)
                           : min(widthPx,heightPx)          (fold screens: bounds dependent)
cellWidthPx  = DeviceProfile.calculateCellWidth(cellLayoutWidth - padL - padR, borderSpace.x, cols)
             = (cellLayoutWidth - padL - padR - (cols-1)*borderSpace.x) / cols
contentHeight = iconTextHeight(cols) + iconDrawablePadding + iconSize
cellHeightPx = max(2 * cellPaddingTopMin(cols) + contentHeight,
                   cellWidthPx + diffCellHeightWithCellWidth(cols, rows, landscape, expanded))
```

* `diffCellHeightWithCellWidth` is `0` for most tablet grids, and a per-grid resource for
  `6x5` (`diffCellHeightWithCellWidthForOldTablet`), `6x4`
  (`diffCellHeightWithCellWidthTablet6X4`), fold-screen grids and the phone grids
  (`3x5`, `3x6`, `4x5`, `5x5`, `5x6`, `4x5`…). It is the only source of intentional
  non-square cells.
* `cellPaddingTopMin` / `cellPaddingTopMin5Cols` (and fold-landscape variants) set the
  minimum air above the icon; `getIconFactor` is `0.6` for 5-column (and two-panel
  landscape) grids and `0.5` otherwise; `iconPaddingBottom = (1 - iconFactor) * contentHeight`.
* Horizontal padding for tablets is `ResourceUtils.pxFromDp(inv.workspacePaddingLRDp, …)`
  (the `General` grid ships `oplusWorkspacePaddingLeftDp=0`); phones use
  `R.integer.oplusLayoutCellLayoutPaddingHor{3,5}colDp` / `…5col9RowDp` / `inv.cellLayoutPadding`.
  Top/bottom padding is `R.dimen.cell_layout_padding_top` / `cell_layout_padding`
  (two-panel variants exist).
* On phones only, `applyPhoneViewportCellHeightCapIfNeeded` clamps
  `cellHeightPx` to `(availableHeight + realNavBar - hotseat - indicator) / rows`.
  Tablets and fold screens reset the cap to `Integer.MAX_VALUE` — i.e. **on tablets the
  cell height is never clamped to the viewport, only the `max(...)` rule above applies.**

## 3. Cell size actually used for layout (`CellLayout.onMeasure`)

`CellLayoutParam`'s numbers are *design* values (they seed workspace height, hotseat and
widget alignment). The size that reaches the icon views is recomputed from the measured
page box, exactly like ZUI:

```
onMeasure(specW, specH):
  boxW = specW - paddingLeft - paddingRight        // padding = deviceProfile.cellLayout().getPadding()
  boxH = specH - paddingTop  - paddingBottom
  if (mFixedCellWidth < 0 || mFixedCellHeight < 0):      // workspace pages keep -1
      cellW = calculateCellWidth(boxW, mBorderSpace.x, mCountX)   // (boxW - (cols-1)*bx) / cols
      cellH = calculateCellHeight(boxH, mBorderSpace.y, mCountY)  // (boxH - (rows-1)*by) / rows
```

`mBorderSpace` is chosen by container type in `resetCellSize`: workspace →
`deviceProfile.cellLayout().getCellLayoutBorderSpacePx()`, hotseat → hotseat border space,
folder → `deviceProfile.folder().getFolderCellLayoutBorderSpacePx()` (from
`inv.folderBorderSpace`), 0 for anything else.

The item box is then built in `CellLayoutLayoutParams#setup`:

```
width  = cellHSpan * cellW + (cellHSpan - 1) * borderSpace.x
height = cellVSpan * cellH + (cellVSpan - 1) * borderSpace.y
x      = cellX * (cellW + borderSpace.x) + leftMargin
y      = cellY * (cellH + borderSpace.y) + topMargin
```

Consequence: an item spanning 2x2 gets `2*cellW + bx` by `2*cellH + by` — the "big
folder" box on the workspace follows from the *measured* cell, not from `cellWidthPx`.

## 4. Icon size is independent of the cell

`IconParam.iconSizePx = LauncherIconConfig.calculateIconSizeByUxDesign(...)`:

* tablet → `R.array.icon_size_tablet`, fold → `icon_size_fold_*`, phone → per-grid arrays
  (`default_icon_size_5_Col`, `default_icon_size_4_Col_6_Row`, …),
* the array is a UX design triple normalised by its maximum (`updateUXDesignSize` builds
  `mScaleUX`), and `LauncherIconConfig.getUXScalar` is `6400 / iconConfig.getIconSize()`
  on tablets,
* text height is measured with `Paint.getFontMetrics()` (`calculateTextHeightIgnoreFontPadding`)
  and collapses to 0 in text-free mode (`mIconTextSizeScale == 0`).

So OPlus does **not** derive the icon from the cell; it lays a fixed-by-design icon into
the cell and lets padding absorb the difference. This is the same axis ZTool's icon-size
hook manipulates.

## 5. Big folder

A folder is a `FolderInfo` with `getMinSpanX/Y = 1` and `getMaxSpanX/Y = 2`
(`IVariableSizeHostViewInfo`); `resetMinWidthAndMinHeightIfNeeded` defaults
`minWidth/minHeight` to 2. `ItemInfo.hasGrid2x2()` is `spanX == 2 && spanY == 2`, and
every "big folder" predicate is gated on it — **the big folder is exactly a 2x2 item.**

```
FolderInfo option bits:
  1  FLAG_WORK_FOLDER         2  FLAG_MULTI_PAGE_ANIMATION   4  FLAG_MANUAL_FOLDER_NAME
  8  BIG_FOLDER_TYPE_3_3      16 BIG_FOLDER_TYPE_2_2         32 BIG_FOLDER_TYPE_HIGHLIGHT
```

Preview grid inside the icon (`getPreviewColumn` / `getPreviewRow`):

| condition | device-side columns x rows |
|---|---|
| simple mode | `IDP.numFolderPreview` (3 for the `General` grid) square |
| `option 16` + 2x2 | 2 x 2 (four-grid look) |
| otherwise | 3 x 3 (unless an abnormal 1-cell span, then 1) |

Counts derived from that grid: `getMaxPreviewIconWithStacked = cols*rows` (9),
`getMaxPreviewIconWithoutStacked = cols*rows - 1` (8 — the last cell is a stack),
`getMaxPreviewChildWithStacked = cols*rows + 3` (12),
`getAllTypeMaxPreviewIconWithStacked = cols*rows - DEL_ICON_NUM` (6) for the highlight
grid. `BigFolderGridOrganizer` hardcodes `mCountX = mCountY = 3` and returns up to
`STACKED_COUNT = 4` items for the stacked cell; `SizeSpacingConfig.DEL_ICON_NUM = 3`.

### 5.1 Interior geometry

`OplusClippedFolderIconLayoutRule.init()` picks `mSizeConfig = DeviceProfile.mWidgetSizeConfig`
(a `SizeSpacingConfig`) and, for `FolderInfo.hasExtendedGrids()` (span > 1), runs
`initForIconWithExtendedGrids`:

```
mBaselineIconSize = SizeSpacingConfig.getPreviewIconSize(cellW, cellH, spanX, spanY, isFourGrid, isFolder=true)
mBaselineIconScale = mBaselineIconSize / bubbleIconSize          // bubbleIconSize == workspace icon px
contentPadding     = SizeSpacingConfig.getContentPadding(...)     // (padX, padY)
mPreviewSubIconGapX = (availableX - 2*padX - cols * mBaselineIconSize) / (cols * 2)
mPreviewSubIconGapY = (availableY - 2*padY - row * mBaselineIconSize) / (row * 2)   // minus text band when the folder name is drawn on the background
```

`getPreviewIconSize` itself works from the two cells the item covers:

```
bgW  = cellWidth * spanX - 2 * getBgHorizontalBySpan(...)
bgH  = cellHeight * spanY - getBgPaddingTopBySpan(...) - getBgPaddingBottomBySpan(...)
box  = (spanX == spanY && spanX != 1) ? min(bgW, bgH) : max(bgW, bgH)
N    = max(getItemRowBySpan(spanX, spanY, isFourGrid), getItemColumnBySpan(...))
       N = 1 for span 1, 2 for 2x2-in-four-grid, else 3
size = (box - getContentPaddingFactor(spanX, spanY, isFourGrid, isFolder) * contentPadding) / N
```

with `getContentPaddingFactor = N*2 * f + 2` where `f` is `0.75` (N=2), `0.6667` (N=3),
`0.38` (rectangle icon shape) or `0.2` (other shapes) for N=1 folders, and
`getBgPaddingTop/Bottom/HorizontalBySpan` derived from `IconUtils.getIconUx(...)`,
`bubbleIconSize`, the measured text height and text-free mode.

Item placement (`computePreviewItemDrawingParams`) then uses
`folderOffset + folderBaseIconSize * (row|col)` with
`folderBaseIconSizeX = 2*gapX + mBaselineIconSize`; the highlight grid
(`option 32`) re-indexes the first items so one icon spans two cells and the last three
cells of page 0 disappear. Stacked pre-icons use
`stackedSubIconSize = mBaselineIconSize/2 - 2*stackedIconPadding`.

Small (span 1) folders take the other branch, `initForDefaultIcon`, with
`mBaselineIconScale = SMALL_FOLDER_ICON_SCALE_FACTOR` (0.35 in simple mode).

### 5.2 Opened folder (page) cell size

Independent of the workspace: `FolderParam.updateAvailableFolderCellDimensions` computes

```
cellW = (widthPx - 2 * pageMarginLRdp) / inv.numFolderColumns        // tablets use widthPx, phones availableWidthPx
cellH = tablet ? R.dimen.folder_cell_fix_height_tablet
               : 2 * folder_content_text_normal_height + iconPaddingTop + folderChildIconSize + drawablePadding
scale = min( (widthPx - 2*margin) / (cellW * numFolderColumns),
             availableHeight / (cellH * numFolderRows + bottomPanel) )
if (scale < 1) re-run cell computation with that scale          // ext hook: needUpdateFolderCellSize()
bottomPanel = folder_pageview_padding_bottom + folder_pageview_padding_top
            + folder_content_wrapper_margin_top + folder_title_height
```

## 6. Where the ZUI and OPlus designs diverge (feeds the ZTool comparison)

| | OPlus (this document) | ZUI (`zui_launcher_wide_grid_geometry.md`) |
|---|---|---|
| design cell size | `CellLayoutParam`, `height = max(2*padTopMin + icon+label, width + diff)` | `updateIconSize()` on `minCellSize * min(widthFit, heightFit)` |
| on-screen cell size | `CellLayout.onMeasure` from the page MeasureSpec, border space by container type | `CellLayout.onMeasure` from the page MeasureSpec |
| vertical space of the grid | cell-layout top/bottom padding + workspace bottom `height - top - mCellLayoutHeightDp` | `cellLayoutPaddingPx` top/bottom |
| side space | tablet: `workspacePaddingLRDp` (grid option) + cell-layout padding | `desiredWorkspaceHorizontalMarginPx` (frozen) vs `cellLayoutPaddingPx` (re-applied) |
| icon size | per-device UX array, not cell-derived | `iconSize` array from `device_profiles.xml` |
| big folder | folder max span 2x2, inner 3x3 (or 2x2 / highlight), preview size solved from the two cell box | no big folder in the same sense |

## 7. Open items

* Numeric values of the dimen/integer resources above are compiled into `resources.arsc`
  (JADX exposes only real files), so they need `aapt2`/on-device reads.
* The caller that applies `DeviceProfile.cellLayout().getPadding()` to each workspace page
  was not traced; `CellLayout.onMeasure` only consumes it.
* `SizeSpacingConfig.ICON_SCALE_FACTOR`'s literal value and the tablet grid-option list
  (`parseAllGridOptions` output on a tablet) are still unverified.
