# ZUI frontend component library — component and style catalog

What custom components and styles the bundled ZUI UI library provides, and which style
tokens drive each of them. Provenance, bundling proof and hookability live in
[zui_component_provenance_and_hookability.md](zui_component_provenance_and_hookability.md);
this document is the inventory that report refers to.

## 1. Method and reproduction

Both passes are read-only and decompiler-free. `tools/zui_catalog.py` regenerates them:

```powershell
python tools/zui_catalog.py classes "F:\USER\Jadx项目\Lenovo\settings.apk"
python tools/zui_catalog.py keys    "F:\USER\Jadx项目\Lenovo\settings.apk" "_zui$"
python tools/zui_catalog.py keys    "F:\USER\Jadx项目\Lenovo\settings.apk" "^Widget\.Zui"
```

* **classes** parses the dex string tables and keeps descriptors under `Lzui/` and
  `Lcom/zui/internal/app/`. Exact.
* **keys** scans the `resources.arsc` string pools. Heuristic: printable length prefixes
  can attach to a key, so the script strips leading non-identifier characters before
  comparing. Names are reliable; treat any count as approximate.

Measured on `settings.apk` (the richest consumer):

| | Count |
|---|---|
| ZUI classes defined | **310** |
| ZUI-named resource keys | **864** |
| — `*_zui` tokens (drawables, colors, dimens, layouts, ids) | 612 |
| — `Widget.Zui*` styles | 87 |
| — `TextAppearance.Zui*` styles | 69 |
| — `Theme.Zui*` styles | 54 |
| — `Preference.Zui*` styles | 16 |
| — `Animation.Zui*` styles | 5 |
| — other (view inflater, mixed) | 21 |

## 2. Components

### 2.1 Dialogs — `zui.app` (32 classes)

| Class | Role |
|---|---|
| `zui.app.a` | Package-private dialog base; `extends android.app.Dialog`; resolves the theme and builds the controller |
| `zui.app.MessageDialog` | The standard alert: title, message, up to three buttons, checkbox, clickable description. `Builder` |
| `zui.app.ActionDialog` | Single/multi-choice list dialog. `Builder`, `MenuAdapter` |
| `zui.app.FloatDialog` | Anchored overflow/popup menu. `Builder`, `MenuAdapter` |
| `zui.app.ProgressDialogX` | Blocking progress with spinner + message |
| `zui.app.DatePickerDialogX` | Date wheel picker, `OnDateSetListener` |
| `zui.app.TimePickerDialogX` | Time wheel picker, `OnTimeSetListener` |
| `zui.app.UnitPickerDialog` | Generic unit/value wheel picker, `UnitItem`, `OnUnitSetListener`, `OnUnitValueChangeListenter` |
| `zui.app.AlertActivity` | Activity hosting a ZUI alert |
| `zui.app.ModalActivity` | Modal activity shell |
| `zui.app.ListFragmentX` | List fragment |
| `zui.app.ZuiContextConstants` | Feature flags (`ZuiContextConstants$Features`) |

### 2.2 Controller layer — `com.zui.internal.app` (29 classes)

ZUI's replacement for `com.android.internal.app.AlertController`. This is the layer a hook
should anchor on: one assembly point per dialog kind, stable across consumers.

| Class | Role |
|---|---|
| `DialogController` | Base: inflates the layout, resolves `buttonPanel`, wires the window |
| `MessageController` | Message body, buttons, checkbox, clickable spans |
| `ActionController` | Choice list |
| `FloatController` | Anchored menu |

### 2.3 Widgets — `zui.widget` (112 classes)

