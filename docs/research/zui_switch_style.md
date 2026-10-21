# ZUI Switch (G-01): stable hook point, indicator and M3 restyle designs

Question this note answers: **does `zui.widget.Switch` have a stable hook point that can change
its style**, enough for (1) a check/cross indicator and (2) an M3-style replacement?

Answer in one line: **yes — the class is public and unminified, shipped by every in-scope APK,
and its constructors plus the inherited `android.widget.Switch` public setters are sufficient
to restyle every instance without touching any minified internal.**

The analysis in §1–2 is read-only. Reference APKs live in `F:\USER\Jadx项目\Lenovo`; the
`com.android.settings` copy on the attached device (17.5.0.260902) was pulled and checked
against the reference (17.5.0.251204) for the resources and descriptors this note relies on.
The implementation lives in `ZuiSwitchStyleHook` (see §6). Device checks so far: indicator
check-on, and M3 on/off with check, cross and both marks; light theme, RTL and the launcher
scopes are still to be exercised.

---

## 1. What the switch actually is

### 1.1 Class

`zui.widget.Switch extends android.widget.Switch` and only adds vibration plus four custom
XML attributes (`LeftAligned`, `rightAligned`, `ThumbPaddingLeft`, `ThumbPaddingRight`), which
it writes into hidden framework fields (`mThumbAlignTrackLeft`, `mThumbPaddingLeft`, …). It
overrides no drawing method. The visual is therefore 100% stock `android.widget.Switch`
behaviour driven by the app-local style below.

It defines four public constructors (reference build):

```
Switch(Context)
Switch(Context, AttributeSet)
Switch(Context, AttributeSet, int)
Switch(Context, AttributeSet, int, int)
```

They do not chain into each other — each calls `super(...)` directly — so a hook on every
declared constructor styles each instance exactly once.

### 1.2 Style

`Widget.Zui.CompoundButton.Switch` (parent `@android:style/Widget.CompoundButton`), applied
through `android:switchStyle` in `Theme.Zui`, not through the class:

| Attribute | Value |
|---|---|
| `android:thumb` | `@drawable/switch_thumb_zui_anim` — `animated-selector` of nine-patch circles |
| `android:track` | `@drawable/switch_track_zui` — one-item `layer-list`; rounded rect, radius 12dp, height 24dp, tinted by `switch_track_background_zui` |
| `android:showText` | `false` (the `capital_on` / `capital_off` items are present but not drawn) |
| `android:switchMinWidth` | `100` (px) |
| `android:minWidth` / `minHeight` | 48dp |
| `android:background` | `@drawable/button_focus_not_press_background_zui` |
| `android:thumbTint` | not set; the animated selector tints itself with `?colorSwitchThumbNormal` |

Track colours come from the framework Material palette: on = `system_primary_color_on_*_zui`
(aliases of `@android:color/system_accent1_*`), off = `system_icon_off_color_zui`, thumb =
white (`switch_thumb_zui` selector).

### 1.3 Widget layout and consumers

Both preference lineages end in the same widget layout, which is a bare
`zui.widget.Switch` with `android:id="@android:id/switch_widget"`, `background=@null`,
`clickable=false`, `layout_width=58dp` (desktoplauncher: `wrap_content`):

```
res/layout/preference_widget_switch_zui.xml:
<zui.widget.Switch android:id="@android:id/switch_widget"
    android:layout_width="58dp" android:layout_height="wrap_content" ... />
```

* `zui.appcompat.preference.SwitchPreference.a()` calls
  `setWidgetLayoutResource(R.layout.preference_widget_switch_zui)` unconditionally, and
  `onBindViewHolder` casts `android.R.id.switch_widget` to `zui.widget.Switch`.
* `zui.preference.SwitchPreference` / `SwitchPreferenceX` get the same layout through
  `Preference.Zui.SwitchPreference` (`android:widgetLayout`), which `Theme.Zui` supplies as
  `android:switchPreferenceStyle`.
* `com.android.settingslib.widget.MainSwitchBar` inflates
  `settingslib_main_switch_bar`, whose switch is also `zui.widget.Switch` (the *expressive*
  variant of that layout uses `com.google.android.material.materialswitch.MaterialSwitch`
  instead, so it is already M3 and must be left alone).
* Plain `zui.widget.Switch` tags in ordinary layouts (11 in Settings, 8 in SystemUI) and
  programmatic uses (e.g. `ZuiBatteryBaseCardView`) go through the same constructors.

### 1.4 Presence per scope (reference and installed build)

