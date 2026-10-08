# PLAN: converge `LauncherWideGridHook` and `BigFolderAlignHook` on one measured-cell model

AGENT-oriented implementation plan. Background:
`docs/research/oplus_launcher_grid_and_big_folder.md`,
`docs/research/zui_vs_oplus_launcher_grid_and_big_folder.md`,
`docs/research/zui_launcher_wide_grid_geometry.md`.

## Status

Implemented: **A, B3, B4, C1, C2, C3, C4, C6, C10, C10b**, plus the ColorOS parity
side-margin preset (frontend). Remaining: B1, B2, B5, B6, B7, C5, C7, C8, C9.

Implementation notes:
- A lives in `hook/modules/launcher/grid/LauncherGridMetrics.kt`
  (`install` / `pageOf` / `fromPage` / `publish` / `contentHeightPx` / `cellYPaddingPx` /
  `writeCellYPaddingPx`), with memoized `Utilities.calculateTextHeight`.
- C2: `backgroundInsetX` defaults to the host's `widgetPadding` (fallback the measured
  insets); `backgroundWidth` / `backgroundHeight` are the single source for the background
  and for the child-gap axis, so the two cannot drift apart.
- C6: the child grid is derived from the span for every big-folder layout
  (`3 x 3`, single axis kept at 1 for capsule folders) instead of matching only 2x2.
- C10b: the blur guard moved to `hook/modules/launcher/grid/LauncherBigFolderBlurGuardHook.kt`
  with its own key (`launcher_big_folder_blur_guard`, **default on**) and a settings switch,
  so it no longer depends on the big-folder alignment feature.
- Parity preset: `LauncherSettingsRepository.paritySideInsetDp()` writes 9 % of the screen
  width (the measured ColorOS margin) into the side-inset key; the slider upper bound was
  raised from 16 dp to 160 dp to make that reachable.
- Decision after the 10x6 screenshot comparison (see
  `docs/research/zui_vs_oplus_launcher_grid_and_big_folder.md` section 7): square mode is
  an auto trade-off driven by the **cell the page handed to its container**
  (`CellLayout.getCellWidth/getCellHeight`). The solve runs when that cell's width/height
  exceeds `squareAspectThreshold` (1.2): 10x6 measures ~1.16 and keeps the host's per-axis
  (ColorOS-like) cells, 6x4 measures ~1.29 and is squared. A forced square on a mild
  rectangle wastes `cellWidth - cellHeight` per column (10x6: 1840 px vs 2120 px grid
  width), which is what the threshold avoids. Square mode requires a launcher restart, so
  the padding it writes never leaks into another grid.
- C4 uses `gap = (bgAxis - n*childSize) / (n + 1)` instead of the planned `/(n - 1)`: the
  centered host layout puts the leftover into the two outer margins, and `n + 1` makes
  those margins equal to the inner gaps, so the child grid never touches the background
  edge.
- C3/C4 also read the horizontal background extent through `cellPitchX`
  (`cellW + borderX`), matching the background width the hook writes.

## Goal

Both launcher hooks must derive geometry from the same **live measured cell** (the one
`CellLayout.onMeasure` hands to `ShortcutAndWidgetContainer`) instead of `DeviceProfile`
design values and empirical constants, and square mode must stop leaving ZUI's
profile-frozen content centring stale.

## Host facts that drive the plan

1. `ShortcutAndWidgetContainer.measureChild` sets the child's `paddingTop` to
   `dp.cellYPaddingPx` **when that field is >= 0 and the container is the workspace**
   (`f2973b == 0`); only when it is < 0 does it fall back to
   `max(0, (lp.height - min(containerMeasuredHeight, dp.getCellContentHeight(0))) / 2)`.
   Big folders are forced to `paddingTop = 0`.
   `DeviceProfile()` initialises `cellYPaddingPx = -1`; the responsive (`f2246f`) and
   scalable (`f2244d`) branches of `updateIconSize` overwrite it with a constant.