| Class | Extends / kind |
|---|---|
| `zui.widget.TextView` | TextView with the ZUI text appearance |
| `zui.widget.Switch` | Switch |
| `zui.widget.ProgressBar` | ProgressBar; default indeterminate drawable is the bundled spinner |
| `zui.widget.SeekBar` | SeekBar |
| `zui.widget.SeekBarNps` | NPS-style seekbar (SystemUI only) |
| `zui.widget.NumberPickerX` | Number wheel, `Formatter`, `OnScrollListener`, `OnValueChangeListener` |
| `zui.widget.EditTextX` | EditText |
| `zui.widget.SearchViewX` | Search field |
| `zui.widget.KeyboardViewX` / `IKeyboardViewX` | Custom keyboard surface |
| `zui.widget.NumberKeyboardViewX`, `StandardKeyboardViewX`, `ZuiKeyboard`, `ZuiKeyboardView` | Keyboard variants |
| `zui.widget.ListViewX`, `ListViewXEditBar` | List with slide actions, edit mode, float menu |
| `zui.widget.SlideView` | Swipe-reveal container |
| `zui.widget.SimpleToolbar` | Toolbar |
| `zui.widget.BottomTabBar`, `BottomBarItem`, `BottomToolBar` | Bottom navigation |
| `zui.widget.AlphabeticIndexBar` | A–Z index strip |
| `zui.widget.AnimTextView` | Text view with `AnimationType` |
| `zui.widget.PairedItemListAdapter`, `ZuiMenuListAdapter` | Adapters |
| `zui.widget.a` | Package-private widget base |

### 2.4 Preference widgets — `zui.appcompat.preference` (40) and `zui.preference` (39)

`SwitchPreference`, `PreferenceWithArrow`, `Preference`, `PreferenceCategory` in both
packages. `zui.appcompat.*` is the AppCompat lineage, `zui.preference.*` the platform one;
which one an app uses depends on the app. `Preference.Zui.*` styles cover both.

### 2.5 Support — `zui.appcompat.widget` (10), `zui.appcompat.app` (4), `zui.util` (10), `zui.themes` (6), `zui.notification` (6), `zui.view.animation` (2), `zui.performance` (2), `zui.platform` (18)

`zui.appcompat.widget.{Toolbar,ActionMenuView,ActionMenuPresenter,WindowDecorActionBar}` are
legacy wrappers around the *old* support library and fail on modern APKs.
`zui.appcompat.app.ZuiAppCompatViewInflater` is the view inflater that maps XML tags to
`zui.widget.*`. `zui.util.*` is reflection/metrics helpers, `zui.themes.ThemeManager` the
theme-service client, `zui.platform.R` the generated R class.

## 3. Styles

### 3.1 `Theme.Zui*` (54)

Root and dialog themes:

```
Theme.Zui                                    parent @android:style/Theme.Material.Light
Theme.Zui.BaseDialog                        sets android:windowBackground
Theme.Zui.Dialog                             button-bar styles
Theme.Zui.Dialog.BaseAlert                   dim amount, min width
Theme.Zui.Dialog.Alert / .FixedSize / .MinWidth / .NoActionBar / .NoFrame / .Presentation
Theme.Zui.MessageDialog [.Dark] [.Horizontal] [.NoActionBar]
Theme.Zui.ActionDialog [.Horizontal]
Theme.Zui.FloatDialog [.Horizontal] [.OverflowMenu]
Theme.Zui.ProgressDialogX
Theme.Zui.ModalActivity [.Horizontal]
Theme.Zui.ShutdownDialog
Theme.Zui.DialogWhenLarge
```

Activity and settings themes:

```
Theme.Zui.NoActionBar [.Overscan] [.Fullscreen] [.TranslucentDecor]
Theme.Zui.Horizontal
Theme.Zui.Panel
Theme.Zui.SearchBar
Theme.Zui.CompactMenu
Theme.Zui.ColorActionBar
Theme.Zui.Settings [.BaseDialog] [.CompactMenu] [.Dialog] [.Dialog.Alert]
             [.Dialog.BaseAlert] [.Dialog.Presentation] [.Horizontal] [.SearchBar]
Theme.AppCompat.Zui.ModalActivity [.Horizontal]
Theme.AppCompat.Zui.ColorActionBar
Theme.AppCompat.Zui.NoActionBar.Horizontal
```

### 3.2 `Widget.Zui*` (87)

The per-widget default styles. Grouped:

