# Launcher no-label mode: why the hooks went silent

Analysis of ZUI Launcher `18.6.0.0927` (`com.zui.launcher`, `com.android.launcher3`).
Both no-label modules installed without throwing, but the desktop version could
not hide folder names or keep labels hidden during folder animations, and the
drawer version could not hide `BubbleTextView` labels at all.

## BubbleTextView text alpha is no longer one value

`BubbleTextView` owns a `MultiPropertyFactory` (`TEXT_ALPHA_CHANNEL_COUNT = 2`)
whose target is the icon's text-alpha `AnimatedFloat` (`G`), property
`AnimatedFloat.VALUE`, combine function `c(f1, f2) = f1 * f2`, initial channel
value `1.0f`:

- channel 0: container visibility (`setContainerTextVisibility`)
- channel 1: floating-view text alpha (`getFloatingViewTextAlpha`)

`setTextAlpha(float)` writes `G.value` directly and then updates the text color,
so the hooks on it work for ordinary calls. A channel update is different:
`MultiProperty.setValue` -> `MultiPropertyFactory.apply` ->
`AnimatedFloat.VALUE.set` -> `AnimatedFloat.updateValue` -> the `AnimatedFloat`
consumer `BubbleTextView.f` -> `setTextColor(ColorStateList)` (or
`super.setTextColor(getModifiedColor())`). That path never calls
`setTextAlpha`, so forcing `setTextAlpha(0)` cannot survive it.

## Anime phase: folder animations drive channel 1

- `FolderAnimationManager.z` moves each folder item view into the folder root
  and animates `getFloatingViewTextAlpha()` from 0 to 1 (opening) on a
  `BubbleTextView`; `ActiveIconView` items instead go through
  `setIgnoreSetAlphaVisible` + `setTextAlpha`, which the existing hook covers.
- `FolderAnimationManager.getZuiFolderAnimator` animates the folder icon name
  (also a `BubbleTextView`) through the same channel.
- `FolderSpringAnimatorSet.Companion.c` runs a spring on the same channel.
- `Folder.closeComplete` and `PopupContainer.closeComplete` set it back to 1.

Because the channel starts at 1.0 and `apply` recomputes `G` from all channels,
the first channel write re-shows a label that `setTextAlpha(0)` had hidden.
Fix (Android 17+ only): `getFloatingViewTextAlpha` now returns a detached
`MultiProperty` (a private factory over a dummy `AnimatedFloat`), created per
call so its `setAutoCancel` animators cannot cancel each other. The real channel
is never touched, so `G` stays at 0.

The views the folder animation actually moves are not the bound folder items:
`FolderAnimationManager.D(ItemInfo)` inflates fresh copies from
`R.layout.folder_application_anim_item` (and app-pair / active-icon variants),
binds only the title text and keeps the default alpha of 1. Channel redirects
cannot hide those copies, so they are hidden right after `D` returns, before
the animation adds them to the hierarchy. `FolderAnimationManager.C` builds the
big-folder add button through the same method.

## Folder icon names were never hidden

`FolderIcon` keeps its label in a child `BubbleTextView`
(`FolderIcon.getFolderName()`). Nothing in Launcher 18.6 calls
`setTextVisibility` / `setTextAlpha` on that child; the name is toggled only
through `FolderIcon.setTextVisible(boolean)` (view visibility), which
`WorkspaceLayoutManager.addInScreen` calls with `true` on every bind. That is
why desktop folder names stayed visible, and why the same text showed up
during folder open/close animations.

Fix (Android 17+ only): `FolderIcon.setTextVisible` is forced to `false`, and
the name view also gets `setTextVisibility(false)` so its text alpha stays 0
even if the view is shown again. The floating-channel fix above keeps the
close animation from raising that alpha back to 1.

## Drawer: no visibility call exists at all

`BaseAllAppsAdapter.onBindViewHolder` only does `reset()` + `applyFrom*` for
drawer icons (view type 2/512/4096 -> `BubbleTextView`, 2048 ->
`ActiveIconView`). No class under `com.android.launcher3.allapps` calls
`setTextVisibility` or `setTextAlpha`; those calls only come from `CellLayout`,
the folder animation classes, taskbar/hotseat containers and edit mode.

`LauncherDrawerNoLabelMode` therefore could only hide drawer `ActiveIconView`
labels (which it does through its attach/bind hooks); every drawer
`BubbleTextView` label stayed visible. Fix (Android 17+ only): the same
attach/bind hooks for `BubbleTextView`, gated on the parent being
`AllAppsRecyclerView` or `PredictionRowView` (the drawer's real containers),
so desktop, folder and taskbar icons are untouched.

## Version gate

All fixes are installed only when `Build.VERSION.SDK_INT >=
Build.VERSION_CODES.CINNAMON_BUN` (Android 17). Launcher releases before 18.6
keep the original hook scheme unchanged.

## Verification

A device probe against `/system/priv-app/ZuiLauncher/ZuiLauncher.apk` confirmed
that channel 1 starts at `1.0f` and `setValue(0f/1f)` drives the target
`AnimatedFloat.value` to `0.0f`/`1.0f`, i.e. the channel bypasses
`setTextAlpha`.

## Not changed

`ActiveIconView.setTextAlpha` is reached from both the direct call and
`createTextAlphaAnimator` (`TEXT_ALPHA_PROPERTY` -> `WorkspaceIconCompat
.setTextAlpha`), so its hooks keep working as before.
