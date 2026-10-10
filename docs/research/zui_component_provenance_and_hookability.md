# ZUI-specific UI components: provenance and hookability

Question this note answers: the ZUI-flavoured dialogs, loading/progress indicators and
widgets used by `com.android.settings`, `com.android.systemui` and `com.zui.launcher` —
are they obtained by **reflecting into the system SDK**, or are they **bundled into each
APK**? And can their **appearance** be changed by an LSPosed/libxposed hook?

Answer, in one line: **they are bundled into every APK (code *and* resources, one `0x7f`
resource-ID space per APK), so an in-scope hook can change their appearance; the only
reflection present is a thin, peripheral layer that reaches *hidden framework APIs* for
behaviour and metrics, never to obtain or style a component.**

Analysis set: `F:\USER\Jadx项目\Lenovo` (Lenovo ZUX OS system apps + `framework.jar`,
`framework-res.apk`, `services.jar`), decompiled with the JADX CLI at
`D:\Program Files\Jadx\bin` (jadx 1.5.6). Working artifacts:
`F:\USER\Jadx项目\Lenovo\_analysis\zui_widgets\`.

---

## 1. The ZUI UI stack, and where it lives

The ZUI UI library is rooted at the Java package `zui.*` (dex descriptor `Lzui/...`) with a
sibling `com.zui.internal.app.*` controller layer. It is Lenovo's fork of the AOSP
`AlertDialog` stack:

| Layer | Classes | Role |
|---|---|---|
| Public widget library | `zui.app.MessageDialog`, `zui.app.ActionDialog`, `zui.app.FloatDialog`, `zui.app.ProgressDialogX`, `zui.app.DatePickerDialogX`, `zui.app.TimePickerDialogX`, `zui.app.UnitPickerDialog`, `zui.widget.*` | The dialogs and widgets apps instantiate |
| Controller layer | `com.zui.internal.app.DialogController`, `MessageController`, `ActionController`, `FloatController` | ZUI's replacement for `com.android.internal.app.AlertController`: inflates the layout, wires buttons, list, message |
| Resource package | `zui.platform.R` | Generated R class of the library; its IDs are `0x7f......` |
| Support | `zui.appcompat.*`, `zui.preference.*`, `zui.util.*`, `zui.themes.*` | Preference widgets, reflection helpers, theme-service client |

### 1.1 Code is bundled — dex-level proof

`dexlib.py` (`iter_dex_bytes` / `defined_classes`) was used to enumerate the classes each
archive **defines**, as opposed to merely references. The dialog stack is defined in every
consumer APK:

| Archive | defines `zui.app.MessageDialog` / `ProgressDialogX` / `com.zui.internal.app.*Controller` |
|---|---|
| `settings.apk`, `settings_15.apk` | yes (all) |
| `系统界面_17.5.24.apk` (SystemUI) | yes (no `ProgressDialogX` / `DatePickerDialogX` / `TimePickerDialogX`) |
| `ZUXLauncher.apk` | yes (all) |
| `com.zui.desktoplauncher_17.0.1.0535.apk` | yes (all) |
| `Work 桌面`, `AIService`, `ZUX系统服务`, `ZuxFreeformBar`, `壁纸和主题风格`, `固件更新`, `软件包安装程序`, `通讯录`, `手写笔`, `智慧识屏`, `游戏助手`, … | yes (all or a subset) — 20+ archives |
| `framework.jar` | **no** |
| `services.jar` | **no** |
| `framework-res.apk` | **no** (no dex at all) |

`framework.jar` (36 636 classes) contains only *framework-level* ZUI APIs — service
interfaces and managers: `zui.icon.*`, `zui.performance.*`, `zui.aplog.*`,
`zui.app.ZuiWallpaperManager`, `zui.view.ZuiKeyEvent`, `zui.widget.ZuiTextWatcher`,
`zui.widget.NotificationUtils`, `zui.inputmethodservice.ZuiNavigationBarController`,
`com.zui.input.*`, `com.zui.internal.app.FullScreenBackgroudView`,
`com.zui.internal.app.ZuiShutdownActivity`, `Landroid/app/Zui*Manager`. **None of the
dialog or progress classes is there.**

`zui.app.*` in an APK is a *direct static reference*: `zui.app.a` (the package-private
dialog base) does `new DialogController(getContext(), this, getWindow())`, and
`zui.app.ProgressDialogX` does `super(context, i, R.attr.progressDialogXTheme,
R.style.Theme_Zui_ProgressDialogX, z)`. There is no `Class.forName`, no `getIdentifier`
and no `com.android.internal.R` lookup anywhere on the dialog/progress path.

### 1.2 Resources are bundled — one `0x7f` ID space per APK

`zui.platform.R` in `settings.apk` assigns the library's resources IDs in the `0x7f......`
range, i.e. **the app's own resource package**, not the framework's `0x01` package and not
a shared-library package:

```java
// src/settings/sources/zui/platform/R.java
public static int message_dialog = 0x7f0e01dc;
public static int progress_dialog_x = 0x7f0e02ec;
public static int Theme_Zui_MessageDialog = 0x7f1605e4;
public static int Theme_Zui_ProgressDialogX = 0x7f1605f1;
```

Cross-checked against the APK's own `resources.arsc` (via `public.xml`): `0x7f1605e4` is
`Theme.Zui.MessageDialog` and `0x7f1605f1` is `Theme.Zui.ProgressDialogX` — both defined
in the *app's* table. `zui.platform.R` and `com.android.settings.R` share the same `0x7f`
namespace, which is what a merged resource table looks like. The two R classes are
field-for-field identical for the shared resources, which is the cleanest proof that the
library's resources were merged into the app rather than resolved from a system package:

```java
// src/settings/sources/zui/platform/R.java       src/settings/sources/com/android/settings/R.java
public static int message_dialog              = 0x7f0e01dc;   // same
public static int progress_dialog_x           = 0x7f0e02ec;   // same
public static int progress_dialog_x_horizontal= 0x7f0e02ed;   // same
public static int Theme_Zui_MessageDialog     = 0x7f1605e4;   // same
```

`framework-res.apk` contains **none** of `message_dialog`, `progress_dialog_x`,
`Theme.Zui*`, `dialog_background_zui`. Its only ZUI-named dialog assets are
`res/drawable/zui_btn_dialog.xml` and `res/drawable/zui_dialog_list_divider.xml`, used
solely by the framework's own `system_warning_dialog_btn_bg`. No consumer APK references
them.

### 1.3 Worked example: the appearance chain of a ZUI message dialog

```
zui.app.MessageDialog.Builder.create()                     (app dex)
  -> zui.app.MessageDialog  extends  zui.app.a  extends  android.app.Dialog
       zui.app.a.a(Context,int,int,int)                    theme resolution
         context.getTheme().resolveAttribute(
             R.attr.messageDialogTheme, ..., fallback R.style.Theme_Zui_MessageDialog)
       -> new com.zui.internal.app.MessageController(...)  (app dex)
            obtainStyledAttributes(null, R.styleable.MessageDialog,
                                   R.attr.messageDialogStyle, 0)
            mLayout = <MessageDialog_android_layout>  ?? R.layout.message_dialog
  -> DialogController.installContent()
       mWindow.setContentView(mLayout)                     single inflation point
