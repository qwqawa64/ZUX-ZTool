# ZUI launcher dock background: two layers, and how to address them

Verified against `com.zui.launcher` 18.1.9 (Lenovo ZUX OS) with JADX. This is the host
behaviour `DisableDockBackground` depends on. The starting point was the existing
`DisableDockBar` hook, which targets `ZuiHotseat.setVisibility(int)`; that method ends in
`B0(0)`, the single refresh entry for the dock's blur region.

## 0. Vocabulary

The "dock" is the ZUI hotseat: `com.zui.launcher.uiextend.ZuiHotseat extends
com.android.launcher3.Hotseat`. `Hotseat` is not abstract and implements
`com.zui.launcher.blur.LauncherBlurAreaProvider`; the ZUI variant is the one inflated into
`R.id.hotseat` (framework code branches on `launcher.getHotseat() instanceof ZuiHotseat`,
e.g. `Utilities.setWorkspaceAlpha`).

The dock background is **two independent layers**:

| Layer | What it is | Owner |
|---|---|---|
| 1 | A rounded rect painted into the View's own canvas | `ZuiHotseat.dispatchDraw` |
| 2 | A `SurfaceControl` blur region on the launcher window surface | `Launcher.updateAreaBlur` |

Both must be removed for the dock to become genuinely background-free. Killing only the
blur leaves a translucent grey plate; killing only the plate leaves the blur.

## 1. Layer 1 — the self-drawn rounded rect

### 1.1 Who sets the colours, and when

`ZuiHotseat.<init>(Context, AttributeSet, int)` creates and colours five `Paint` fields.
`ZuiHotseat(Context)` and `ZuiHotseat(Context, AttributeSet)` both delegate to it via
`this(...)`, and `LayoutInflater` uses the 2-arg form, so the 3-arg constructor is the
single choke point.

```
ZuiHotseat.<init>(Context, AttributeSet, int)
  l0 = new Paint(); setStyle(FILL)
       setColor(getColor(R.color.hotseat_dock_bg_color)
                or argb(ThemeUtilsKt.OPACITY_60, r, g, b) when dock dynamic blur is off)
  k0 = new Paint(); setStyle(STROKE)
       setStrokeWidth(R.dimen.dock_border_stroke_width)
       setColor(R.color.hotseat_dock_stroke_color)
  C0 = new Paint(); setStyle(FILL);   setColor(R.color.hotseat_empty_fill_color)
  B0 = new Paint(); setStyle(STROKE); setColor(R.color.hotseat_empty_stroke_color)
  m0 = new Paint();                   setColor(R.color.hotseat_split_line_color)
       setStrokeWidth(R.dimen.hotseat_split_line_width)
  n0 = R.dimen.dock_border_radius          // shared corner radius of l0 and k0
  D0 = R.dimen.dock_empty_cell_radius      // corner radius of C0 and B0
```

Colour values (JADX resource dump):

| Resource | `res/values/colors.xml` | `res/values-night/colors.xml` |
|---|---|---|
| `hotseat_dock_bg_color` | `#4dd8d8d8` | `#593b3b3b` |
| `hotseat_dock_stroke_color` | `#1a000000` | `#1affffff` |
| `hotseat_split_line_color` | `#4d000000` | `#4dffffff` |
| `hotseat_empty_fill_color` | `#4dd8d8d8` | (not overridden) |
| `hotseat_empty_stroke_color` | `#1a000000` | (not overridden) |

### 1.2 Who paints it, and when

`ZuiHotseat.dispatchDraw(Canvas)`, every frame. The background rect `z0` is computed in
`onLayout` and exposed by the public `getBgRect()`.

```java
RectF r = this.z0;
if (r.bottom - r.top > 0) {
  if (r.right - r.left > 0) {
    canvas.drawRoundRect(r.left - L0, r.top, r.right + M0, r.bottom, n0, n0, this.l0);  // fill
    canvas.drawRoundRect(r.left - L0, r.top, r.right + M0, r.bottom, n0, n0, this.k0);  // border
    if (this.E0 && getVisualizeGridAlpha(0) == 0) {                                     // dead, see 3.3
      canvas.drawRoundRect(this.A0, D0, D0, this.C0);
      canvas.drawRoundRect(this.A0, D0, D0, this.B0);
    }
    if (this.x0.height() != 0) {
      canvas.drawLine(..., this.m0);                                                    // split line
    }
  }
}
super.dispatchDraw(canvas);   // icons
```

`L0`/`M0` are 0, so the painted rect equals `z0`.