| Group | Styles |
|---|---|
| Text | `Widget.Zui.TextView`, `.TextView.SpinnerItem` |
| Buttons | `Widget.Zui.Button`, `.Big`, `.Big.Colored`, `.Small`, `.Small.Colored`, `.Borderless`, `.ButtonBar`, `.ButtonBar.AlertDialog[.Dark]`, `Widget.Zui.ButtonBar`, `Widget.Zui.ActionButton[.Overflow][.CloseMode]`, `Widget.Zui.ColorActionButton`, `Widget.Zui.SegmentedButton` |
| Lists | `Widget.Zui.ListView`, `.Dialog[.Dark]`, `.DropDown`, `.Horizontal`, `.White`, `Widget.Zui.ListViewX[.Horizontal]`, `Widget.Zui.FastScroll` |
| Edit / search | `Widget.Zui.EditText`, `Widget.Zui.EditTextX`, `Widget.Zui.AutoCompleteTextView`, `Widget.Zui.SearchView`, `Widget.Zui.SearchViewX` |
| Selection | `Widget.Zui.CompoundButton`, `.Switch`, `Widget.Zui.Spinner[.DropDown]`, `Widget.Zui.NumberPickerX` |
| Progress | `Widget.Zui.ProgressBar`, `.Inverse`, `.Large`, `.Small`, `.SS`, `.Horizontal`, `.Small.Title` |
| Bars | `Widget.Zui.ActionBar`, `.Solid`, `.TabBar`, `.TabText`, `.TabView`, `Widget.Zui.ColorActionBar[.Solid][.TabText][.TabView]`, `Widget.Zui.Toolbar[.Appcompat][.Horizontal]`, `Widget.Zui.SimpleToolbar[.Horizontal]`, `Widget.Zui.BottomTabBar[.Horizontal]`, `Widget.Zui.BottomToolBar[.Horizontal]` |
| Menus / tabs | `Widget.Zui.PopupMenu[.Overflow][.Overflow.Horizontal]`, `Widget.Zui.ContextPopupMenu`, `Widget.Zui.DropDownItem[.Spinner]`, `Widget.Zui.Tab`, `Widget.Zui.TabWidget` |
| Misc | `Widget.Zui.SeekBar`, `Widget.Zui.AlphabeticIndexBar`, `Widget.Zui.ActionMode`, `Widget.Zui.ColorActionMode` |

### 3.3 `TextAppearance.Zui*` (69)

A full type ramp: `Display1`–`Display4`, `Headline`, `Subhead[.Dark]`, `Title`,
`Medium`, `Small`, `Large`, `Caption`, `Body1`, `Body2`, `Button`, `Menu[.Inverse]`,
`ListItem`, `ListSecondLineItem`, `Notification[.Title][.Line2]`, `SearchResult`,
`Toast`, `WindowTitle`, `DialogWindowTitle.Dark`, `Inverse`, plus `.Inverse` variants and
the `TextAppearance.Zui.Widget.*` per-widget set (`ActionBar`, `Toolbar`, `PopupMenu`,
`TabWidget`, `TextView`, `EditText`, `Switch`, `Button`, `SpinnerItem`, `DropDownItem`,
`DropDownHint`, `IconMenu.Item`, `ColorActionBar.*`).

### 3.4 `Preference.Zui*` (16)

`Preference.Zui.SeekBarPreference`, `.CheckBoxPreference`, `.RingtonePreference`,
`.InLineEditTextPreference`, `.DialogPreference.YesNoPreference`, `.PreferenceCategoryX`,
`.Category.Horizontal`, `.BasePreferenceScreen`.

### 3.5 `Animation.Zui*` (5)

`Animation.Zui.Activity_horizontal` and the activity transition set.

## 4. Theme attributes — the per-dialog style hooks

Each dialog family resolves its style through a theme attribute, so an app can swap a whole
dialog style by setting one attribute. Verified in `settings.apk`:

| Attribute | Pairs with |
|---|---|
| `messageDialogStyle` / `messageDialogTheme` | `Theme.Zui.MessageDialog` |
| `actionDialogStyle` / `actionDialogTheme` | `Theme.Zui.ActionDialog` |
| `floatDialogStyle` / `floatDialogTheme` | `Theme.Zui.FloatDialog` |
| `progressDialogXStyle` / `progressDialogXTheme` | `Theme.Zui.ProgressDialogX` |
| `alertDialogStyle` / `alertDialogTheme`, `alertDialogButtonGroupStyle` | ZUI's fork of the AOSP alert attributes |

Material's `materialAlertDialogTheme`, `bottomSheetDialogTheme`, `sideSheetDialogTheme`
also appear, but those belong to the Material library the app links, not to `zui.*`.

`Theme.Zui` is where an app supplies them, and it also redefines the framework progress-bar
attributes (`android:progressBarStyle`, `…StyleLarge`, `…StyleSmall`, `…StyleHorizontal`,
`…StyleInverse`) to point at `Widget.Zui.ProgressBar*`, which is why a layout's
`?android:attr/progressBarStyleLarge` still resolves to a bundled ZUI drawable.