```

Every resource on that chain is app-local:

| Resource | Value | Where |
|---|---|---|
| `res/layout/message_dialog.xml` | `LinearLayout` + `zui.widget.TextView`, `?attr/dialogDividerColor`, `@color/button_background_zui`, `@dimen/divider_width_zui` | APK |
| `style Theme.Zui.MessageDialog` | parent `Theme.Zui.Dialog.BaseAlert`; sets `android:windowTitleStyle`, `android:titleTextColor`, `android:subtitleTextColor`, `android:windowAnimationStyle` | APK |
| `style Theme.Zui.BaseDialog` → `android:windowBackground` | `@drawable/dialog_background_inset_zui` | APK |
| `drawable dialog_background_inset_zui` | inset of `@drawable/dialog_background_zui` | APK |
| `drawable dialog_background_zui` | `<shape>` rectangle, `corners android:radius="@dimen/dialog_radius_zui"` (20dp), `solid android:color="@color/dialog_background_zui"` | APK |
| `color dialog_background_zui` → `system_dialog_background_zui` | `@android:color/system_accent1_10` | **framework** dynamic colour (monet) |
| `color text_title_dialog_zui` | `#ff191919` (dark variant `#fffafafa`) | APK |
| `dimen dialog_radius_zui` | `20dp` (`float_dialog_radius_zui` = 12dp) | APK |

Full style chain (all in the APK):
`Theme.Zui` (parent `@android:style/Theme.Material.Light`) → `Theme.Zui.BaseDialog`
(sets `android:windowBackground=@drawable/dialog_background_inset_zui`) →
`Theme.Zui.Dialog` (button-bar styles) → `Theme.Zui.Dialog.BaseAlert` (dim amount, min
width) → `Theme.Zui.MessageDialog` / `Theme.Zui.ProgressDialogX` / `Theme.Zui.ActionDialog`
/ `Theme.Zui.FloatDialog`. Only the *root* of that chain is a framework theme. A handful of
framework attribute *names* are still referenced from the layouts
(`?android:attr/buttonBarPositiveButtonStyle`, `?android:attr/progressBarStyleLarge`), but
`Theme.Zui` redefines them to bundled ZUI styles (see §1.4), and either way they are
resolved through the app's own theme, so they remain overridable in-process.

So the *shape, radius, layout, typography, dividers and button styling are bundled*, and
only the **final surface fill colour** is pulled from the framework's Material-You tonal
palette (`android:color/system_accent1_10`), because ZUI aliases its own colour to it.

### 1.4 The ZUI loading indicator

`zui.app.ProgressDialogX` (of the three target apps, referenced by `com.android.settings`
only — SystemUI and both launchers ship the class but never call it; e.g.
`com/android/settings/MainClearConfirm.java:118`,
`com/android/settings/applications/manageapplications/ResetAppsHelper.java:99`) inflates
`R.layout.progress_dialog_x` / `progress_dialog_x_horizontal`:

