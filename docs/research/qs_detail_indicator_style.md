# QS detail-indicator corner handles — style feasibility (ZUI 17.5.24 SystemUI)

Question: starting from the two injection points used by `HideDetailIndicatorHook`
(`QSTileViewImpl.loadSideViewDrawableIfNecessary` and the `ToggleSliderView` constructor),
can the appearance of the corner "handles" (the little right triangle in the bottom-right
corner of a tile card / slider card) be changed by a hook, rather than only hidden?

Verdict: **yes.** All three views are plain `ImageView`s whose drawable, tint and size are
set once at inflation and never re-applied by the host. Any post-inflation style write
sticks for the lifetime of the view. The only host-controlled property is visibility
(tiles) and a clickable/content-description update (brightness slider); neither touches
drawable, tint, size, position or alpha.

## 1. What the injection points actually reach

| Injection point | Object | Type | Reference |
|---|---|---|---|
| `QSTileViewImpl.loadSideViewDrawableIfNecessary(State)` | field `detailIndicatorView` | `ImageView` | assigned in the ctor from `R.id.tile_side_custom_detail_indicator` |
| `ToggleSliderView.<init>(Context, AttributeSet, int)` | field `mBrightnessDetailIndicator` | declared `View`, actual `ImageView` | `findViewById(R.id.brightness_detail_indicator)` |
| same ctor | field `mVolumeDetailIndicator` | declared `View`, actual `ImageView` | `findViewById(R.id.volume_detail_indicator)` |

Small tiles do **not** reach the first row: `CustomQSTileViewImpl` overrides both
`createAndAddLabels` and `loadSideViewDrawableIfNecessary` and uses a different view,
`labelDetailIndicatorView` (an `ImageView` inside `labelRoot`). It also appears in
`PcModeQSTileViewImpl`.

## 2. Current appearance (all three share one drawable)

`res/layout/qs_tile_side_icon.xml`:

```
<ImageView android:id="@+id/tile_side_custom_detail_indicator"
    android:visibility="gone" android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:src="@drawable/ic_qs_tile_label_detail_indicator"
    style="@style/QSTileSideCustomDetailIndicator"/>
```

`res/drawable/ic_qs_tile_label_detail_indicator.xml` is a 7dp × 7dp vector: a single
`fillColor="#d8d8d8"` path, right angle at the bottom-right corner (~16,16), hypotenuse
from the top-right to the bottom-left. No theme attribute, no selector, no animation.

Both slider layouts (`status_bar_toggle_slider.xml`, `status_bar_toggle_slider_detail.xml`)
repeat the same `src` and id set:

* `brightness_detail_indicator` — `layout_gravity="end|bottom"`, **VISIBLE by default**,
  `android:tint="#b2b2b2"`; paddings 25/25/10/10 in the portrait layout and 20/20/10/10 in
  the detail layout.
* `volume_detail_indicator` — `layout_gravity="bottom|right"`, paddings 20/20/10/10,
  **GONE by default**; ZTool's `VolumeSliderLongPressHook` is the only code that reveals it.

Because the vector is shared, the visible handle is the same 7dp triangle everywhere; only
the brightness copy has an extra grey tint from the layout.

`QSTileSideCustomDetailIndicator` exists only in the compiled resource table (the APK ships no
`res/values` XML), so Jadx's file-resource API cannot show its attributes. It does not matter
for hooking: any tint/alpha it sets is per-ImageView state that a hook overwrites.

## 3. Who touches these views at runtime

`detailIndicatorView` (Jadx xrefs): ctor, `init(...)` (sets the click listener),
`loadSideViewDrawableIfNecessary` (visibility only), `updateDualLabelContentDescription`.

`mBrightnessDetailIndicator` (Jadx xrefs): ctor, `onStateChanged` (accessibility mode:
click listener + clickable), `setAccessibilityContentDescription` (content description),
`ToggleSeekBar.onTouchEvent` (reads screen location and width/height to build the
tap-to-open-brightness-detail rect).

`mVolumeDetailIndicator`: ctor only (plus `VolumeSliderLongPressHook` by reflection).