## 5. Style tokens by component family

612 `*_zui` keys. Grouped; regenerate the full list with the `keys` command.

### Dialog shell

`dialog_background_zui`, `dialog_background_inset_zui`, `dialog_background_radius_zui`,
`dialog_bg_dimen_zui`, `dialog_radius_zui`, `dialog_min_width_major_zui`,
`dialog_min_width_minor_zui`, `dialog_divider_zui`, `dialog_list_divider_zui`,
`dialog_list_padding_zui`, `dialog_list_selector_zui`, `dialog_list_item_background_zui`,
`dialog_btn_text_color_zui`, `dialog_button_text_normal_zui`,
`dialog_button_text_disabled_zui`, `dialog_button_background_normal_zui`,
`dialog_button_background_pressed_zui`, `dialog_button_background_focus_not_pressed_zui`,
`dialog_button_padding_vertical_zui`, `dialog_option_item_text_normal_zui`,
`dialog_option_item_text_disabled_zui`, `system_dialog_background_zui`,
`alert_dialog_padding_zui`, `alert_dialog_padding_top_zui`, `alert_dialog_max_height_zui`,
`alert_dialog_min_height_zui`, `alert_dialog_extra_height_zui`,
`alert_dialog_button_bar_height_zui`, `alert_dialog_message_view_width_zui`,
`alert_dialog_message_only_height_zui`, `divider_width_zui`, `text_title_dialog_zui`,
`text_body_dialog_zui`, `text_size_dialog_title_zui`, `text_size_dialog_button_zui`,
`text_size_dialog_list_item_zui`, `text_size_dialog_description_zui`

Per-dialog families add their own:

* **MessageDialog** — `message_dialog_check_box_height_zui`,
  `message_dialog_check_box_default_height_zui`, `message_dialog_check_box_text_padding_zui`,
  `message_dialog_check_box_vertical_padding_zui`, `message_dialog_text_vertical_margin_zui`
* **ActionDialog** — `action_dialog_background_zui`, `action_dialog_width_zui`,
  `action_dialog_max_height_zui`, `action_dialog_text_zui`,
  `action_dialog_title_min_height_zui`, `action_dialog_title_max_height_zui`,
  `action_dialog_title_horizontal_padding_zui`, `action_dialog_message_title_width_zui`,
  `action_dialog_min_width_minor_zui`, `actiondlg_2btns_bg_zui`, `actiondlg_background_zui`,
  `actiondlg_btn_bg_zui`, `actiondlg_btn_bg_normal_zui`, `actiondlg_btn_bg_pressed_zui`,
  `actiondlg_btn_cancel_selector_zui`, `actiondlg_btn_cancel_background_zui`,
  `actiondlg_btn_cancel_pressed_zui`
* **FloatDialog** — `float_dialog_radius_zui`, `float_dialog_min_width_zui`,
  `float_dialog_max_width_zui`, `float_dialog_max_height_zui`,
  `float_dialog_top_margin_zui`, `float_dialog_bottom_margin_zui`,
  `float_dialog_shadow_padding_zui`, `float_dialog_item_padding_zui`,
  `float_dialog_vertical_padding_zui`, `float_dialog_overflowmenu_max_height_zui`
* **ProgressDialogX** — `progress_dialog_customized_view_height_zui`,
  `progress_dialog_customized_view_bottom_padding_zui`,
  `progress_dialog_padding_between_progressbar_and_textview_zui`
* **Pickers** — `number_picker_x_height`, `number_picker_x_cover_height`,
  `number_picker_x_cover_width`, `number_picker_x_textview_width`,
  `number_picker_x_divider_height_zui`, `number_picker_x_level_0_text_zui`,
  `number_picker_x_level_1_text_zui`, `number_picker_x_level_2_text_zui`,
  `number_picker_with_selector_wheel_zui`, `timepicker_time_label_size_zui`,
  `time_picker_dialog_wheel_bottom_margin`, `time_picker_focused_text_zui`

### Buttons