```xml
<LinearLayout android:layout_width="@dimen/progress_dialog_x_width" ...>   <!-- 230dp -->
  <zui.widget.ProgressBar android:id="@+id/progress"
      style="?android:attr/progressBarStyleLarge"/>
  <TextView android:id="@+id/message" android:textSize="20sp"
      android:textColor="@color/text_title_dialog_zui"/>
</LinearLayout>
```

`zui.widget.ProgressBar` extends `android.widget.ProgressBar` and, on its default path,
takes its indeterminate drawable from `style Widget.Zui.ProgressBar`
(`android:indeterminateDrawable = @drawable/progressbar_loading_zui`), which is an
**app-local 34-frame `animation-list`** of PNGs (`loading_large_rotate_000..033`,
30 ms/frame). Everything is bundled. The `?android:attr/progressBarStyleLarge` reference in
the layout is *not* a framework escape hatch either: the app's own `Theme.Zui` overrides
that framework attribute —

```xml
<!-- res/values/styles.xml, inside Theme.Zui -->
<item name="android:progressBarStyleLarge">@style/Widget.Zui.ProgressBar.Large</item>

<!-- Widget.Zui.ProgressBar.Large -->
<item name="android:indeterminateDrawable">@drawable/progressbar_loading_zui</item>
```

— so the spinner graphic is app-local, reached through a framework *attribute name*. The
same pattern covers `progressBarStyle`, `progressBarStyleSmall`, `progressBarStyleHorizontal`,
`…Inverse`. In SystemUI the equivalent drawable is `progressbar_loading_zui_legion` /
`progressbar_loading_zui`; the launcher and desktoplauncher use the same names.

### 1.5 Where reflection *is* used (and what for)

Reflection exists, but it is a thin peripheral layer aimed at **hidden framework APIs**,
never at obtaining or styling a component. Counts are occurrences inside the `zui/` library
tree of each APK:

| APK | `Class.forName` | `getDeclaredMethod` | `getDeclaredField` | `Resources.getSystem()` / `getIdentifier` | `SystemProperties` | `ServiceManager` |
|---|---|---|---|---|---|---|
| settings | 4 | 7 | 13 | 6 | 11 | 2 |
| SystemUI | 3 | 4 | 13 | 5 | 9 | 1 |
| launcher | 1 | 7 | 13 | 6 | 11 | 2 |
| desktoplauncher | 2 | 7 | 13 | 6 | 11 | 2 |

The concrete uses:

| Site | Reflection target | Purpose | Appearance? |
|---|---|---|---|
| `com.lenovo.common.internel.Res` (settings, **214 files**) | `Class.forName("com.android.internal.R$drawable"/"$dimen"/"$styleable"/… )` + `Field.getInt(null)` | hidden-API workaround to obtain **AOSP framework** resource IDs that the SDK stubs do not expose. Used for things like `config_backgroundDimAmount`, AOSP layouts and styleables — never for a ZUI widget | only indirectly, and only where an AOSP framework resource is reused |
| `com.lenovo.common.utils.ZuiConfig` | none — 36-line pass-through to `Resources.getBoolean/getInteger/getStringArray` | reads nothing, gates nothing; a red herring for provenance work | no |
| `zui.util.CommonUtils.getSystemProperties` | `ClassLoader.getSystemClassLoader().loadClass("android.os.SystemProperties")` | read `ro.config.zui.devicetype`, `ro.config.lgsi.device.type`, `ro.config.lgsi.screen_size`, `persist.sys.zui.pcmode`, `ro.product.name` | no — product/form-factor gating |
| `zui.util.CommonUtils.getAndroidInternalRes{Bool,Dimen,Int,String,Id}` | `Resources.getSystem().getIdentifier(name, type, "android")` | read framework-res values **by name** (avoids a hidden-API dependency at compile time), e.g. `config_navBarInteractionMode` | metrics only |
| `zui.widget.ProgressBar.a(Class,String,int)` | `Class.forName("android.widget.ProgressBar")` + `getMethod("setMinWidth"/"setMaxWidth"/"setMinHeight"/"setMaxHeight")` | hidden framework setters, executed only when `CommonUtils.isLegionStyleProduct()` is true | size only, and dead on every build in this set — `isLegionStyleProduct()` discards its own `getSystemProperties("ro.zui.product.gamephone")` result and unconditionally returns `false` (settings `CommonUtils.java:127-133`, launcher `:106-112`, desktoplauncher `:117-123`); SystemUI's copy is `void` |
| `zui.widget.SeekBar` | hidden `android.widget.ProgressBar#setProgressExternal` | slider plumbing | no |
| `zui.themes.ThemeManager` (inner `a extends ReflectClass`) | `android.os.ServiceManager.getService("zuithemes")` | talk to the ZUI theme service (`IThemeService`) for fonts/theme config | indirectly (theme/font), not per-dialog |
| `zui.util.Resources`, `zui.util.CompatibilityInfo` | `android.content.res.Resources#getCompatibilityInfo/getFloat`, `CompatibilityInfo#applicationScale` | compatibility-scale / float resource reads | no |
| `zui.appcompat.widget.{Toolbar,ActionMenuView,ActionMenuPresenter,WindowDecorActionBar}` | `Class.forName("android.support.v7.widget.Toolbar")` etc. | legacy wrappers around the *old support library* — the class does not exist in modern APKs, so these wrappers fail and log | no |
| `com.zui.quickstep.views.AppBackgroundView` (launcher) | `Class.forName("zui.app.IMagicWindowManager$Stub")` + `ServiceManager` | fetch a blurred window bitmap from the `magicwindow` service | blur only, not a widget |