| APK | defines `Lzui/widget/Switch;` | `preference_widget_switch_zui` | style |
|---|---|---|---|
| settings (17.5.0.251204 / installed 17.5.0.260902) | yes | yes, 58dp, `zui.widget.Switch` | size 15, identical 15-item body |
| SystemUI (17.5.24 / installed) | yes | yes, 58dp, `zui.widget.Switch` | size 15, same thumb/track items |
| launcher (18.1.8 / installed) | yes | yes, 58dp, `zui.widget.Switch` | same thumb/track items |
| desktoplauncher (17.0.1.0535 / installed) | yes | yes, `wrap_content`, `zui.widget.Switch` | same thumb/track items |

`zui.preference.SwitchPreference` is absent from SystemUI only;
`zui.appcompat.preference.SwitchPreference` exists everywhere. That matches the work order's
K-04 note.

### 1.5 Measured geometry (device, 400 dpi = 2.5x, 3200×2000)

Captured from the WLAN main switch bar and from two preference rows on the attached
ZUX OS 1.5.04 device:

| Part | Pixels | dp |
|---|---|---|
| visible track | 110 × 60 | 44 × 24 |
| visible thumb circle | 50 | 20 |
| thumb inset inside track | 5 | 2 |
| thumb drawable intrinsic | 80 | 32 (the nine-patch carries ~5dp transparent padding per side) |
| preference widget slot | — | 58 |

So the stock rendering is: 44×24dp pill, 20dp white thumb travelling 20dp, right-aligned in
the 58dp slot with ~14dp slack on the start side (LTR). This matches the resource maths:
`mThumbWidth = intrinsic 32dp − 2 × 5dp padding = 22dp`, `switchMinWidth = 40dp`, hence
`mSwitchWidth = max(40, 2 × 22) = 44dp`; `mSwitchHeight = max(track 24, thumb 32) = 32dp`,
with the layer-list's `gravity=center_vertical` keeping the visible pill 24dp tall.

---

## 2. The hook point

### 2.1 Anchor

Hook **every declared constructor of `zui.widget.Switch`** after
`chain.proceed()`, then restyle `chain.thisObject` (this is the same constructor-hook shape
already used by `CustomQsRoundCorner`). Resolve the class from the host loader by name:

```kotlin
val cls = runCatching { param.classLoader.loadClass("zui.widget.Switch") }.getOrNull()
    ?: return                       // pruned consumer: keep the stock look
cls.declaredConstructors.forEachIndexed { index, ctor ->
    hookWithId(ctor, "zui_switch_ctor_$index") { chain ->
        val result = chain.proceed()
        runCatching { restyle(chain.thisObject as android.widget.Switch) }
        result
    }
}
```

Why this is stable:

* The class name is public and survives R8; constructors cannot be renamed independently of
  the class, and host code (`SwitchPreference`, `MainSwitchBar`, layouts) instantiates the
  class by that name.
* It is present in all four in-scope APKs, including the builds installed on the device.
* `loadClass` + `declaredConstructors` tolerates a build that adds or drops an overload;
  a missing class degrades to the stock switch instead of failing.
* No resource ID, nine-patch padding or minified member is hardcoded.

### 2.2 Public API surface the restyle uses

All inherited from `android.widget.Switch` (public, API 1/21, stable):

| Call | Use |
|---|---|
| `setThumbDrawable(Drawable)` / `getThumbDrawable()` | wrap or hide the thumb |
| `setTrackDrawable(Drawable)` / `getTrackDrawable()` | wrap or replace the track |
| `setSwitchMinWidth(int)` | force the M3 52dp box |
| `setSplitTrack(false)` | stop the thumb-shaped clip when the track is replaced |
| `setShowText(false)` | suppress ON/OFF text (already false in the style) |
| `setThumbTintList` / `setTrackTintList` | optional recolouring only (not enough for M3) |
| `isChecked`, `getDrawableState` | state source (`android.R.attr.state_checked`) |

State propagation is automatic: `Switch.drawableStateChanged()` calls `setState()` on both
drawables **only when `drawableState()` is stateful**, so a wrapper must override
`isStateful() = true` and return `true` from `onStateChange()` to trigger `invalidate`.

### 2.3 What is *not* used as an anchor