Normal, coloured and big/small variants each carry background, stroke, text and radius
tokens: `button_background_zui`, `button_background_pressed_zui`,
`button_background_disabled_zui`, `button_background_focus_not_pressed_zui`,
`button_normal_background_zui`, `button_normal_background_pressed_zui`,
`button_normal_background_focus_not_pressed_zui`, `button_normal_big_background_zui`,
`button_normal_small_background_zui`, `button_normal_rect_stroke_zui`,
`button_normal_stroke_height_zui`, `button_normal_text_zui`, `button_normal_text_pressed_zui`,
`button_normal_text_disabled_zui`, `button_normal_text_color_zui`, `button_text_normal_zui`,
`button_text_pressed_zui`, `button_text_disabled_zui`, `button_colored_background_start_zui`,
`button_colored_background_end_zui`, `button_colored_background_pressed_{start,end}_zui`,
`button_colored_background_disabled_{start,end}_zui`, `button_colored_background_hovered_zui`,
`button_colored_big_background_zui`, `button_colored_small_background_zui`,
`button_colored_text_zui`, `button_colored_text_pressed_zui`,
`button_colored_text_disabled_zui`, `button_big_radius_zui`, `button_small_normal_radius_zui`,
`button_small_colored_radius_zui`, `button_{min,big_min,small_min}_{width,height}_zui`,
`button_padding_{horizontal,vertical}_zui`, `button_{big,small}_padding_horizontal_zui`,
`button_inset_{horizontal,vertical}_zui`, `btn_dialog_zui`, `btn_dialog_color_zui`,
`btn_background_default_zui`, `btn_background_at_list_zui`, `btn_borderless_zui`,
`system_button_normal_zui`, `system_button_normal_stroke_zui`,
`system_btn_normal_disabled_zui`, `system_button_normal_pressed_zui`,
`system_button_colored_{start,end}_zui`, `system_btn_colored_{start,end}_disabled_zui`,
`system_button_colored_{start,end}_pressed_zui`, `system_button_list_pressed_zui`

### Switch, checkbox, radio

State matrices for track, thumb and the check/radio glyphs:
`ic_btn_check_{enabled,disabled,focused,hovered,pressed}_{checked,unchecked}_zui`,
`ic_btn_radio_*`, `ic_btn_switch_track_*`, `ic_switch_track_*`,
`ic_btn_switch_thumb_*`, `ic_btn_thumb_{enabled_on,enabled_off,disabled}_zui`,
`ic_btn_thumb_enabled_{focused,hovered,pressed}_zui`, `switch_thumb_zui`,
`switch_track_zui`, `switch_track_background_zui`, `switch_thumb_padding_{left,right}_zui`,
`icon_thumb_color_zui`, `icon_thumb_color_disable_zui`, `btn_check_anim_zui`,
`btn_radio_anim_zui`

### Preference rows

`preference_card_background_zui`, `preference_card_stroke_zui`,
`preference_category_bg_zui`, `preference_category_text_zui`, `preference_category_zui`,
`preference_category_x_divider_zui`, `preference_category_text_padding_zui`,
`preference_item_background_zui`, `preference_item_arrow_zui`, `preference_arrow_zui`,
`preference_background_focused_not_pressed_zui`,
`preference_background_activated_and_focused_zui`, `preference_selected_background_zui`,
`preference_list_content_zui`, `preference_list_content_single_zui`,
`preference_list_fragment_zui`, `preference_placeholder_zui`,
`preference_radiobutton_zui`, `preference_appcompat_radiobutton_zui`,
`preference_right_state_text_zui`, `preference_title_maxWidth_zui`,
`preference_volume_slider_zui`, `preference_widget_{checkbox,seekbar,switch,start}_zui`,
`preference_appcompat_zui`, `preference_switch_appcompat_zui`,
`preference_switch_offset_zui`, `preference_dialog_{edittext,seekbar}_zui`,
`preference_inline_edittext_zui`, `dash_divider_preference_zui`

### Lists

`list_item_background_zui`, `list_item_background_{activated,focused}_zui`,
`list_item_background_focused_not_pressed_zui`, `list_item_text_zui`,
`list_item_text_{normal,disabled}_zui`, `list_item_second_line_text_normal_zui`,
`list_item_with_button_zui`, `list_selector_zui`, `list_selector_background_zui`,
`list_selector_background_focused_zui`, `list_selector_focused_zui`, `list_divider_zui`,
`list_divider_horizontal_zui`, `listview_divider_zui`, `listview_divider_width_zui`,
`list_common_padding_zui`, `list_text_vertical_padding_zui`,
`list_height_{one,two,three}_lines_zui`, `listview_x_edit_title_option_text_zui`,
`listview_x_color_edit_title_option_text_zui`, `simple_list_item_1_zui`,
`simple_list_item_2_zui`, `simple_list_item_with_icon_1_zui`, `fastscroll_thumb_zui`,
`fastscroll_thumb_{default,pressed}_zui`, `fastscroll_thumb_padding_zui`,
`fastscroll_track_zui`