`zui.util.ReflectClass` / `zui.util.ReflectMethod` are generic helpers (`Class.forName` +
`getDeclaredMethod` + `setAccessible`) used by the sites above. **No dialog, progress or
widget class depends on them for its appearance.**

`android.R.*` appears 115–128 times per tree, but those are ordinary public references
(`android.R.id.button1`, `android.R.id.text1`, `?android:attr/buttonBarPositiveButtonStyle`)
used to *identify* views and inherit framework theme attributes — not reflection.

### 1.6 The framework's one real contribution: `android.app.Dialog` is a ROM fork

`framework.jar` does not provide the ZUI widgets, but it does replace the *base class* they
all derive from. Verified from the DEX class hierarchy:

```
framework.jar : Landroid/app/Dialog;      extends Landroid/app/OvfBaseDialog;
framework.jar : Landroid/app/OvfBaseDialog;  extends Ljava/lang/Object;
settings.apk  : Lzui/app/a;               extends Landroid/app/Dialog;
settings.apk  : Lcom/zui/internal/app/DialogController;  extends Ljava/lang/Object;
```

So every ZUI dialog inherits the ROM's `OvfBaseDialog` window-mode/freeform machinery
(`framework.jar` also carries `android.util.OvFreeformUtils`, `com.android.internal.ov.*`).
That is a genuine system-SDK dependency of the dialog stack — but it is a *superclass in
the `android` scope*, not a mechanism by which the components are obtained, and it is
reached by ordinary inheritance, not reflection.

---

## 2. Per-app inventory