| Candidate | Why not |
|---|---|
| `zui.widget.Switch.a()` / hidden framework fields | minified internals, only used for thumb alignment |
| `android.widget.Switch.draw/onDraw` | would fire for unrelated stock switches and `SwitchCompat` in the same process |
| `Resources.getDrawable("switch_thumb_zui_anim" / "switch_track_zui")` | process-global and on the hottest resource path; cannot vary per instance |
| theme `android:switchStyle` override | `zui.widget.Switch`'s 2-arg constructor resolves the style through the theme, but replacing a whole style leaves no per-instance indicator/M3 geometry |
| replacing the view with a `MaterialSwitch` in the row | `SwitchPreference` keeps a `zui.widget.Switch` field and binds/recycles the holder; swapping the view desynchronises state, listeners and accessibility |

---

## 3. Design A — check / cross indicators

Two variants; both keep the stock track and thumb animation.

### A1. Glyph in the thumb (check when on, cross when off)

Wrap the existing thumb drawable:

1. Read `switch.thumbDrawable` (the animated selector).
2. Install a delegating `Drawable` (`GlyphThumbDrawable`) that
   * forwards `intrinsicWidth/Height`, `getPadding`, `getOpticalInsets`, `setTintList/Mode`,
     `setAlpha`, `setColorFilter`, `setHotspot`, `setLayoutDirection`, `getOutline`,
     `jumpToCurrentState` to the host,
   * reports `isStateful() = true`, forwards `setState()` to the host and remembers
     `state_checked`,
   * implements `Drawable.Callback` for the host (forwarding `invalidateDrawable` /
     `scheduleDrawable` / `unscheduleDrawable` to itself) so the nine-patch's checked
     transition still animates,
   * draws the host, then a check (checked) or cross (unchecked) centred in its bounds.
3. `switch.setThumbDrawable(wrapper)` — idempotent if the wrapper is already installed.

The glyph follows the native thumb, including the slide animation, because the wrapper *is*
the thumb. Bounds are 32dp with the visible circle 20dp centred; a 10–12dp glyph is legible.
The thumb is white in both themes (disabled: grey), so use the theme accent for the check and
a neutral grey for the cross; dim the alpha when `state_enabled` is absent.

### A2. Both glyphs on the track (the "coexist" reading)

Wrap the existing track drawable instead: draw the host track, then a cross on the exposed
end when off and a check on the exposed end when on. Because the 44dp track has a 20dp thumb
travelling 20dp, exactly one half is visible in each settled state:

* checked (thumb right): draw the check on the left half (x ≈ 11dp), dim the cross;
* unchecked (thumb left): draw the cross on the right half (x ≈ 33dp), dim the check.

This gives the "both indicators present" look the work order's wording allows, while never
putting a glyph under the thumb. It still uses only `setTrackDrawable`.

Variant A2 draws *both* glyphs permanently (one dimmed). Variant A1 draws exactly one at a
time. Both are one enum value apart in the preference schema.

---

## 4. Design B — M3 replacement

Pixel-accurate M3 cannot be produced by swapping the native thumb/track alone: `Switch`
computes `mSwitchWidth = max(switchMinWidth, 2 × mThumbWidth + trackPadding)` and derives the
on-thumb end inset from the track padding, so the M3 combination (52dp track, 24dp on-thumb
with a 4dp inset, 16dp off-thumb with an 8dp inset) is not expressible — 4dp padding also
widens the box to 56dp, and zero padding leaves the thumb flush with the end.

The workable design is therefore: **make the switch box exactly the M3 box, then draw the
whole switch inside the track drawable.**

1. `setSwitchMinWidth(52dp)` (px), `setShowText(false)`, `setSplitTrack(false)`.
2. `setThumbDrawable(TransparentDrawable(24dp))` — keeps `hitThumb()` and the 52×32dp
   measurement math but draws nothing.
3. `setTrackDrawable(M3SwitchDrawable)` with intrinsic height 32dp.
   `Switch.draw()` then sets the drawable's bounds to exactly the 52×32dp switch rect
   (adjusted by thumb optical insets, which are `NONE`).

`M3SwitchDrawable` draws:

* track: rounded rect 52×32dp, radius 16dp; colour animated between
  `colorPrimary` (on) and a surface-variant/outline pair (off), disabled alpha applied;
* thumb: a constant 24dp circle whose centre animates between off `(16dp, r=12dp)` and on
  `(36dp, r=12dp)` on `state_checked` change (`scheduleSelf` ticker, ~200ms eased). Stock M3
  shrinks the off thumb to 16dp, but a constant size keeps the optional check and cross
  marks identical, which is what the ZTool option wants;
* optional 16dp check inside the on-thumb (M3-expressive look), which also satisfies the
  "check when on" requirement for this style;
* state handling from `getState()`: `state_enabled`, `state_pressed` (M3 grows the thumb
  slightly when pressed), `state_focused` (outline), RTL mirroring via
  `getLayoutDirection() == LAYOUT_DIRECTION_RTL`.

