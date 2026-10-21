# ZUI ProgressDialogX Loading Indicator — Structure, Replaceability, Call Sites

Scope: work-order item **E-01 旋转指示器** in
[zui_ui_hook_work_order.md](zui_ui_hook_work_order.md) §E. Companion analysis:
[zui_component_style_catalog.md](zui_component_style_catalog.md) §5,
[zui_component_provenance_and_hookability.md](zui_component_provenance_and_hookability.md) §1.4.

Everything below was re-verified on `ZuiSettings.apk` pulled from the test device, not
copied from the earlier notes:

* device `TB710FU` / `TB710FU_CN_OPEN_USER_Q00019.0_A16_ZUXOS_1.5.04.509_ST_260911` (Android 16)
* APK `/system/priv-app/ZuiSettings/ZuiSettings.apk`, package `com.android.settings`,
  `versionCode 999991500`, `versionName 17.5.0.251204`
* resource ids and values read with aapt2; code read with JADX

## 1. What the indicator actually is

`zui.app.ProgressDialogX.onCreate(Bundle)` inflates a layout it resolves from the
**constructor context's theme attribute** (`this.c.obtainStyledAttributes(null,
R.styleable.progressDialogX, R.attr.progressDialogXStyle, 0)`), then
`setContentView(thatLayout)`:

| Layout | id | Resolved from |
|---|---|---|
| `progress_dialog_x` | `0x7f0e02f0` | `Theme.Zui.progressDialogXStyle` = `@style/ProgressDialogX` |
| `progress_dialog_x_land` | `0x7f0e02f3` | `@style/ProgressDialogX.Horizontal` (used by `Theme.Zui.Horizontal`) |
| `progress_dialog_x_horizontal` | `0x7f0e02f1` | `.Horizontal` (`STYLE_HORIZONTAL`) |
| `progress_dialog_x_horizontal_land` | `0x7f0e02f2` | idem |

Portrait spinner layout (`progress_dialog_x`, de-obfuscated from `res/dOU.xml`):

```xml
<LinearLayout orientation="vertical"
    paddingTop="@dimen/progress_dialog_x_padding_top"          <!-- 30dp / 32dp @sw400dp -->
    paddingBottom="@dimen/progress_dialog_x_padding_bottom"    <!-- 34dp / 32dp -->
    layout_width="@dimen/progress_dialog_x_width">             <!-- 230dp / 400dp @sw400dp -->
  <zui.widget.ProgressBar android:id="@+id/progress"           <!-- 0x7f0b0740 -->
      style="?android:attr/progressBarStyleLarge"
      layout_width="wrap_content" layout_height="wrap_content"/>
  <TextView android:id="@+id/message" android:textSize="20sp"  <!-- 0x7f0b05d9 -->
      android:textColor="@color/text_title_dialog_zui"/>