Reference counts below are occurrences of the symbol in each decompiled tree (they include
the library's own definitions and imports, so treat them as an order of magnitude).

| Component | settings | SystemUI | launcher | desktoplauncher |
|---|---|---|---|---|
| `zui.app.MessageDialog` | 301 | 32 | 27 | 19 |
| `zui.app.FloatDialog` | 8 | 5 | 8 | 8 |
| `zui.app.ActionDialog` | 18 | 1 | 12 | 12 |
| `zui.app.ProgressDialogX` | 8 | — | — | — |
| `zui.app.DatePickerDialogX` | 4 | — | — | — |
| `zui.widget.SeekBarNps` | — | 6 | — | — |
| `com.zui.internal.app.DialogController` | 12 | 18 | 12 | 12 |

ZUI widgets referenced from decoded layouts (custom-view tags), showing the same picture —
`zui.widget.*` and `zui.appcompat.preference.*` are the building blocks of these UIs:

| APK | top custom-view tags |
|---|---|
| settings (668 tags) | `com.lenovo.common.widget.CommonPreference` 163, `zui.appcompat.preference.SwitchPreference` 120, `…PreferenceWithArrow` 85, `…Preference` 32, `zui.widget.Switch` 11, `zui.widget.ProgressBar` 6, `zui.widget.NumberPickerX` 9 |
| SystemUI (72) | `zui.widget.TextView` 9, `zui.appcompat.preference.PreferenceCategory` 9, `zui.widget.Switch` 8, `zui.widget.SeekBarNps` 7, `zui.widget.ProgressBar` 6, `zui.widget.NumberPickerX` 6 |
| launcher (241) | `com.zui.timelaweather.View.WidgetTextView` 50, `zui.appcompat.preference.SwitchPreference` 22, `zui.widget.NumberPickerX` 8, `zui.widget.ProgressBar` 4, `com.zui.launcher.views.GridProgressBar` 4 |
| desktoplauncher (429) | `zui.preference.SwitchPreference` 25, `zui.preference.PreferenceCategory` 16, `zui.widget.ProgressBar` 4, `zui.widget.NumberPickerX` 8 |

### 2.1 `com.android.settings` (`settings.apk`, `settings_15.apk`)

* `zui.app.MessageDialog` is *the* dialog of Settings: **293** `new MessageDialog.Builder(...)`
  construction sites (301 symbol occurrences; 223 sites under `com/android/**`, 62 under
  `com/lenovo/**`, 5 under `com/zui/**`).
* `zui.app.ProgressDialogX` is the blocking-progress dialog, e.g.
  `com/android/settings/MainClearConfirm.java:118` (factory-reset progress) and
  `com/android/settings/applications/manageapplications/ResetAppsHelper.java:99`
  (reset app preferences).
* `zui.app.ActionDialog` drives single/multi-choice lists:
  `com/android/settings/inputmethod/PhysicalKeyboardFragment.java:1049`,
  `com/lenovo/settings/sim/DualCardPrefsFragment.java:803/845/877` (default SIM for
  call/SMS/data), `com/lenovo/settings/system/autopower/SetTimeRepeatPreferenceController.java:135/160`.
* `zui.app.FloatDialog` is the overflow/anchor popup used by
  `zui/appcompat/widget/ZuiAppcompatToolbar.java:110`, `zui/widget/SimpleToolbar.java:131`,
  `zui/widget/ListViewX.java:713/735`.
* Preference UI is `zui.appcompat.preference.*` (bundled) with
  `com.lenovo.common.roundedcorner.*` supplying the rounded-card look;
  `com.lenovo.common.widget.ZuiDialogPreference` /
  `ZuiPreferenceDialogFragmentCompat` bridge preferences to the ZUI dialog.
* `settings_15.apk` carries the same stack: 1814 zui-ish classes defined, 2 external, and
  `Lzui/*` = 275 vs 279 in `settings.apk` (274 common). **No ZUI dialog or progress class
  differs** — the deltas are compiler artefacts plus dropped `EditTextX` / `SearchViewX`.

### 2.2 `com.android.systemui` (`系统界面_17.5.24.apk`)

SystemUI bundles a **pruned** subset: `zui.app` has only `a`, `MessageDialog`,
`ActionDialog`, `FloatDialog`; there is no `ProgressDialogX`/`DatePickerDialogX`/
`TimePickerDialogX`/`UnitPickerDialog`/`AlertActivity`/`ModalActivity`; and `zui.widget`
additionally contains `SeekBarNps` (used by the QS brightness/volume sliders, and already
hooked by `hook/modules/systemui/qs/ControlCenterLongPressHook.kt`).

Two structural quirks worth knowing before hooking SystemUI:

* SystemUI ships **no** `zui/platform/R.java` (only `R$styleable.java`); its
  `zui/app/MessageDialog.java:23` imports **`com.android.wm.shell.R`** directly. So inside
  SystemUI the "library R class" is the WMShell R class — the same merged-table story, with
  a different host.
* `com.android.settingslib.utils.ZuiCustomDialogHelper` is bundled in **SystemUI** (and
  only there): `settings.apk` neither defines nor references it. It is not a `Dialog`, just
  a holder for a ZUI dialog's title/message/button views.

Representative ZUI dialog call sites:

* QS tiles: `com/android/systemui/qs/tiles/{BluetoothTile,QPCModeTile,QPoweroffTile,QRebootTile,QSmartSoundTile}.java`,
  `com/android/systemui/qs/tileimpl/QSTileImpl.java:681`
* PC mode: `com/android/systemui/pcmode/PcModeManager.java:1238`,
  `com/android/systemui/pcmode/window/TipsDialogController.java:115`
* status bar core: `com/android/systemui/statusbar/phone/ZuiCoreImpl.java` (six sites),
  `ZuiCentralSurfacesImpl.java:242`
* LGSI keyboard shortcuts: `com/android/systemui/statusbar/LgsiKeyboardShortcuts.java`
  (four sites, with `R.style.zui_keyboard_dialog`)
* screen recorder: `com/zui/screenrecorder/ScreenRecorderService.java:1215`,
  `com/zui/screenrecorder/floatview/FloatView.java:1516`
* WM shell: `com/android/wm/shell/compatui/OvGameTipActivity.java:28`,
  `OvSplitGameTipActivity.java:32`, `windowdecor/OvDragModeGuide.java:25`

`com.android.systemui.globalactions.ZuiGlobalActionsDialog` (power menu) is
SystemUI-specific, not part of the `zui.*` library — and it is **not a `Dialog`**: it adds a
raw view through `WindowManager.addView(...)` (`ZuiGlobalActionsDialog.java:291`, root
`ZuiGlobalActionsView extends FrameLayout`, layout `R.layout.shutdown_dialog_zui_new`).
Hooking it means hooking `ZuiShutdownContainerView.onFinishInflate()` rather than a dialog
lifecycle method.

SystemUI also carries ZUI-library subclasses that exist nowhere else and are convenient,
stable hook anchors because they are *single-purpose*:

| Subclass | Extends | Used for |
|---|---|---|
| `com.android.systemui.zui.ZuiSystemUIDialog` | `zui.app.MessageDialog` | SystemUI's standard ZUI dialog |
| `com.android.systemui.volume.SafetyWarningDialog` | `zui.app.MessageDialog` | volume safety warning (`VolumeDialogImpl.java:373`) |
| `com.android.settingslib.users.UserCreatingDialog` | `zui.app.MessageDialog` | user-creation progress (`R.layout.user_creation_progress_dialog`) |
| `com.android.systemui.pcmode.window.{EnterPcModeGuidanceDialog,PcKeyboardGuidanceDialog}` | `android.app.Dialog` directly | PC-mode guidance |

### 2.3 `com.zui.launcher` (`ZUXLauncher.apk`) and `com.zui.desktoplauncher`

Same bundled library, plus launcher-local ZUI subclasses of AOSP launcher3 classes
(`com.zui.launcher.views.ZuiArrowPopup extends ArrowPopup`,
`ZuiPopupContainerWithArrow`, `ZuiTaskbarEduTooltip extends AbstractFloatingView`,
`ZuiEditModePanel`, `ZuiTaskbarDragGuidePageView`, `com.android.launcher3.taskbar.ZuiDividerFrameLayout`).
The launcher theme chain ends at the bundled `Theme.Zui`
(`BaseLauncherTheme parent="@style/Theme.Zui"`), which is where
`messageDialogStyle` / `actionDialogStyle` / `floatDialogStyle` / `progressDialogXStyle`
are supplied.

`com.zui.launcher` and `com.zui.desktoplauncher` are **not** the same codebase renamed:
different packages and version streams (18.1.8 vs 17.0.1.0535), separate ZUI customisation
packages, and all taskbar/popup-container ZUI classes are absent from desktoplauncher.
They do share the same bundled `zui.*` library *by name* (resource IDs differ, e.g.
`Theme.Zui` = `0x7f1203a5` vs `0x7f110386`).

---

## 3. Can a hook change the appearance?

**Yes.** Nothing needs to be "un-reflected": the components and their resources are
ordinary app classes and app resources, reachable from the target process's classloader and
`Resources` object. Three hooking layers are available; they compose.

### 3.1 Layer 1 — code hooks (recommended)

The library centralises inflation, so a handful of targets covers every ZUI dialog in an app:

| Target | Why | Where (settings / SystemUI / launcher / desktoplauncher) |
|---|---|---|
| `com.zui.internal.app.DialogController.installContent()` | the single `mWindow.setContentView(mLayout)` point for **all** `zui.app.a` dialogs (`MessageDialog`, `ProgressDialogX`, date/time pickers) | `DialogController.java:779` / `:427` / `:761` / `:852` |
| `com.zui.internal.app.ActionController.installContent()` | override used by `ActionDialog` | `:162` / `:34` / `:161` / `:161` |
| `com.zui.internal.app.FloatController.installContent()` | override used by `FloatDialog` (anchor popups, overflow menus) | `:524` / `:129` / `:512` / `:535` |
| `com.zui.internal.app.MessageController.setupContent(...)` | message body/title/icon wiring; the place to post-process `zui.widget.TextView` styling | `MessageController.java` |
| `zui.app.a.a(Context,int,int,int)` | resolves `R.attr.*DialogTheme` → forces which `Theme_Zui_*` style is used | `zui/app/a.java:47` / `:41` / `:44` / `:53` |
| `zui.app.MessageDialog$Builder.create()` | last point before the dialog exists; the object you get back is an `android.app.Dialog` | `zui/app/MessageDialog.java:83` (Builder ctor `:54`) |
| `zui.app.MessageDialog.show()` / `zui.app.FloatDialog.showAtLocation(Rect)` | after the window exists; safest place to mutate the decor view (radius, background, animation) | `zui/app/MessageDialog.java`, `zui/app/FloatDialog.java` |
| `zui.app.ProgressDialogX.onCreate(Bundle)` | per-dialog setup for the loading dialog (sets message, max, progress) | `zui/app/ProgressDialogX.java:93` |
| `com.lenovo.common.widget.ZuiPreferenceDialogFragmentCompat.onCreateDialog` | settings-only: the bridge from `Preference` to a ZUI dialog | `:106` |
| `zui.appcompat.preference.PreferenceDialogFragmentCompat.onCreateDialog` | library-level equivalent, present in settings/launcher/desktoplauncher | `:107` |
| `com.lenovo.common.roundedcorner.LenovoRoundedCorner.initRoundedCorner` | settings-only: the rounded-card look of preference rows | `:100` |
| `zui.widget.ProgressBar` ctor / `zui.widget.Switch` ctor / `zui.widget.SeekBar` ctor / `zui.widget.NumberPickerX` ctor | per-widget drawable/thumb/divider replacement | `zui/widget/*.java` |
| SystemUI power menu: `ZuiGlobalActionsDialog.handleShow()`, `ZuiShutdownContainerView.onFinishInflate()/setTypeface()` | the power menu is **not** a `Dialog` (raw `WindowManager` view), so it needs its own anchor | `com/android/systemui/globalactions/…` |

Post-inflation view mutation is the safest pattern and is already used in this repo:
`CustomQsRoundCorner` hooks `refreshSeekBar` / `updateRippleRadius` and rewrites
`GradientDrawable.cornerRadius` and `LayerDrawable` layers on the produced views. The same
technique applies to a ZUI dialog: hook `installContent()`, `chain.proceed()`, then walk
`dialog.getWindow().getDecorView()` and restyle by view id
(`R.id.parentPanel`, `R.id/title_template`, `R.id/alertTitle`, `R.id/buttonPanel`,
`R.id/progress`, `R.id/message`).

### 3.2 Layer 2 — resource hooks

Because every appearance resource lives in the app's own table under a stable *name*, a
module can resolve it at runtime and substitute the value:

```kotlin
val id = resources.getIdentifier("dialog_background_zui", "drawable", pkg)
```

Then hook a resolution entry point — `Resources.getDrawable(int, Theme)`,
`Resources.getColor(int, Theme)`, `Resources.getDimensionPixelSize(int)`,
`TypedArray.getResourceId(int, int)` — and return a replacement for that id. This is the
only way to change the *shape/radius* (`dialog_radius_zui`, `dialog_background_zui`)
without touching code, and it changes the look everywhere the resource is used.

Two caveats: (a) `Resources` is shared and hot — resolve ids once, cache, and keep the hook
body allocation-free; (b) the ZUI library is a *pruned subset per APK*, so an id that
exists in settings.apk may be absent in SystemUI — always resolve by name and fall back.

### 3.3 Layer 3 — theme-attribute hooks

`zui.app.a` resolves the dialog theme through the **host context's theme**
(`context.getTheme().resolveAttribute(R.attr.messageDialogTheme, …)`, with the bundled
`Theme_Zui_*` as fallback). The apps themselves set those attributes in their base theme
(`Theme.Zui` → `messageDialogStyle` / `actionDialogStyle` / `floatDialogStyle` /
`progressDialogXStyle`). Hooking `Resources.Theme.resolveAttribute(int, TypedValue, boolean)`
(or `Context.getTheme()`) therefore lets a module swap an app's dialog style wholesale —
useful when the goal is "make all ZUI dialogs use style X" rather than restyling views.

### 3.4 The one piece that is not app-local: the surface colour

`color system_dialog_background_zui = @android:color/system_accent1_10` (light) /
`@android:color/system_neutral2_700` (night) resolves into **framework-res.apk**, i.e. the
Material-You tonal palette generated from the wallpaper. Progress-bar accent colours are
the same story (`system_accent1_600`, `system_neutral1_100`). This is the *only* appearance
input that lives outside the APK.

Changing that fill colour therefore means either (a) overriding the colour at Layer 2 by
hooking the *app-local* alias `dialog_background_zui` / `system_dialog_background_zui`
before it reaches the framework value (preferred), or (b) hooking framework colour
resolution in `android` scope (system-wide, higher blast radius). Text colours
(`text_title_dialog_zui = #ff191919`, dark `#fffafafa`), the 20 dp corner radius and the
spinner animation are all app-local and can be replaced directly.

### 3.5 Constraints, risks, version drift

1. **Per-package registration.** Every APK ships its own copy, so the module must register
   the hook for each scope package (`com.android.settings`, `com.android.systemui`,
   `com.zui.launcher`, …) — exactly the existing `hook/modules/<package>/` layout. There is
   no single system-wide choke point.
2. **Obfuscation is partial.** Public API names survive (`zui.app.MessageDialog`,
   `MessageDialog$Builder`, `com.zui.internal.app.DialogController`), but internals are
   minified: the dialog base is `zui.app.a`, the widget base is `zui.widget.a`, inner
   classes are `DialogController$a…$e`, and fields are single letters (`mLayout` survives,
   `this.a`, `this.b` do not). Field access must be resolved defensively or through the
   project's offline DexKit index (`docs/dex_index/readme.md`), never hardcoded.
3. **Version drift.** The library subset differs per APK and per ROM build. SystemUI's copy
   has no `ProgressDialogX` / date-time pickers at all; the launcher *defines*
   `ProgressDialogX` but never calls it; `SeekBarNps` exists only in SystemUI; and
   `ZuiEditModePanel` is a different implementation in launcher vs desktoplauncher. Wrap
   every lookup in `runCatching` and degrade to "leave the stock look" on failure.
4. **Layout inflation identity.** `DialogController.installContent()` is defined *and
   overridden* (`ActionController`, `FloatController`); hook the concrete subclass method
   as well as the base, or hook `Window.setContentView(int)` instead. Do **not** hook
   `LayoutInflater.inflate` globally — it is extremely hot.
5. **Re-entrancy.** A dialog is created and shown on many paths (including from
   `Application.onCreate`-time code); keep the hook idempotent and never assume the view
   hierarchy is complete in a constructor hook — post to the main looper or hook after
   `chain.proceed()`.
6. **Do not use reflection to *call* the components** unless cross-process convenience
   demands it. `hook/modules/launcher/misc/BatchUninstall.kt:516` uses
   `Class.forName("zui.app.MessageDialog", true, loader)` to *display* a ZUI dialog from the
   module; that is a module-side choice and is unrelated to how the launcher itself obtains
   the class.
7. **Resource replacement is global within the process.** Changing
   `dialog_background_zui` affects every ZUI dialog and every view using it — including
   `preference_card_background_zui` and `float_dialog_background_color`, which alias the
   same colour.
8. **The shared classes make reuse easy.** `zui.app.MessageDialog`, `zui.app.ProgressDialogX`,
   `zui.widget.ProgressBar`, `zui.widget.KeyboardViewX`, `zui.util.ReflectClass` and
   `com.zui.internal.app.DialogController` are defined in settings, settings_15, SystemUI,
   ZUXLauncher and desktoplauncher alike, so one hook class can serve all those scopes —
   parameterised by `ScopeKeys` package name. Only `com.lenovo.common.*` targets
   (`ZuiDialogPreference`, `LenovoRoundedCorner`) are settings-specific.
9. **Build-to-build stability is good where it matters.** `settings.apk` and
   `settings_15.apk` differ by 4148 defined classes overall, but their `Lzui/*` sets are
   279 vs 275 (274 common) and **no ZUI dialog or progress class differs** — the deltas are
   compiler artefacts (synthetic lambdas vs named inner classes) plus dropped `EditTextX` /
   `SearchViewX`. Expect the same for other ROM updates; still guard by presence.
10. **One decompilation gap.** `zui.app.MessageDialog.e()` fails to decompile cleanly
    (`MessageDialog.java:749`, animation selection); read it in smali
    (`jadx --single-class` or `mcp__jadx__get_smali_of_class`) if the dialog animation path
    matters.

### 3.6 Recommended shape for a ZTool module

A single `AppHookModule` per scope package, anchored on the controller layer, is enough to
restyle every ZUI dialog of that app. Sketch (project libxposed patterns; see
`docs/hook/libxposed_guide_ztool_ver.md`):

```kotlin
override fun handleLoadPackage(param: PackageLoadedParam) {
    val cl = param.defaultClassLoader
    // Base controller: one setContentView for every zui.app.a dialog that does not
    // override installContent (MessageDialog, ProgressDialogX, pickers).
    // ActionController/FloatController override it -> register those too.
    val install = runCatching {
        cl.loadClass("com.zui.internal.app.DialogController")
          .getDeclaredMethod("installContent")
    }.getOrNull() ?: return

    hookWithId(install, "zui_dialog_skin") { chain ->
        val result = chain.proceed()
        runCatching {
            // DialogController.mWindow is `public final Window` (DialogController.java:79)
            val window = findField(chain.thisObject.javaClass, "mWindow")
                .get(chain.thisObject) as? android.view.Window
            window?.decorView?.let { restyle(it) }
        }
        result
    }
}
```

Rules that follow from the findings above:

* Anchor on `com.zui.internal.app.*Controller` (stable, present in every consumer APK)
  rather than on `zui.app.*` (pruned differently per APK).
* Resolve resources by **name** through the host `Resources`, never by hardcoded ID.
* Hook `ActionController.installContent()` and `FloatController.installContent()` too —
  they override the base method.
* Guard every lookup with `runCatching`; if a class/method is missing, leave the stock look.
* Do not hook `LayoutInflater.inflate`; it is a hot path shared by the whole process.

---

## 4. Residual uncertainty

* The analysis set contains `framework.jar`, `services.jar` and `framework-res.apk` only.
  If the device carried an additional boot-classpath jar defining `zui.app.*` (none is
  present here), ART's parent-first delegation could shadow the bundled copy. Since every
  consumer APK defines the classes itself and references them statically, the bundled copy
  is the one in use unless such a jar exists.
* The `zui.themes` theme-service path (`zuithemes` / `IThemeService` / `ThemeConfig` in
  `framework.jar`) is a *supported* appearance channel ZUI already offers. It was not
  exercised here; it is a plausible alternative to hooking for font/theme-level changes.

## 5. Artifacts

All under `F:\USER\Jadx项目\Lenovo\_analysis\zui_widgets\`:

| Path | Contents |
|---|---|
| `dexlib.py` | pure-stdlib DEX reader (`defined_classes`, `all_strings`, `iter_dex_bytes`) |
| `zui_defs.py`, `out/*.classes.txt`, `out/summary.json` | per-archive defined-vs-external ZUI class sets |
| `sweep_markers.py`, `marker_sweep.txt`, `out/marker_sweep.json` | whole-folder sweep for the dialog stack |
| `scan_reflection.py`, `reflection.txt` | reflection-pattern census |
| `scan_res_zui.py`, `res_zui.txt`, `scan_layout_tags.py`, `layout_tags.txt` | resource and layout-tag census |
| `arsc.py`, `verify_provenance.py`, `prove_bundled.py` (+ `out_provenance.txt`, `out_bundled.txt`) | pure-stdlib `resources.arsc` reader; proves each resource *name* lives in the APK's own table and is absent from `framework-res.apk` |
| `zui_scan.py`, `zui_hierarchy.py`, `zui_arsc*.py`, `scan_zui_refs.py`, `scan_zui_usage.py`, `scan_reflect_ui.py` | DEX pool / superclass-chain / arsc-pool probes |
| `launcher_res_probe.py`, `launcher_cmp_desktop.py`, `launcher_cmp_roles.py`, `cmp_settings.py` | cross-APK resource-ID and source-similarity comparison |
| `reports/_zui_subset_compare.txt`, `reports/_zui_pool_systemui.txt`, `reports/_arsc*.txt` | raw probe outputs |
| `src/{settings,systemui,systemui_full,launcher,desktoplauncher,framework-res}` | JADX 1.5.6 decompiled sources + resources |
| `reports/{launcher,systemui,settings,zui-library-core}.md` | per-target deep dives (84 KB / 68 KB / 690 lines / 944 lines) |

Tooling caveat: `arsc.py` is a hand-written reader validated against settings, launcher and
desktoplauncher `public.xml` (96–99 % exact name matches). It is **not** reliable for
SystemUI, whose `layout`/`anim` type chunks use `FLAG_OFFSET16` and whose key pool is
name-mangled; for SystemUI rely on value-path strings, the `res/*.xml` byte search and
`res/values/public.xml`.
