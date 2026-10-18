# Status-bar notification overflow dot — how to remove it by hook (ZUI 17.5.24 SystemUI)

Question: starting from the two injection points used by `NotificationIconHook`
(`NotificationIconContainerStatusBarViewModel` constructor and the
`NotificationIconsViewData` constructor), how can a hook remove the "dot" that the status bar
shows when there are too many notification icons?

Verdict: the count-layer injection points cannot remove it. The dot is not a resource, an
extra view or an icon of its own — it is a **visible state of the same `StatusBarIconView`**
that would have held the next notification icon. The dot position is chosen by
`NotificationIconContainer.calculateIconXTranslations()`, applied through
`StatusBarIconView.setVisibleState(...)` and painted by `StatusBarIconView.onDraw(Canvas)`.
A hook must therefore target the view layer: rewrite the DOT state (recommended) or suppress
the circle draw.

All names below are unobfuscated in this build (`go/retraceme` mapping present), so plain
`loadClass` + reflection is safe here; the usual DexKit index rule still applies to builds
that lose these names.

## 1. What the existing injection points already control

| Layer | Target | Effect |
|---|---|---|
| ViewModel ctor | `NotificationIconContainerStatusBarViewModel.maxIcons` (public final int) ← `resources.getInteger(R.integer.max_notif_static_icons)` | count limit used by the VM |
| Data ctor | `new NotificationIconsViewData(maxIcons, icons, 4)` → `(visibleIcons, iconLimit, LimitType.MaximumAmount)` | per-emission icon limit |
| View binder | `NotificationIconContainerViewBinder$bindIcons$5` writes `container.mMaxIcons = viewData.iconLimit` on every icons emission | the container field is overwritten each time; do not hook the field |
| Layout | `NotificationIconContainer.calculateIconXTranslations()` reads `mMaxIcons` via `shouldForceOverflow(...)`; `onMeasure` counts `mMaxIcons + 1` children | only decides *when* overflow starts |

Nothing in that chain touches the dot. The count limit merely forces the overflow index; when
horizontal space runs out first, the dot appears no matter what `maxIcons`/`iconLimit` are.

## 2. Where the dot is actually produced

1. `NotificationIconContainer.onLayout(...)` — only when `mIsStaticLayout` (default `true`) —
   runs `resetViewStates()` → `calculateIconXTranslations()` → `applyIconStates()`.
2. `calculateIconXTranslations()` walks the children. A child overflows when
   `shouldForceOverflow(index, mSpeedBumpIndex, iconAppearAmount, mMaxIcons)` (count; the field
   `mSpeedBumpIndex` is final `-1` in this build) or `isOverflowing(isLast, leftBound,
   rightBound, mIconSize)` (space) returns true. At the first overflowing child it records
   `mVisualOverflowStart`, then, in the second loop, the first child whose
   `iconAppearAmount >= 0.8` becomes the dot (`IconState.visibleState = 1`) and sets
   `mIsShowingOverflowDot = true`; every child after that one gets `visibleState = 2`.
3. `applyIconStates()` → `NotificationIconContainer$IconState.applyToView(View)` →
   `StatusBarIconView.setVisibleState(int state, boolean animate, Runnable endRunnable,
   long delay)`.
4. `StatusBarIconView.setVisibleState(...)`: state `0` (ICON) animates `mDotAppearAmount`
   towards `2.0`, state `1` (DOT) towards `1.0`, state `2` (HIDDEN) towards `0.0`. The guard
   `state != mVisibleState` makes repeated calls no-ops.
5. `StatusBarIconView.onDraw(Canvas)`: the icon is scaled by `mIconAppearAmount` (which is `0`
   in DOT state), and if `mDotAppearAmount != 0` a circle is painted at
   `(mNewStatusBarIconSize / 2, height / 2)` with radius `mDotRadius` (amount ≤ 1) or
   `interpolate(mDotRadius, width / 4, amount - 1)` with fading alpha (amount > 1), colour
   `mDecorColor`. The `amount > 1` branch is the dot's "burst" when a DOT turns back into an
   icon.

