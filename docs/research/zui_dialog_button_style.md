# ZUI Dialog Button Style — Host Structure And Hook Anchoring

Scope: `ZuiDialogSkinHook` (`hook/modules/zuiui/`), the cross-app ZUI dialog
restyling module. Companion analysis: `zui_component_provenance_and_hookability.md`.

## 1. Why the old anchor was wrong

The first implementation hooked `com.zui.internal.app.DialogController` method
`"c"` (no-arg, void). That name is the *outlined* assembly body on ZUI Settings,
but not on other APKs:

| APK | `DialogController` assembly routine | `c()` |
|---|---|---|
| `ZuiSettings.apk` | `installContent()` is a thin wrapper that calls private `c()`; `c()` does the `parentPanel`/`buttonPanel` lookup and the `setup*` calls | exists (private) |
| `ZuiSystemUI.apk` | `installContent()` holds the body inline | **absent** |

So the hook silently resolved nothing in SystemUI — the module only worked where
R8 happened to outline the same body into `"c"`. Any rename (even between two
APKs of the same ROM) kills it. Both APKs keep the public name `installContent`,
but betting on that is still a name bet on a minified library, and the same
library keeps changing shape per APK.

`settings.apk`'s `zui.app.a.onCreate(Bundle)` is the only external call site
(`installContent()` followed by `prepareWatchOrientation()`), i.e. content is
installed exactly once, inside `Dialog.onCreate`, and therefore inside
`Dialog.show()`.

## 2. Current anchor

* Hook `android.app.Dialog.show()` — a public platform method that cannot be
  obfuscated, and it returns only after `onCreate` → `installContent` and the
  button texts/visibilities are final.
* Recognise a ZUI dialog by walking the superclass chain for a class in package
  `zui.app.` (`MessageDialog`, `ActionDialog`, `FloatDialog` all extend the
  obfuscated `zui.app.a`, which extends `android.app.Dialog`). Class *packages*
  are stable; method names are not. No host method or field name is hardcoded.
* Locate the panel through the public `android.R.id.button1` and its parent
  (`buttonPanel`), exactly as before. The controller's `mWindow` field is no
  longer reflected — `dialog.getWindow()` is public API.

Cost is one superclass walk per `Dialog.show()` in a scoped process; non-ZUI
dialogs (Material/AppCompat) exit immediately.

## 3. Stock layout structure (`message_dialog`, `action_dialog`, `float_dialog`)

Both ZUI Settings and SystemUI ship the same shape:

```
parentPanel (LinearLayout)
├── topPanel                 title
├── titleDivider             (gone by default)
├── contentPanel             message / list
├── empty                    (View)
├── customPanel              FrameLayout
├── divider                  ← the horizontal separator to hide; a bare View
└── buttonPanel (LinearLayout, @color/button_background_zui)
    ├── Button button3
    ├── Button button2
    ├── View button_divider  ← the vertical separator, dropped by the pill style
    └── Button button1

```

Consequences used by the hook:

* The horizontal separator is always a plain `View` **immediately before** the
  button panel in its parent. The hook hides only a sibling whose exact class is
  `android.view.View`, so a layout change cannot make it hide something else.
* Any non-`Button` child of the panel is a vertical separator and is hidden when
  the pills are applied. For >2 buttons `installContent` rebuilds the panel with
  `orientation=VERTICAL`, so gaps are applied on the top/bottom edges there.
* `message_dialog_warning`, `progress_dialog_x` etc. have no button panel —
  `android.R.id.button1` is absent and the hook returns without touching them.

## 4. Filled pill style

The reference screenshot (Miuix-style dialog) has two inset, filled, rounded
buttons: a tinted primary and a neutral secondary, with no separators. ZUI
already ships that button family, so the hook reuses it instead of inventing
colors:

| Role | Resource (resolved by name from the dialog's own `Resources`) |
|---|---|
| primary fill | `drawable/button_colored_big_background_zui` (accent gradient, pressed/disabled states) |
| primary text | `color/button_colored_text_color_zui` (white) |
| neutral fill | `drawable/button_normal_big_background_zui` (surface + 1 px stroke) |
| neutral text | `color/button_normal_text_color_zui` (`#333333` / `#FFFFFF` night) |
| corner radius | `dimen/button_big_radius_zui` (baked into the drawables) |
| min height | `dimen/button_big_min_height_zui` (48 dp) |
| panel body colour | `color/dialog_background_zui` (aliases `system_dialog_background_zui`) |
| bottom rounding | `dimen/dialog_radius_zui` (20 dp) |

Presence was verified in `ZuiSettings`, `ZuiSystemUI`, `ZuiPackageInstaller`,
`ZuiLauncher` resource tables. Missing drawables degrade to a flat
`GradientDrawable` pill filled with `android.R.attr.colorAccent` (primary) or a
12 % white / 8 % black overlay (neutral).

The neutral text colour must switch away from the dialog default:
`dialog_btn_text_color_zui` resolves to `system_primary_color_zui` (accent) for
*all* dialog buttons, which is unreadable on the neutral pill.

### Geometry

Measured on the reference dialog against its 20 dp corner radius: outer margin
≈ 24–32 dp, gap ≈ 12–16 dp, row inset ≈ 12 dp vertically. The hook uses 24 dp
side padding, 12 dp gap and 12 dp vertical padding, i.e. `dialog_side_padding`
for the outer edge. The panel padding is what keeps the dialog's rounded bottom
corners: the stock bottom rounding lives on `btn_dialog_zui`, which the pills
replace, so the panel background is repainted as the body colour with the bottom
radius re-applied. Square bottom corners would otherwise show the opaque panel
against the rounded window background.

## 5. Failure behaviour

Every lookup is `runCatching`-guarded, and the whole skin runs after
`chain.proceed()` inside the hook body. If the panel, a dimen or a drawable is
missing, the dialog keeps the stock look for that element; nothing is thrown into
the host process.