### 1.3 The one runtime recolour, and why it is unusable

`ZuiHotseat.setWallpaperColor(int[])` rewrites **only** `l0` and `m0`, choosing between
`wallpaper_black_bg_color` / `wallpaper_white_bg_color` (and the matching split-line
colours) or the `hotseat_*` defaults, keyed on `Launcher.isWallpaperBlack()`.

It has **no caller in this APK**: `get_xrefs_to_method` returns an empty list, and a
whole-APK text search for `setWallpaperColor` matches only its own declaration (the same
search resolves `ZuiHotseat` across all three dex files, so the index is complete). It is
dead code or a reflective entry point. Its only use to us is as a *relational anchor*
(section 3.2).

## 2. Layer 2 — the SurfaceControl blur region

### 2.1 Registration

```
Launcher.setupViews()                                   // once, at launcher view inflation
  inflateRootView(R.layout.launcher)
  Hotseat hotseat = (Hotseat) findViewById(R.id.hotseat)
  if (BlurUtils.isDockDynamicBlurEnable())
      addBlurAreaProvider(hotseat)                      // Launcher.L1.add(provider)
```

`Launcher.updateAreaBlur(Consumer<SurfaceControl.Transaction>)` walks `L1`, keeps the
providers whose data passes the filter

```
blurRadius > 0 && blurAlpha > 0 && blurRect != null && blurRect.width() > 0
  && blurRect.height() > 0 && isValidate
```

and pushes the survivors as one `float[][]` (`{radius, alpha, l, t, r, b, corner x4}`) into
`new SurfaceControl.Transaction().setBlurRegions(windowSurfaceControl, regions)`, applied
via `applyTransactionOnDraw`.

### 2.2 Data source and refresh

`ZuiHotseat.getBlurAreaData()` returns the field `i1` (a `LauncherBlurAreaData`), and
`i1` is written in exactly one place:

```java
public final void B0(final int i7) {                    // void B0(int) — obfuscated name
    if (BlurUtils.isDockDynamicBlurEnable()) {
        this.i1.updateData(f28950p1 /* 400 = blur radius */,
                           getBlurAlpha(), getBlurRect(), (int) (this.n0 * getScaleX()));
        this.launcher.updateAreaBlur(transaction -> { ... setEarlyWakeupStart/End ... });
    }
}
```

`B0(0)` callers: `setVisibility`, `setTranslationY`, `setAlpha`, `setScaleX`, `setScaleY`,
`t0()`/`A0()`, `onLayout` (via `t0()`), `onUnlockScreenIconFallInStateChange`, and a
50 ms `postDelayed` from `setAlpha(1.0f)`.

Two invariants matter:

- `getBlurAlpha()` returns `0.0f` when `getVisibility() != 0`, so an invisible dock is
  filtered out of the blur array on the next `updateAreaBlur`.
- `onDetachedFromWindow` does **not** call `removeBlurAreaProvider`; registration is
  launcher-lifetime. Only `Folder`/`FolderIcon` unregister.

### 2.3 The gate, and why it is not a hook target

```java
BlurUtils.isDockDynamicBlurEnable()
  = !(isSpruce() || isLinden() || isSycamore())
    && DefaultConfigReader.getInstance(ctx).getConfigMap()
         .getOrDefault("show_dock_blur", null) != "false"
```

`show_dock_blur` is `DefaultConfigReader.KEY_SHOW_DOCK_BLUR`, read from
`/system/media/zui_launcher_config.xml` — **not** from the APK's bundled
`assets/zui_launcher_config.xml`, which has no such key. Default is enabled.

Do not force this to `false`: `isBigFolderIconDynamicBlurEnable()` ends in
`return isDockDynamicBlurEnable()`, so big-folder dynamic blur would be disabled too, and
`DepthController.v` / `DragLayer.onOverlayScrollChanged` also read the flag.

## 3. Resolving the obfuscated Paint fields

JADX prefixes colliding names, so the reflection names are the `renamed from` originals:

| DEX name | JADX display | Role |
|---|---|---|
| `l0` | `f28965l0` | dock background fill |
| `k0` | `f28963k0` | dock background border |
| `m0` | `f28967m0` | split line |
| `C0` / `B0` | `C0` / `B0` | edit-mode empty-cell fill / border |
| `z0` | `f28983z0` | background rect (`getBgRect()`) |
| `n0` | `f28969n0` | dock corner radius |

### 3.1 What a runtime predicate can and cannot separate