`mIsShowingOverflowDot` is written only in `calculateIconXTranslations()` and read only by
`NotificationShelf.getAmountInShelf()` (the shelf's own container), so it can be left alone.

## 3. Which containers can show a dot

| Container | Id / class | Dot possible? |
|---|---|---|
| Status bar | `NotificationIconContainer`, id `notificationIcons` (`res/layout/notification_icon_area.xml`), inflated by `CollapsedStatusBarFragment.onViewCreated` per display | yes (`mIsStaticLayout = true`) |
| Extended / PC display | the same layout is also inflated by `ExtendNotificationIconAreaController`, so the id is not unique — the id filter is a heuristic, not a unique key | same code path if that container is attached |
| AOD | plain `NotificationIconContainer`, id `aod_notification_icon_container`, built in `AodNotificationIconsSection.addViews` | yes |
| Shelf | `NotificationShelfIconContainer` (id `content`); `NotificationShelf.onFinishInflate` sets `mIsStaticLayout = false` | no — `calculateIconXTranslations()` never runs |

The 4-argument `setVisibleState` exists only on `StatusBarIconView`; `StatusBarMobileView` /
`StatusBarWifiView` implement only the 2-argument interface method, so hooking the 4-argument
overload cannot disturb battery/Wi-Fi/mobile icons.

## 4. Hook levers

### A. Rewrite the DOT state (recommended)

Target the 4-argument overload. Do **not** build its signature from class names: the third
parameter is the compiler-generated lambda class
`com.android.systemui.statusbar.phone.NotificationIconContainer$$ExternalSyntheticLambda0`
(the source type is `Runnable`), whose name can change between builds. Select it by name and
arity instead:

```kotlin
val iconViewClass = classLoader.loadClass("com.android.systemui.statusbar.StatusBarIconView")
val setVisibleState = iconViewClass.declaredMethods.firstOrNull {
    it.name == "setVisibleState" && it.parameterTypes.size == 4
} ?: return
```

Mapping choices:

| DOT → | Result | Notes |
|---|---|---|
| `2` (HIDDEN) | dot gone, its slot stays empty | matches "0 icons" / strict count limit; visible icon count unchanged |
| `0` (ICON) | dot replaced by the real notification icon | fills the slot; can exceed `mMaxIcons` by one when the count limit — not space — caused the overflow |
| `2` if `index >= mMaxIcons`, else `0` | no dot, no wasted slot, count limit kept | needs `mMaxIcons` from the parent container and `parent.indexOfChild(view)` |

### B. Flip the states after `calculateIconXTranslations()`

`calculateIconXTranslations()` is a stable, no-argument method on `NotificationIconContainer`.
After `chain.proceed()`, read the public `mIconStates` map (`View -> IconState`), and for the
container whose id is `notificationIcons` rewrite every `IconState.visibleState == 1` to `2`
(the public field `visibleState`). Leave `mIsShowingOverflowDot` untouched (`true`) so the later
icons stay hidden. This is container-scoped by construction and runs once per layout rather
than once per state change.

### C. Suppress the drawing

* `StatusBarIconView.onDraw(Canvas)`: temporarily force `mDotAppearAmount = 0` around
  `chain.proceed()`. Kills the static dot **and** the `amount > 1` burst. Runs on every frame.
* Hook the `StatusBarIconView(Context, String, StatusBarNotification, boolean)` constructor and
  set the public `mDotRadius = 0`: kills only the static dot. The burst branch ignores
  `mDotRadius` for the endpoint (`width / 4`), so a dot→icon transition still paints a growing
  circle. `reloadDimens$3()` does not restore the field (`mDotRadius == mStaticDotRadius` is
  false), so the write sticks across configuration changes.

### D. Do not try to disable overflow detection

Returning `false` from `shouldForceOverflow(...)` / `isOverflowing(...)` makes
`calculateIconXTranslations()` lay every icon out sequentially, and
`notification_icon_area.xml` sets `android:clipChildren="false"` — the extra icons draw over
the clock / neighbouring views instead of being clipped. The dot exists precisely to prevent
that.

## 5. Geometry and side effects

* Space overflow uses a **two-icon** test for non-last children
  (`leftBound + 2 * mIconSize > rightBound`). With equal icon widths the dot position always
  satisfies `dotX + mIconSize <= rightBound`: the icon that the dot replaces would itself have
  fit. The dot is a deliberate reserve for one more icon, not the first icon that no longer
  fits.
* Hiding the dot (→ `2`) leaves the reserved tail of the icon area empty. Icons are
  start-aligned inside `notification_icon_area` and the rest of the status-bar row is empty
  background, so this does not read as a gap next to other content; it only means `onMeasure`
  still reserves `mMaxIcons + 1` icon widths.
* With `mMaxIcons == 0` the dot sits at index 0 and is the only child that would draw; hiding
  it leaves the container empty, but `onMeasure` still adds the width of child 0
  (`i4 <= mMaxIcons`), so a one-icon-wide blank is still reserved. Collapsing that blank is a
  separate width/measure concern, not part of dot removal.
* Showing the real icon instead (→ `0`) fills that tail, but makes the count limit off by one
  whenever `maxIcons` (not space) triggered the overflow.
* A DOT→ICON transition animates the dot burst (`mDotAppearAmount` 1 → 2, expanding fading
  circle). With mapping `1 → 2` a slot that later becomes a normal icon plays the ordinary
  "icon emerges from overflow" animation.
* Scope filter: `(view.parent as? View)?.id == notificationIcons` limits the change to the
  status bar / extended display. Without the filter the AOD container loses its dot too.
* `com.android.wm.shell.R` is this APK's R class, so the id can be resolved with
  `classLoader.loadClass("com.android.wm.shell.R\$id").getField("notificationIcons").getInt(null)`
  or with `resources.getIdentifier("notificationIcons", "id", "com.android.systemui")`.

## 6. Recommended hook shape

Add a separate, stable hook id inside `NotificationIconHook` (installed only when the feature is
enabled), resolve everything in `handleLoadPackage`, and keep the interceptor branch tiny:

```kotlin
private fun hookOverflowDot(classLoader: ClassLoader) {
    val iconViewClass = classLoader.loadClass(
        "com.android.systemui.statusbar.StatusBarIconView"
    )
    val setVisibleState = iconViewClass.declaredMethods.firstOrNull {
        it.name == "setVisibleState" && it.parameterTypes.size == 4
    } ?: run {
        logger.warn("StatusBarIconView.setVisibleState(...) not found")
        return
    }
    val rId = classLoader.loadClass("com.android.wm.shell.R\$id")
    val statusBarIconsId = rId.getField("notificationIcons").getInt(null)

    hookWithId(setVisibleState, "hide_overflow_dot") { chain ->
        val parent = (chain.thisObject as? View)?.parent as? View
        if (parent?.id != statusBarIconsId || chain.args[0] != 1) {
            return@hookWithId chain.proceed()
        }
        val args = chain.args.toMutableList()
        args[0] = 2
        chain.proceed(args.toTypedArray())
    }
}
```

Notes:

* `chain.args[0]` is the `int` state; `1` is DOT. Return `chain.proceed()` unchanged in every
  other case so the ICON/HIDDEN paths stay native.
* The method returns `void`; `chain.proceed(...)` returns `null`, which is the correct result.
* Drop the id filter if the AOD dot should be removed as well.
* A user-facing toggle would need the usual `PreferenceKeys` + repository + UiState +
  `SearchIndex` wiring (`docs/hook/add_frontend_item.md`); the hook itself needs nothing else.

## 7. Status

Static analysis of the bundled SystemUI (versionName 17.5.24, versionCode 20260716,
compileSdk 36). Not verified on a device.