2. The design cell (`DeviceProfile.getCellLayoutWidth/Height` + `minCellSize`/specs) and
   the measured cell (`CellLayout.onMeasure`: `(spec - padding - (n-1)*border)/n`) diverge
   as soon as custom grids, the side inset or square mode are active.
3. ZUI's big-folder background is computed from the **design** cell and `widgetPadding`
   (`PreviewBackground.setup`), not from the icon view's measured box, so it can disagree
   with the cells the child icons actually occupy.
4. `BigFolderConfig` child grids/gaps come from per-workspace-grid string tables whose
   `else` branch falls back to the 6x4 table; `CHILD_ICON_SCALE` is per-grid.
5. OPlus reference model: `cellHeight = max(contentHeight, cellWidth + diff)`; the big
   folder's child size and gaps are solved from the span box.
6. `DeviceProfile.f2243c` is `com.android.launcher3.util.IconSizeSteps`
   (`getIconSmallerThan(int)`, `getNextLowerIconSize(int)`, `minimumIconSize()`).

## A. Shared metrics object (new)

- A1. New file `app/src/main/java/com/qimian233/ztool/hook/modules/launcher/grid/LauncherGridMetrics.kt`.
  Resolve once at install: `CellLayout.getCellWidth/getCellHeight/getCountX/getCountY`,
  `ShortcutAndWidgetContainer.setCellDimensions`, `DeviceProfile.cellLayoutPaddingPx`,
  `cellLayoutBorderSpacePx`, `cellYPaddingPx`, `iconSizePx`, `iconDrawablePaddingPx`,
  `folderIconSizePx`, `widgetPadding`, `folderIconOffsetYPx`, `inv.numColumns/numRows`,
  `IconSizeSteps` (`f2243c`), `Utilities.calculateTextHeight(int)`.
- A2. `measure(page: View?): Metrics` prefers the page's own values
  (`getCellWidth/getCellHeight`, `getCountX/getCountY`) and falls back to the design math
  (`(getCellLayoutWidth - 2*pad)/cols`, `(getCellLayoutHeight - padTop - padBottom)/rows`).
  `Metrics = (cellW, cellH, borderX, borderY, cols, rows, boxW, boxH, padLeft, padTop)`
  plus derived `contentHeight = iconSizePx + iconDrawablePaddingPx + textHeight`.
- A3. Single process-wide cache (`ConcurrentHashMap` keyed by nothing; just volatile
  fields) refreshed from: the page solve (B), `PreviewBackground.setup` (C),
  `DeviceProfile.updateIconSize` (both hooks). Both hooks read only this cache.

## B. `LauncherWideGridHook` (square grid / side inset)

- B1. Keep the solve inside `CellLayout.onMeasure` (the `MeasureSpec` is the only
  trustworthy page box). No change to the entry point.
- B2. Apply the OPlus content floor: `side = min(byHeight, byWidth)`; if
  `side < contentHeight`, use `side` for the cell **width** but keep
  `cellH = contentHeight` (so a dense grid degrades to OPlus's "taller than wide" instead
  of clipping the label), and log it once per geometry.
- B3. Re-centre content after changing the cell: write
  `dp.cellYPaddingPx = max(0, (cellH - contentHeight)/2)` (host semantics of the responsive
  / scalable branches) or `-1` to let `measureChild` recompute from the measured height.
  `-1` is preferred when `cellH` may still move within the pass.
- B4. Hoist the `dp.cellLayoutPaddingPx.left/right` write out of the
  `padStale || newTarget` block (keep the idempotent `rect != padding` check) so a profile
  rebuild cannot leave page padding and profile padding disagreeing.
- B5. Icon shrink is telemetry-only by default: compute
  `steps.getIconSmallerThan(side - textHeight - drawablePadding)` and log the delta; writing
  `dp.iconSizePx` would collide with the icon-size feature and needs a model rebind, so keep
  it behind an explicit switch if it is ever wanted.
- B6. Mode ownership stays explicit: square mode owns page padding +
  `cellLayoutPaddingPx.left/right`; the inset slider owns
  `desiredWorkspaceHorizontalMarginPx` + the same padding. Keep the current early return in
  the `Workspace#setInsets` hook and log which owner is active.