### Edit text and search

`edit_text_zui`, `edit_text_background_zui`, `edit_text_background_stroke_zui`,
`edit_text_background_corner_radius_zui`, `edit_text_border_zui`, `edit_text_hint_zui`,
`edit_text_description_zui`, `edit_text_error_bg_zui`, `edit_text_error_border_zui`,
`edit_text_height_zui`, `edit_text_min_width_zui`, `edit_text_min_margin_zui`,
`edit_text_min_padding_zui`, `edit_text_max_padding_zui`, `edit_text_padding_start_zui`,
`system_edittext_background_zui`, `system_edittext_background_night_zui`,
`search_view_min_width_zui`, `search_view_min_margin_zui`, `search_view_min_padding_zui`,
`search_view_max_padding_zui`, `search_view_label_{min_width,min_height}_zui`,
`search_view_label_padding_zui`, `search_view_label_margin_start_zui`,
`search_view_label_text_size_zui`, `search_view_zui`, `textfield_search_default_zui`,
`textfield_search_selected_zui`, `textfield_searchview_zui`, `text_cursor_zui`,
`text_cursor_width_zui`, `text_cursor_height_zui`, `text_selection_zui`,
`ic_text_select_handle_{left,middle,right}_alpha_zui`

### SeekBar and progress

`seekbar_track_zui`, `seekbar_track_background_zui`, `seekbar_track_radius_zui`,
`seekbar_track_progress_{start,end}_zui`, `seekbar_track_progress_{start,end}_disable_zui`,
`seekbar_track_progress_height_zui`, `seekbar_track_background_height_zui`,
`seekbar_track_background_stroke_zui`, `seekbar_track_background_disable_zui`,
`seekbar_track_secondary_progress_zui`, `seekbar_thumb_disable_alpha_zui`,
`scrubber_control_focus_mtrl_zui`, `progress_background_{primary,secondary}_zui`,
`progress_background_horizontal_zui`, `progress_primary_horizontal_zui`,
`progress_secondary_horizontal_zui`, `progress_bg_horizontal_zui`,
`progress_bar_horizontal_height_zui`, `progressbar_bg_zui`, `progressbar_horizontal_zui`,
`progressbar_loading_zui`, `progressbar_rotate_zui`, `progressbar_{small,medium,large}_zui`,
`lenovo_progressbar_horizontal_zui`, `lenovo_progress_background_{primary,secondary}_zui`,
`lenovo_progress_background_horizontal_zui`, `lenovo_progress_bar_horizontal_height_zui`,
`system_progressbar_bg_zui`, `system_progress_horizontal_night_zui`,
`system_seekbar_track_{bg,night,night_disable}_zui`,
`system_seekbar_progress_{start,end}_zui`, `system_seekbar_progress_{start,end}_disabled_zui`

### Action bar, toolbar, tabs

`action_bar_default_height_zui`, `action_bar_icon_{height,width,max_height}_zui`,
`action_bar_home_zui`, `action_bar_title_margin_{start,end}_zui`,
`action_bar_{padding_start,padding_end}_zui`, `action_bar_horizontal_subtitle_height_zui`,
`action_bar_icon_layout_margin_zui`, `action_bar_overflow_padding_{start,end}_zui`,
`actionbar_background_zui`, `actionbar_menu_text_zui`, `actionbar_menu_text_color_zui`,
`actionbar_color_menu_text_zui`, `actionbar_tabview_bg_zui`,
`actionbar_tabview_text_color_zui`, `actionbar_tabtext_{selected,unselected}_text_color_zui`,
`actionbar_color_tabview_text_color_zui`, `actionbar_padding_{start,end}_horizontal_zui`,
`action_button_min_{height,width}_zui`, `action_menu_layout_zui`,
`action_menu_item_layout_zui`, `tool_bar_padding_{start,end}_zui`,
`tab_background_zui`, `tab_default_background_zui`, `tab_select_background_zui`,
`tab_indicator_zui`, `tab_widget_indicator_text_color_zui`,
`tabwidget_{selected,unselected}_text_color_zui`, `overflow_menu_text_color_zui`,
`text_size_action_menu_zui`, `text_size_tab_indicator_zui`,
`ic_ab_back_zui`, `ic_color_ab_back_zui`, `ic_home_back_{normal,pressed}_zui`,
`ic_color_home_back_{normal,pressed}_zui`, `ic_menu_moreoverflow_zui`,
`ic_menu_moreoverflow_{normal,pressed}_zui`, `ic_color_menu_moreoverflow_zui`,
`ic_color_menu_moreoverflow_pressed_zui`, `ic_simple_toolbar_back_{normal,pressed}_zui`