</LinearLayout>
```

`progress_dialog_x_land` has the same two views and the same `@id/progress`.

### 1.1 The chain from `?progressBarStyleLarge` to the frames

`Theme.Zui` (id `0x7f1605d0`, root of the ZUI theme chain) remaps the framework
progress-bar attributes:

```xml
<item name="android:progressBarStyleLarge">@style/Widget.Zui.ProgressBar.Large</item>
```

`Widget.Zui.ProgressBar.Large` (id `0x7f160875`, parent `0x0103001d` — a framework
progress-bar style):

| attribute | value |
|---|---|
| `android:minWidth` / `android:maxWidth` | `@dimen/progress_bar_min_width_large` — **45dp**, **67dp** at `sw400dp` |
| `android:minHeight` / `android:maxHeight` | `@dimen/progress_bar_min_height_large` — **28dp**, **42dp** at `sw400dp` |
| `android:indeterminate` | `true` |
| `android:indeterminateDrawable` | `@drawable/progressbar_loading_zui` (id `0x7f0807ce`) |
| `size` (zui styleable) | `"large"` |

`progressbar_loading_zui` is `res/9Uy.xml`: an **`animation-list` with 34 items,
`duration="30"` each** (1020 ms loop) over `loading_large_rotate_000..033`, which are
**xhdpi PNGs, 76×48 px (38×24 dp)**; frame `000` also has a `sw400dp-night-xhdpi`
variant (`res/He-.png`).
`progressbar_rotate_zui` (id `0x7f0807d1`) is *not* part of this: it is a single
96×96 PNG and no progress style references it.

`zui.widget.ProgressBar` is a thin subclass of `android.widget.ProgressBar`; its only
drawable override sits behind `CommonUtils.isLegionStyleProduct()`, which is hardcoded
`return false` in this build (`isPadProduct()` early-returns, then `return false`), so
`progressbar_loading_zui_legion` is never installed here.

**Net result: the spinner is a stock framework `ProgressBar` whose indeterminate
drawable is an app-local `AnimationDrawable`.** There is no custom View, no SurfaceView,
no reflection and no framework-resource involvement.

### 1.2 `Theme.Zui.ProgressDialogX` contributes only the window

`Theme.Zui.ProgressDialogX` (id `0x7f1605f3`) is `Theme.Zui.Dialog.BaseAlert` plus
`android:windowMinWidthMajor/Minor = @dimen/action_dialog_min_width_minor_zui`. It does
not touch the progress bar; the drawable arrives through `Theme.Zui` remapping the
framework attribute name, as described above.

### 1.3 The one layout that is different

`ResetAppsHelper.showResetAppsProgressDialog()` calls
`setContentView(R.layout.reset_dialog_layout)` **after** `show()`, which replaces the
whole content of the dialog window (a second `setContentView` on an installed dialog
window removes the previous children). That layout (id `0x7f0e030e`, `res/T6H.xml`)
has

* a **bare `<ProgressBar>`** tag — i.e. `android.widget.ProgressBar`, not `zui.widget.ProgressBar`
* id `@id/charge_progress_bar` (`0x7f0b020f`) — **not** `@id/progress`
* no `style=` attribute, so it resolves `?android:attr/progressBarStyle` →
  `Widget.Zui.ProgressBar` (`32dp × 20dp`, same `progressbar_loading_zui`)

So the id, the view class and the style all differ from the stock path while the
drawable is the same.

## 2. Verdict

**Yes, the rotation indicator can be replaced — and cheaply.** It is one
`android.widget.ProgressBar` and one `Drawable`; `ProgressBar.setIndeterminateDrawable`
is public framework API, so a hook needs no reflection into host internals and no
knowledge of `zui.widget.*`.

What is *not* viable:

| Route | Status |
|---|---|
| Replace the `progressbar_loading_zui` **resource** | rejected — process-global; every `ProgressBar` in Settings (list loaders, `charge_progress_bar`, `Widget.Zui.ProgressBar.SS`) uses it |
| Point `progressDialogXStyle`/`Theme.Zui.ProgressDialogX` at a module-local style | rejected — the module's resources are not in the host's resource table; a themed attribute cannot reference them |
| Swap `progressLayoutX` via the styleable | rejected — read from the **context** theme in `onCreate`, so it would have to be changed on the Activity before the dialog is built |
| Compose `LoadingIndicator()` in a `ComposeView` | rejected — Material3's `LoadingIndicatorKt` *is* in the APK, but a dialog decor has no `ViewTreeLifecycleOwner`; setup cost far exceeds the gain |

### 2.1 Option A — custom self-animating `Drawable` (recommended)

Implement a `Drawable` in the module that draws a Material-style indicator (arc / M3
loading-indicator sweep) and drives itself (`ValueAnimator` or `Choreographer` →
`invalidateSelf()`), then assign it with `setIndeterminateDrawable`.

Why this is the right fit:

* zero host dependency — works in any scoped app, not only where the Material library
  happens to be bundled;
* keeps the dialog's layout, spacing and text untouched (E-02…E-04 stay independent);
* one view swap, fully reversible on dismiss.

Constraints to respect:

1. **The box is not square.** 45×28 dp (67×42 dp at `sw400dp`, i.e. this tablet).
   The view is `wrap_content` and only `min/maxWidth`/`min/maxHeight` give it a size,
   so the drawable ends up in a wide rectangle. A circle must letterbox itself:
   centre a square of `min(w, h)` and derive the stroke width from it, instead of
   assuming the bounds are square.
2. **The framework will not start a drawable installed after attach.**
   `ProgressBar.startAnimation()` runs once from `onAttachedToWindow()`, before our
   hook replaces the drawable. A self-animating `Drawable` sidesteps this; a frame
   list (`AnimationDrawable`) would have to be `start()`ed manually.
3. **Skip determinate bars.** The `STYLE_HORIZONTAL` layouts reuse `@id/progress` for
   the horizontal bar (`?android:attr/progressBarStyleHorizontal`); gate on
   `progressBar.isIndeterminate` so a horizontal progress bar is never turned into a
   spinner. No Settings call site sets `STYLE_HORIZONTAL` today, but the guard is free.
4. **No tint is applied by the style** (`indeterminateTint` is unset), so the drawable
   owns its colours; `?android:attr/colorAccent` / `textColorPrimary` are the natural
   picks, with a fixed fallback.

### 2.2 Option B — swap in `CircularProgressIndicator` (available, but heavier)

`com.google.android.material.progressindicator.CircularProgressIndicator` **is present
in `ZuiSettings.apk`** (verified), so it can be instantiated inside the Settings
process. But:

* it is a `View`, not a `Drawable` — the `ProgressBar` has to be replaced inside the
  `LinearLayout` (preserve `layoutParams`, or the centring breaks);
* the module does not depend on the Material Views library (only
  `material.color.utilities` is on the classpath), so it would have to be built
  reflectively;
* the Material library exists in Settings but not in the other scopes the module
  covers, so every use needs a per-APK availability guard;
* `Theme.Zui` defines none of the Material theme attributes; the component falls back
  to `Widget.MaterialComponents.CircularProgressIndicator` defaults and resolves its
  colours from `colorPrimary`. Needs an on-device check before it can be trusted;
* a 42 dp box means `indicatorSize ≈ 40dp`, `trackThickness ≈ 3–4dp` — if the view is
  simply dropped in at its default 48 dp it will be clipped.

Use this only if pixel-fidelity to Material Components matters more than portability.

## 3. Hook anchoring

| Proposal | Verdict |
|---|---|
| **A7** `ProgressDialogX.onCreate(Bundle)` post-proceed | works for the stock layout (`findViewById(progress)` is populated by then), **misses** the reset layout |
| **A13** `Dialog.show()` (what `ZuiDialogSkinHook` already does) | works for the stock layout; the reset layout is applied after `show()` returns, so the restyle is thrown away |
| `Dialog.setContentView(int)` + `Dialog.setContentView(View)` filtered by class name | **best** — fires once for `onCreate`'s layout and once for the reset replacement; `DialogController.installContent()` goes through `Window.setContentView`, so it does not produce false positives |

Recommendation: hook both `Dialog.setContentView` overloads, gate on the dialog class
name walking up to `zui.app.` (the pattern already used by
`ZuiDialogSkinHook.isZuiDialog`), and then require that the decor actually contains an
indeterminate `ProgressBar` before doing anything — so the decision never rests on the
class name alone.

Locating the view: **do not hardcode `R.id.progress`** as an id constant. Walk
`dialog.getWindow().getDecorView()` recursively, collect `android.widget.ProgressBar`
instances, keep `isIndeterminate` ones. That covers `@id/progress` (both spinner
layouts), `@id/charge_progress_bar` (reset layout) and any future ZUI layout with one
extra pass. Resolving the id by name (`resources.getIdentifier("progress", "id",
packageName)`) also works for the stock path but will silently stop matching the reset
layout.

## 4. Call sites in `ZuiSettings.apk`

`zui.app.ProgressDialogX` is referenced from exactly these classes (constant-pool
search, then confirmed per class):

| # | Class | Site | Dialog text | Process | Reachability |
|---|---|---|---|---|---|
| 1 | `com.android.settings.applications.manageapplications.ResetAppsHelper` | `showResetAppsProgressDialog()` → `show()` **then** `setContentView(reset_dialog_layout)` | layout's own `@string/reset_app_preferences` | `com.android.settings` | practical target — 设置 → 应用 → 应用信息 → 右上菜单 → 重置应用偏好设置 → 确认. No SIM needed, but the action **does** reset every app's preferences |
| 2 | `com.android.settings.MainClearConfirm` | `getProgressDialog()` in `handleMasterClear()` (AsyncTask `onPreExecute`) | `main_clear_progress_title` "Erasing" / `main_clear_progress_text` "Please wait…" | `com.android.settings` | not usable for testing — it appears only after the factory reset is confirmed, right before the wipe |
| 3 | `com.android.settings.users.UserSettings` | `addUserNow(int)` → `getProgressDialog(ctx, creating_new_user_dialog_message)`, `setCancelableOnOrientation(false)` | "Creating new user…" | `com.android.settings` | 设置 → 系统 → 多用户/用户 → 添加用户; the page is not search-indexed on PRC builds (`LenovoUtils.isPrcVersion()`), so it may be hidden on this CN tablet |
| 4 | `com.zui.mobileNetworkSettings.apnsettings.ApnSettings` | `restoreDefaultApn()` → `getProgressDialog()` | `restore_default_apn` "Restoring default APN settings." | `com.android.settings` | needs an active SIM; APN 页面 `android.zui.mobile.APN_SETTINGS` → 恢复默认 APN |
| 5 | `com.zui.simsettings.fragment.ProgressReminderDialog` | `onCreateDialog` (DialogFragment) | `activating` / `deactivating` | `com.android.settings` (host `CardInfoEditActivity`) | needs SIM; SIM 卡信息编辑页; showing it is part of the enable/disable-SIM flow |
| 6 | `com.zui.mobileNetworkSettings.NetworkSelectActivity` | `onCreateDialog(int)`, dialogs 100 / 200 | `register_on_network` / `load_networks_progress` "Searching" | **`com.android.phone`** (`android:process` in the manifest) | outside the current scope (§5); launching it can also switch carrier selection back to automatic |
| 7 | `com.lenovo.settings.wifi.DeviceActivationForWifiActivity` | `showProgressDialog(true)` | `setup_wizard_wifi_dialog_title` "正在激活请稍候…" | `com.android.settings` | OOBE only |
| 8 | `com.lenovo.settings.wifi.DeviceActivationForSimActivity` | `showProgressDialog(true)` | same string | `com.android.settings` | OOBE only |

`com.android.settings.R` and `zui.platform.R` also match the search but only define the
`progressDialogX` styleable / attribute — not call sites.

**Not this control** (easy to confuse, they draw the same artwork): the framework
`android.app.ProgressDialog`, used by `AutoSelectPreferenceController` and
`com.lenovo.settings.network.BluetoothWiFiResetPreferenceController`, inflates its own
`ProgressBar` whose style also comes from `Theme.Zui`'s remap — so it shows the same
34-frame spinner without going through `ProgressDialogX`. The AppCompat
`*ProgressDialogFragment` classes are unrelated. A hook limited to `zui.app.
ProgressDialogX` will not restyle those.

## 5. Scope caveat

`NetworkSelectActivity` declares `android:process="com.android.phone"`, so the dialogs
it builds live in the phone process. `ScopeKeys` has no phone entry and `scope.list`
does not list `com.android.phone`, so with the module scoped to `com.android.settings`
only, those two dialogs will keep the stock spinner. Decide before implementing:
accept the gap (all other call sites are in-process) or add `com.android.phone` to
`ScopeKeys` / `scope.list` — the latter drags a whole new scope and `HowToRestart`
decision with it.

## 6. Test plan for the implementation

1. Use call site **#1** (`ResetAppsHelper`): it is the only site that exercises the
   post-`show()` `setContentView` path. None of the eight sites is side-effect free —
   #1 resets app preferences by design, #3/#4/#5 need a SIM, and #2/#6/#7/#8 are
   either destructive or OOBE-only — so accept #1's reset on a test device.
2. Walk the path, confirm the spinner is gone and the custom indicator animates
   smoothly for the ~1–3 s the reset takes; confirm the dialog's 230/400 dp width,
   text size and vertical rhythm are unchanged.
3. Repeat in landscape (forces `Theme.Zui.Horizontal` → `progress_dialog_x_land`) and
   in dark mode (frame `000` ships a `sw400dp-night-xhdpi` variant, so the stock
   drawable adapted to the night palette — the replacement must pick its colour per
   `uiMode`).
4. Check a *non*-`ProgressDialogX` ZUI dialog (`MessageDialog`) and a list-loading
   `ProgressBar` elsewhere in Settings to prove the change is restricted to the
   loading dialog.
5. Only if #2 works, re-check one of the SIM-dependent sites (#3/#4) to cover the
   `@id/progress` path with a message line.

Open questions to settle on device during implementation:

* does `ProgressBar` letterbox or stretch the indeterminate drawable on this build —
  confirms the square-fitting math in §2.1 (1);
* whether `CircularProgressIndicator` renders under `Theme.Zui` at all (only relevant
  if Option B is chosen);
* whether LSPosed injects into the `com.android.phone` process when only
  `com.android.settings` is scoped.

## 7. Alternative style: SpinKit Cube Grid

The replacement carrier from §2.1 is a plain `Drawable`, so any canvas animation that
can be expressed as a function of time is available — including SpinKit's *Cube Grid*.
Nothing has to be imported: SpinKit ships as CSS (and as a third-party Flutter port),
so the animation is re-implemented, not depended on.

Canonical spec — `spinkit@2.0.1`, class `.sk-grid` ([spinkit.css](https://cdn.jsdelivr.net/npm/spinkit@2.0.1/spinkit.css)):

```css
.sk-grid-cube { width: 33.33%; height: 33.33%; animation: sk-grid 1.3s infinite ease-in-out; }
.sk-grid-cube:nth-child(1..9) delay: 0.2 0.3 0.4 / 0.1 0.2 0.3 / 0.0 0.1 0.2   /* seconds */
@keyframes sk-grid {
  0%, 70%, 100% { transform: scale3D(1, 1, 1); }
  35%           { transform: scale3D(0, 0, 1); }
}
```

So both axes are fully described by two numbers:

| Parameter | Value | Meaning |
|---|---|---|
| period | 1300 ms | one cube's shrink→grow→hold cycle |
| cell | `size / 3` | 9 cells, no gap in CSS |
| delay | `(2 - row + col) * 100 ms` | diagonal wave starting at the **bottom-left** cell |
| scale curve | `1 → 0` over 0–35%, `0 → 1` over 35–70%, `1` over 70–100% | `ease-in-out` = `cubic-bezier(0.42, 0, 0.58, 1)` |

Sizing on this device (400 dpi, `sw800dp`, so the `sw400dp` qualifier wins): the box is
**67 × 42 dp**, the grid is square, so it letterboxes to **42 × 42 dp** → cell 14 dp,
cube **12.6 dp** with a 10% gap, or 14 dp without one.

Two adjustments worth making at this size:

* **Keep a gap.** CSS cells touch at `scale == 1`, and the curve holds `scale == 1` for
  30% of the cycle, so a faithful no-gap grid flashes into a solid blue square. A 10%
  inset keeps the 3×3 grid readable through the peak and does not change the wave.
* **Optional: shorten the hold.** Moving the 70% keyframe down (e.g. 0/60/100) makes the
  wave punchier; it is a single constant.

Implementation notes, on top of the constraints in §2.1:

* derive the phase from `SystemClock.uptimeMillis()` per frame instead of accumulating
  deltas, so a dropped frame never slows the animation down;
* re-arm with `scheduleSelf(...)` from inside `draw()`, and stop ticking in
  `setVisible(false, ...)` — `ProgressBar` calls that on detach, otherwise a dismissed
  dialog keeps a 60 fps callback alive;
* nine `drawRect` calls per frame against a reused `Paint`/`RectF`; cheaper than the
  34 PNG frames the stock drawable decodes;
* the stock frames have night variants, so pick the cube colour from the current theme
  (`colorAccent` / `textColorPrimary`) instead of hardcoding one;
* if the system disables animations (`ValueAnimator.areAnimatorsEnabled() == false`),
  draw the 3×3 grid at `scale == 1` and skip the ticker.