- B7. Re-apply on profile change: hook `DeviceProfile.updateIconSize(float, Context)`,
  clear `lastSquarePad/lastSquareSide` (or key them by the `DeviceProfile` instance) and
  `requestLayout()` the pages, so a grid/config change re-solves instead of reusing a stale
  `side`.

## C. `BigFolderAlignHook`

- C1. Replace `readGridMetrics`' design math with the shared metrics (A2); keep the
  `nominalW`/`cellW` telemetry fields to detect design != actual.
- C2. Background width = `spanX*cellW + (spanX-1)*borderX - 2*inset`, with `inset` =
  `dp.widgetPadding.left` by default (the host's own big-folder inset) and
  `inset = (cellW - folderIconSizePx)/2` when `alignToSmallFolder` is on; log which one is
  active instead of leaving it a compile-time const.
- C3. Drop `ART_INSET_RATIO`: the art inset is `dp.widgetPadding.left/top`
  (the host uses the same value in `PreviewBackground.setup`/`computeBigFolderAvaliableWh`);
  keep 0.11 only as a fallback when `widgetPadding` is 0.
- C4. Drop `GRID_OCCUPANCY`: gaps = `max(0, (bgAxis - n*childSize)/(n+1))`
  with `childSize = folderIconSizePx * CHILD_ICON_SCALE`; the two outer margins then equal
  the inner gaps. The child grid fills exactly the box whose insets the background uses,
  which is the OPlus relation without a magic occupancy factor.
- C5. Optional "OPlus parity" child size (off by default, exposed as a setting only if the
  user asks): `childSize = (box - contentPaddingFactor*padding)/N`,
  `contentPaddingFactor = N*2*f + 2` (`f = 0.6667`), `N = 3` for span > 1.
- C6. Child grid: keep the 2x2 -> 3x3 rewrite; for other spans derive
  `cols = if (spanX <= 1) 1 else 3`, `rows = if (spanY <= 1) 1 else 3` when the learned
  stock table has no entry for that span, instead of silently inheriting the 6x4 fallback.
- C7. Label: keep the full `FolderIcon.z` replacement and the single formula
  `(spanY-1)*(cellH+borderY) + rowInset + iconSize + drawablePadding`, with
  `chain.proceed()` fallback on any unresolved input.
- C8. Extend the existing `DeviceProfile.updateIconSize` telemetry hook to refresh the
  shared metrics cache, so the static `getBigFolderIconHGap/VGap` hooks never read a stale
  cell.
- C9. Logging hygiene: the hot-path debug strings (`isUpdatePreviewSize`, `childGrid idx=`)
  are built eagerly; route them through the existing `loggedSpans` limiter / lazy check.

## Verification

1. `.\gradlew.bat assembleDebug`.
2. On device (TB710FU, ZUX 18.1.9), with square mode + big-folder align on, check the hook
   logs: `page measure ... side= pad=` (cell == square on both axes, or the B2 floor),
   `BigFolderAlign ... nominalW==cellW`, `label topMargin`, `isUpdatePreviewSize`.
   Visual check on at least three grids (e.g. 6x4, 8x6, default) in portrait and landscape:
   big-folder background edges flush with the outer icon columns, child grid centred, label
   on the app-label baseline.
3. Regressions: square mode off -> slider inset still moves the grid; big-folder align off
   -> stock geometry; small folders and hotseat untouched.
4. If C5/C2 gain user-facing switches: add the `SettingItem.key` + `SearchEntry` in the same
   change and strings in both `values/` and `values-en-rUS/`, then run
   `SearchIndexConsistencyTest`.

## Risks / notes

- `cellYPaddingPx` is a profile-wide field; only container type 0 (workspace) consumes it,
  hotseat/all-apps are unaffected.
- Removing the empirical constants changes the currently tuned look; keep the old numbers
  as the documented fallback until the device check passes.
- Writing `dp.iconSizePx` (B5/C5) is a global change and needs a rebind, so it stays opt-in.