| Field | style | strokeWidth | colour (light) | read by |
|---|---|---|---|---|
| `l0` | FILL | 0 | `#4dd8d8d8` | `dispatchDraw`, `setWallpaperColor` |
| `k0` | STROKE | `dock_border_stroke_width` > 0 | `#1a000000` | `dispatchDraw` |
| `m0` | FILL | `hotseat_split_line_width` > 0 | `#4d000000` | `dispatchDraw`, `setWallpaperColor`, `onLayout`, `calculateScaleFactor` |
| `C0` | FILL | 0 | `#4dd8d8d8` | `dispatchDraw` |
| `B0` | STROKE | 0 | `#1a000000` | `dispatchDraw` |

- `k0` is the unique STROKE paint with `strokeWidth > 0` (`B0` is STROKE with width 0).
- `m0` is the unique FILL paint with `strokeWidth > 0`.
- **`l0` and `C0` are indistinguishable**: same type, style, stroke width and colour. No
  amount of additional object-local predicates separates them. This is a hard limit, not
  an implementation detail — do not write a "first match" loop here.

### 3.2 The relational constraint that does separate them

The dock's paints are the ones the host's own dock-colour API touches. DexKit 2.3.0 can
express "field is read by a method matching X" (`FieldMatcher.readMethods` /
`addReadMethod`), so the offline indexer emits

```kotlin
bridge.findField {
    searchPackages("com.zui.launcher")
    matcher {
        declaredClass("com.zui.launcher.uiextend.ZuiHotseat")
        type("android.graphics.Paint")
        readMethods { countMin(2) }        // dispatchDraw + setWallpaperColor
    }
}.map { it.name }                          // -> [l0, m0]
```

Intersecting the runtime predicate with this set yields `l0` uniquely:

```
l0 = {FILL, strokeWidth == 0} = {l0, C0}   ∩   readMethods >= 2 = {l0, m0}   ->  {l0}
```

DexKit has no dataflow analysis, so a static query alone cannot do better: all five paints
are read from `dispatchDraw` alongside the same integer fields, so `{l0, k0}` cannot be
separated from `{C0, B0}` statically. Static proposes, runtime disposes.

**Index staleness.** `DexIndexManager.needsReindex` rebuilds only on a
`DexIndexConstants.SCHEMA_VERSION` bump or a target apk fingerprint change. Adding an
output key to an existing indexer therefore requires a schema bump — otherwise the key
never reaches the hook, `hints` is empty, and the resolver silently degrades to the
superset. That is exactly what happened when this query was first added against a v2
index: the hook logged `ambiguous paint set, clearing all 5 fields` and still looked
correct on screen, because `C0`/`B0` are dead code (section 3.3).

### 3.3 Why degrading to a superset is safe here

`E0` gates the `C0`/`B0` empty-cell branch. `get_xrefs_to_field(ZuiHotseat, E0)` returns
exactly three hits: `<init>` (writes `false`), `dispatchDraw` and `onLayout` (both read).
**Nothing ever writes `true`**, so the branch is dead in this version and `C0`/`B0` never
draw.

Consequently "zero the alpha of every `Paint` field declared in `ZuiHotseat`" is
behaviourally identical to "zero `{l0, k0, m0}`" here, and is a safe fallback when the
DexKit index is missing or the intersection stays ambiguous. The residual risk is a future
version that wires up `E0`.

## 4. Consequences for the hook

| Layer | Hook point | Why |
|---|---|---|
| 1 | `ZuiHotseat.<init>(Context, AttributeSet, int)`, then zero `l0` / `k0` / `m0` alpha | the paints are created once and never recoloured in-APK; the constructor is matched by signature, so it is immune to obfuscation |
| 2 | `Launcher.addBlurAreaProvider(LauncherBlurAreaProvider)`, skip when the argument is a `Hotseat` | prevents registration outright; a real (non-obfuscated) name, unlike the narrower `ZuiHotseat.B0(int)` |

Rejected:

- Clearing `z0` to suppress the background: `getBgRect()` also feeds drag/touch hit
  testing (`LauncherCustom.isTouchInHotseat`, `getHotseatBoundsForViewInDragLayer`,
  `Workspace`), and the split line and empty-cell hints are nested inside the same
  `if`, so all three would break together.
- Hooking `ZuiHotseat.dispatchDraw` and not proceeding: removes the icons too.
- Hooking `Canvas.drawRoundRect`: `dispatchDraw` receives a `RecordingCanvas`, which
  overrides that method, so a `Canvas` hook would not intercept these calls; and `l0`/`k0`
  cannot be told from `C0`/`B0` by colour.