The two ids appear in exactly two classes (`QSTileViewImpl` / `ToggleSliderView`) plus `R`;
no other SystemUI code fetches or restyles them. The drawable itself is never swapped at
runtime, and `QSTileViewImpl.updateResources()` / `ToggleSliderView.onConfigurationChanged()`
only refresh colors, text size and seekbar backgrounds.

## 4. Style levers

Safe, no layout side effects:

* `imageTintList` / `colorFilter` — recolor; overrides both the baked `#d8d8d8` fill and the
  layout tint. Use the ImageView API, not `Drawable.setTint` on the cached constant state.
* `alpha` / `imageAlpha`.
* `setImageDrawable(customDrawable)` — replace the triangle with any drawable built in code
  (`GradientDrawable`, `Path`-based `Drawable`, rounded shape, chevron, dot). The new
  drawable must keep the bottom-right anchoring, because the host only reserves a
  `wrap_content` side view sized to the drawable.
* `scaleX` / `scaleY` — grow/shrink the triangle without re-measuring. The side
  `FrameLayout` keeps the default `clipChildren=true`, and `QSAnimator.updateAnimators()`
  also calls `setClipChildren(true)` on some tiles, so a scale that leaves the view bounds
  is clipped. Touch hits follow the scale matrix, but `getWidth()` does not.

With layout consequences:

* `layoutParams` width/height (and `minWidth/minHeight`) — upscales the vector inside a
  larger ImageView. On tiles this widens `QSTileViewImpl.sideView`, stealing width from the
  label row. On the brightness slider it also enlarges the tap rect used by
  `ToggleSeekBar.onTouchEvent` (there is a hidden click zone around the handle).
* `padding` — shrinks/offsets the drawn triangle; on the slider handles padding is already
  part of the inflated layout (20/25dp), and it counts toward the tap rect.
* `rotation` / `layoutDirection` mirroring + `LayoutParams.gravity` — move the handle to a
  different corner (e.g. bottom-left); needs a mirrored drawable and an explicit gravity.
* `background` / `foreground` and `clipToOutline` — a pill/disc behind the triangle.

Practical limits:

* The **tile handle's visibility is re-asserted on every state change** by
  `loadSideViewDrawableIfNecessary` (`dualTarget` → VISIBLE, else GONE). Style writes are
  fine there; visibility must keep going through the hook (as `HideDetailIndicatorHook`
  already does).
* The large-tile `sideView` is host-animated: `QSAnimator.translateContent` writes
  `translationX/translationY` on it on every `updateAnimators()`. Reposition the handle with
  `LayoutParams.gravity`, not with translations.
* `ToggleSliderView` exists in two layout variants (`mFromType` 2/5 and 3 detail); both
  contain the same ids, so a ctor hook covers portrait, landscape and keyboard variants.
  In the landscape variant the seekbars are rotated 90°, the handles are not.
* The brightness handle doubles as the accessibility entry point: its content description
  and clickable state are set by the host, and the `ToggleSeekBar` hit rect is derived from
  its bounds — do not move or resize it unless the tap zone change is acceptable.
* Small tiles need a second hook on `CustomQSTileViewImpl.loadSideViewDrawableIfNecessary`
  (field `labelDetailIndicatorView`); `QSAnimator.updateAnimators` animates its alpha, so
  per-view `alpha` is not a stable lever there.
* Changing the handle means the app-side preview
  (`ControlCenterPreviewCard.kt`, `DetailIndicator`, 0.18 × tile height, right triangle,
  `onSurfaceVariant` at 0.7 alpha) should be updated in the same change.

## 5. Recommended hook shape

1. Keep both existing injection points; add the style write after `chain.proceed()`.
2. Resolve views by id rather than by field reflection
   (`resources.getIdentifier("...", "id", "com.android.systemui")`), or keep the current
   field lookup — both field names are unobfuscated in this build.
3. Make the write idempotent and cheap (compare current drawable/tint/size before writing);
   the tile path runs on every state change.
4. Do not touch `tag`, `isClickable`, `isFocusable` or the click listener — they are owned
   by `HideDetailIndicatorHook` / `ControlCenterLongPressHook` /
   `VolumeSliderLongPressHook`.
5. Apply style only when the handle is meant to be visible; if `hide_detail_indicator` is on,
   the hide path wins.