### Popup, toast, background, text colours

`popup_background_zui`, `popup_enter_zui`, `popup_exit_zui`,
`popup_window_corner_radius_zui`, `popup_window_padding_vertical_zui`, `toast_frame_zui`,
`toast_text_zui`, `background_zui`, `system_background_zui`, `settings_background_zui`,
`title_background_zui`, `navigationbar_background_zui`, `system_bottom_bar_background_zui`,
`floating_window_z_zui`, `common_corner_radius_zui`, `max_corner_radius_zui`,
`common_stroke_zui`, `primary_text_zui`, `primary_text_{default,activated}_zui`,
`primary_text_disable_only_zui`, `secondary_text_zui`,
`secondary_text_{default,activated}_zui`, `text_title_zui`, `text_subhead_zui`,
`system_primary_color_zui`, `system_primary_color_on_zui`,
`system_primary_color_on_{pressed,hovered}_zui`, `system_primary_color_disabled_zui`,
`system_primary_color_off_pressed_zui`, `system_icon_primary_color_zui`,
`system_icon_off_color_zui`, `system_icon_thumb_color_zui`,
`system_icon_thumb_disabled_zui`, `activated_alpha_zui`, `disabled_alpha_zui`,
`pressed_alpha_zui`

### Type scale

`text_size_{display_1..4, headline, subhead, title, large, medium, small, body_1, body_2,
caption, button, button_big, button_small, dialog_title, dialog_button, dialog_list_item,
dialog_description, list_item, list_subitem, menu, message, tab_indicator, toast, hint,
checkbox, home_title}_zui`

### Keyboard

`zui_keyboard_view`, `zui_keyboard_view_anim_in`, `zui_keyboard_view_anim_out`

### Activity transitions

`activity_open_enter_zui`, `activity_open_exit_zui`, `activity_close_enter_zui`,
`activity_close_exit_zui`, `popup_enter_zui`, `popup_exit_zui`

## 6. Layouts

Dialog and picker layouts are ordinary app-local layouts: `message_dialog`,
`action_dialog`, `float_dialog`, `progress_dialog_x`, `date_picker_dialog_x`,
`time_picker_dialog_x`, `unit_picker_dialog`, `zui_keyboard_view`.

## 7. What is *not* in the library

* **The final surface fill colour.** `system_dialog_background_zui` aliases
  `@android:color/system_accent1_10` (night: `system_neutral2_700`), i.e. the framework's
  Material-You tonal palette. It is the only appearance input outside the APK. See
  provenance §3.4.
* **The dialog base class.** `android.app.Dialog` is a ROM fork
  (`android.app.Dialog extends android.app.OvfBaseDialog`), so every ZUI dialog inherits
  the ROM's window-mode/freeform machinery. Reached by inheritance, not reflection.
* **The framework attribute names.** Layouts still say
  `?android:attr/progressBarStyleLarge`, but `Theme.Zui` redefines those to bundled
  `Widget.Zui.*` styles, so the graphic is app-local.

## 8. Reading this catalog when hooking

The tick-list form of this inventory — one work item per hookable unit, with its hook
anchor, style entry and per-APK coverage — is
[zui_ui_hook_work_order.md](zui_ui_hook_work_order.md).

The library is a **pruned subset per APK** — SystemUI has no `ProgressDialogX` /
`DatePickerDialogX` / `TimePickerDialogX`, and `SeekBarNps` exists only there. Always resolve
tokens by name and fall back; never hardcode an id or a resource number. The hookable
entry points, the three hook layers and the per-APK differences are in
[zui_component_provenance_and_hookability.md](zui_component_provenance_and_hookability.md) §3.