The drawable is the track, so it receives the view's drawable state and can invalidate itself
through the switch (`verifyDrawable(track) == true`).

Colour sourcing: `android.R.attr.colorPrimary` / `colorOnPrimary` / `colorOutline` /
`colorSurfaceVariant` from the host theme, with `android.R.color.system_accent1_*` /
`system_neutral*` fallbacks. `SystemUI`'s and Settings' themes both expose these attributes;
the loading-indicator hook already uses the same `themeColor()` helper.

---

## 5. Constraints and degradation

1. **Only `zui.widget.Switch` is covered.** Stock `android.widget.Switch`, `SwitchCompat` and
   `MaterialSwitch` instances are not ZUI-styled and are out of scope; the expressive
   `settingslib_expressive_main_switch_bar` already uses `MaterialSwitch` and should be left
   alone. A bare `<Switch>` tag is not necessarily a `zui.widget.Switch` (it depends on which
   view inflater the host installs); the explicit `zui.widget.Switch` tags in the preference
   widget layout and in ordinary layouts always are.
2. **Pruned APKs.** If `zui.widget.Switch` is missing, skip; never assume resources exist.
3. **Forced 52dp width.** All four preference widget layouts reserve 58dp (desktoplauncher
   wraps), and `MainSwitchBar` is wrap-content, so 52dp fits. A hypothetical tighter slot
   would clip; the M3 drawable should clamp the drawn width to `min(52dp, bounds.width())`.
4. **`switch_thumb_zui_anim` is stateful.** Wrapping it must preserve its callback, or the
   checked/unchecked cross-fade stops; A1's `Drawable.Callback` forwarding is required, not
   cosmetic.
5. **Tint.** `setThumbTintList`/`setTrackTintList` are applied to whatever drawable is
   installed; wrappers must forward `setTintList`/`setTintMode` to the host or the stock thumb
   loses its white tint.
6. **Hot reload.** A constructor hook only affects switches created after the reload; existing
   rows keep their style until rebound. Re-applying in `setChecked(boolean)` (also public,
   called on every preference bind) is a cheap repair path.
7. **No text.** `showText=false` is part of the style; if an instance overrides it, M3 must
   call `setShowText(false)` or the ON/OFF text will overpaint the custom track.
8. **One process, one style.** Each app process reads the preference once, as
   `ZuiLoadingIndicatorHook` does; a hot reload re-reads it.

---

## 6. Integration outline for ZTool

* Hook module: `hook/modules/zuiui/ZuiSwitchStyleHook.kt` (registered in
  `HookManager.registerZuiWidgetModules()`), targets
  `ScopeKeys.SETTINGS`, `SYSTEM_UI`, `LAUNCHER`, `DESKTOP_LAUNCHER`; `supportsPackage()`
  returns true so any scoped app that bundles `zui.widget.Switch` is covered.
* Drawables: `hook/modules/zuiui/switch/SwitchStyleDrawables.kt` — `GlyphThumbDrawable`,
  `TransparentThumbDrawable`, `M3SwitchDrawable`, the glyph and theme-colour helpers.
* Preference keys: `ZUI_SWITCH_STYLE` (Bool master), `ZUI_SWITCH_STYLE_KIND`
  (`indicator` / `m3`) and `ZUI_SWITCH_MARK` (`none` / `check` / `cross` / `both`), mirrored
  by `SwitchStyle` / `SwitchMark` in `data/zuiui/SwitchStyle.kt`.
* Frontend: the "开关" section of the existing ZUI UI components page
  (`ZuiUiComponentsViewModel` / `ZuiUiComponentsRepository`), with matching search entries.
* Verification (device): WLAN main switch, Display preference rows, a disabled switch, dark
  and light theme, an RTL locale, scrolling a long preference list (view recycling), and one
  launcher/desktoplauncher settings page; screenshot diff against the stock layout.

---

## 7. Decisions taken

1. Indicator semantics: `check` draws a check only when on, `cross` draws a cross only when
   off, `both` uses the corresponding glyph in each state. Variant A1 (glyph in the thumb)
   is the implemented one; the track-drawn dual-glyph variant was not built.
2. The M3 thumb keeps a constant 24dp size in both states (stock M3 shrinks it to 16dp off),
   so the check and cross marks stay the same size; the M3 style honours the same mark option
   as the indicator.
3. Colours follow the host accent (`colorAccent`) with fixed M3 baseline neutrals for the
   off track, outline and thumb; there are no colour pickers.
