# ZUI UI Hook work order

A tick-list of every hookable ZUI component style, one item per unit of work. Derived from
[zui_component_style_catalog.md](zui_component_style_catalog.md) (what exists) and
[zui_component_provenance_and_hookability.md](zui_component_provenance_and_hookability.md) §3
(what can be hooked).

## How to use

* Tick `[ ]` → `[x]` when the item is implemented **and** verified on device.
* Each item names a **hook anchor** (by id from §2), a **style entry** to use instead if you
  would rather swap a whole style, and the **tokens** the item owns.
* Resolve every resource **by name** through the host `Resources`
  (`resources.getIdentifier(name, type, pkg)`); never hardcode an id or a resource number.
* **Coverage** says which of the four reference APKs define the component. A pruned consumer
  must degrade to the stock look, not crash.
* Preferred pattern: hook, `chain.proceed()`, then walk `dialog.getWindow().getDecorView()`
  and restyle by view id. Already used in this repo by `CustomQsRoundCorner`.

## 1. Legend

| Marker | Meaning |
|---|---|
| `四端` | settings, SystemUI, launcher, desktoplauncher all define it |
| `三端` | missing from SystemUI |
| `部分` | see the listed APKs |

## 2. Hook anchors

| Id | Target | Covers |
|---|---|---|
| **A1** | `com.zui.internal.app.DialogController.installContent()` | the single `setContentView` point for every `zui.app.a` dialog |
| **A2** | `com.zui.internal.app.ActionController.installContent()` | `ActionDialog` (overrides A1) |
| **A3** | `com.zui.internal.app.FloatController.installContent()` | `FloatDialog` (overrides A1) |
| **A4** | `com.zui.internal.app.MessageController.setupContent(...)` | message body, title, icon wiring |
| **A5** | `zui.app.a.a(Context,int,int,int)` | forces which `Theme_Zui_*` a dialog resolves |
| **A6** | `zui.app.MessageDialog.show()` / `zui.app.FloatDialog.showAtLocation(Rect)` | after the window exists; safest for decor-view mutation |
| **A7** | `zui.app.ProgressDialogX.onCreate(Bundle)` | loading dialog setup |
| **A8** | `zui.widget.<Widget>` constructors | per-widget drawable / thumb / divider replacement |
| **A9** | `zui.appcompat.preference.PreferenceDialogFragmentCompat.onCreateDialog` | `Preference` → ZUI dialog bridge (library level) |
| **A10** | `com.lenovo.common.widget.ZuiPreferenceDialogFragmentCompat.onCreateDialog` | same, settings only |
| **A11** | `com.lenovo.common.roundedcorner.LenovoRoundedCorner.initRoundedCorner` | settings-only preference card corners |
| **A12** | `ZuiGlobalActionsDialog.handleShow()` / `ZuiShutdownContainerView.onFinishInflate()` | SystemUI power menu — not a `Dialog` |
| **A13** | `android.app.Dialog.show()` + `zui.app.*` class check | every ZUI dialog, returns after `onCreate` → `installContent`; obfuscation-proof, used by `ZuiDialogSkinHook` |

Layers 2 (resource) and 3 (theme attribute) from provenance §3.2 / §3.3 are alternatives to
Layer 1 for any item below.

A13 (`Dialog.show()`) is what the shipped shell hook (`ZuiDialogSkinHook`) actually uses:
`installContent`'s body is outlined into a private helper in ZUI Settings but inlined in
SystemUI, so controller-level anchors are more fragile than they look. Details in
[zui_dialog_button_style.md](zui_dialog_button_style.md) §1–2.

## A. Shared dialog shell

- [ ] **A-01 对话框表面填充色**
  - 锚点：A1（重写 `dialog_background_zui` 的解析，早于 framework 值）
  - 说明：`system_dialog_background_zui` 别名到 `@android:color/system_accent1_10`（夜间 `system_neutral2_700`），
    是**唯一**落在 APK 之外的观感输入。改它要么在 Layer 2 拦 app 本地的别名，要么在 `android` 作用域拦框架取色
  - 备注：`ZuiDialogSkinHook` 只重绘**按钮区** panel 的背景（承担对话框底部圆角），对话框自身表面未动，此项仍未完成
- [ ] **A-02 对话框背景与圆角**
  - 锚点：A1 → `R.id.parentPanel`
  - 样式：`Theme.Zui.BaseDialog` 的 `android:windowBackground`
  - token：`dialog_background_zui` `dialog_background_inset_zui` `dialog_background_radius_zui` `dialog_radius_zui`
  - 覆盖：四端
- [x] **A-03 按钮区容器（填色 + 去掉按钮间分割线）**
  - 锚点：A13 → `android.R.id.button1` 的 parent（`R.id.buttonPanel`）
  - token：`button_background_zui` `dialog_button_background_normal_zui` `btn_dialog_zui` `dialog_radius_zui`
  - 状态：已由 `ZuiDialogSkinHook` 实现。按钮改为本地自建 pill（圆角、填色、配色跟随系统均可配），
    panel 背景只负责对话框底部圆角；按钮间竖分割线由“非 Button 子视图一律隐藏”去掉
- [x] **A-04 内容区与按钮区之间的分割线**
  - 锚点：A13 → buttonPanel 的前一个同级裸 `View`（不用 `R.id.divider`：资源 id 每个 APK 都不同）
  - token：`divider_width_zui`（2px）
  - 状态：已由 `ZuiDialogSkinHook` 的 `zui_dialog_hide_divider` 开关实现（默认关），设备上已用
    “强行停止”对话框验证
- [ ] **A-05 标题排版与颜色**
  - 锚点：A1 / A4 → `R.id.alertTitle`、`R.id.title_template`
  - 样式：`Theme.Zui.MessageDialog` 的 `android:windowTitleStyle` / `android:titleTextColor`
  - token：`text_title_dialog_zui` `text_size_dialog_title_zui`
  - 覆盖：四端
- [ ] **A-06 正文排版与颜色**
  - 锚点：A4 → `R.id.message`
  - token：`text_body_dialog_zui` `text_size_dialog_description_zui` `text_size_message_zui`
  - 覆盖：四端
- [ ] **A-07 对话框尺寸约束**
  - 锚点：A1 / A6
  - 样式：`Theme.Zui.Dialog.BaseAlert`（dim amount、min width）
  - token：`dialog_min_width_major_zui` `dialog_min_width_minor_zui` `alert_dialog_max_height_zui` `alert_dialog_min_height_zui` `alert_dialog_padding_zui`
  - 覆盖：四端
- [ ] **A-08 按钮文字颜色三态**
  - 锚点：A1 → 遍历 `buttonPanel` 的 `Button` 子视图
  - token：`dialog_btn_text_color_zui` `dialog_button_text_normal_zui` `dialog_button_text_disabled_zui`
  - 覆盖：四端
  - 备注：pill 样式下 `ZuiDialogSkinHook` 已按填色亮度推导 normal/disabled 文本色（不读这三个 token），
    非 pill 对话框与 pressed 态未覆盖
- [ ] **A-09 对话框按钮尺寸与内边距**
  - 锚点：A1
  - token：`button_min_width_zui` `button_min_height_zui` `button_padding_horizontal_zui` `button_padding_vertical_zui` `alert_dialog_button_bar_height_zui`
  - 覆盖：四端
  - 备注：pill 样式下已设置最小高度（`button_big_min_height_zui`）与水平内边距，其余 token 未覆盖

## B. MessageDialog

- [ ] **B-01 复选框**
  - 锚点：A4
  - token：`message_dialog_check_box_height_zui` `message_dialog_check_box_default_height_zui` `message_dialog_check_box_text_padding_zui` `message_dialog_check_box_vertical_padding_zui`
  - 覆盖：四端
- [ ] **B-02 可点击描述文字**
  - 锚点：A4（`OnDescriptionClickableTextListener`）
  - token：`message_dialog_text_vertical_margin_zui`
  - 覆盖：四端
- [ ] **B-03 对话框图标**
  - 锚点：A4
  - 覆盖：四端
- [ ] **B-04 暗色变体**
  - 样式：`Theme.Zui.MessageDialog.Dark` / `MessageDialog.Zui.Dark`
  - 覆盖：四端

## C. ActionDialog

- [ ] **C-01 列表行样式**
  - 锚点：A2 → `R.id.select_dialog_listview`
  - token：`select_dialog_item_zui` `select_dialog_option_zui` `select_dialog_item_option_zui` `dialog_option_item_text_normal_zui` `text_size_dialog_list_item_zui`
  - 覆盖：四端
- [ ] **C-02 单选 / 多选标记**
  - 锚点：A2
  - token：`select_dialog_singlechoice_zui` `select_dialog_multichoice_zui` `ic_btn_radio_*_zui` `ic_btn_check_*_zui`
  - 覆盖：四端
- [ ] **C-03 标题区尺寸**
  - 锚点：A2
  - token：`action_dialog_title_min_height_zui` `action_dialog_title_max_height_zui` `action_dialog_title_horizontal_padding_zui`
  - 覆盖：四端
- [ ] **C-04 容器与按钮组**
  - 锚点：A2
  - token：`action_dialog_background_zui` `actiondlg_background_zui` `actiondlg_2btns_bg_zui` `actiondlg_btn_bg_zui` `actiondlg_btn_bg_normal_zui` `actiondlg_btn_bg_pressed_zui`
  - 覆盖：四端
- [ ] **C-05 取消按钮独立样式**
  - 锚点：A2
  - token：`actiondlg_btn_cancel_selector_zui` `actiondlg_btn_cancel_background_zui` `actiondlg_btn_cancel_pressed_zui`
  - 覆盖：四端

## D. FloatDialog

- [ ] **D-01 容器圆角与阴影**
  - 锚点：A3 / A6（`showAtLocation(Rect)`）
  - token：`float_dialog_radius_zui` `float_dialog_shadow_padding_zui` `popup_window_corner_radius_zui`
  - 覆盖：四端
- [ ] **D-02 容器尺寸与边距**
  - 锚点：A3
  - token：`float_dialog_min_width_zui` `float_dialog_max_width_zui` `float_dialog_max_height_zui` `float_dialog_top_margin_zui` `float_dialog_bottom_margin_zui` `float_dialog_vertical_padding_zui`
  - 覆盖：四端
- [ ] **D-03 菜单项**
  - 锚点：A3
  - token：`float_dialog_item_padding_zui` `dialog_option_item_text_normal_zui`
  - 覆盖：四端
- [ ] **D-04 溢出菜单变体**
  - 样式：`Theme.Zui.FloatDialog.OverflowMenu` / `FloatDialog.Zui.OverflowMenu`
  - token：`float_dialog_overflowmenu_max_height_zui`
  - 覆盖：四端

## E. ProgressDialogX

- [ ] **E-01 旋转指示器**
  - 锚点：A7 / A8（`zui.widget.ProgressBar` 构造）
  - 样式：`Widget.Zui.ProgressBar.Large`
  - token：`progressbar_loading_zui` `progressbar_rotate_zui` `progressbar_large_zui`
  - 覆盖：三端（**SystemUI 无 ProgressDialogX**）
  - 备注：34 帧 `animation-list`，30ms/帧
  - 调研：[zui_progress_dialog_x_indicator.md](zui_progress_dialog_x_indicator.md)。实测指示器是**框架 `ProgressBar` + 本地 `AnimationDrawable`**，
    可直接 `setIndeterminateDrawable` 换成自绘 drawable；`isLegionStyleProduct()` 在本 build 恒 false，legion 帧不会出现；
    更稳的锚点是 `Dialog.setContentView`（`ResetAppsHelper` 在 `show()` 之后替换内容），调用点与进程归属见该文档 §4–§5
  - 状态：已实现。`ZuiLoadingIndicatorHook` + `LoadingIndicatorStyle`（方格、圆圈两种），
    「替换加载动画」开关决定是否替换，样式下拉决定用哪种动画，颜色可跟随系统或自选；
    `com.android.phone` 进程（`NetworkSelectActivity`）已确认留空。方格已上机验证（日夜与自定义颜色均生效），圆圈待验证
- [ ] **E-02 文案排版**
  - 锚点：A7 → `R.id.message`
  - token：`text_title_dialog_zui` `progress_dialog_padding_between_progressbar_and_textview_zui`
  - 覆盖：三端
- [ ] **E-03 自定义视图尺寸**
  - 锚点：A7
  - token：`progress_dialog_customized_view_height_zui` `progress_dialog_customized_view_bottom_padding_zui`
  - 覆盖：三端
- [ ] **E-04 对话框宽度**
  - 锚点：A7
  - token：`progress_dialog_x_width`（230dp）
  - 覆盖：三端

## F. Pickers

- [ ] **F-01 DatePickerDialogX**
  - 锚点：A1（它继承 `zui.app.a`，走基础 `installContent`）
  - token：`date_picker_dialogx_wheel_count` `date_picker_dialog_title`
  - 覆盖：三端
- [ ] **F-02 TimePickerDialogX**
  - 锚点：A1
  - token：`time_picker_dialog_title_height` `time_picker_dialog_wheel_bottom_margin` `time_picker_dialog_selector_top_divider_margin_top` `timepicker_time_label_size_zui` `time_picker_focused_text_zui`
  - 覆盖：三端
- [ ] **F-03 UnitPickerDialog**
  - 锚点：A1
  - 覆盖：三端
- [ ] **F-04 NumberPickerX 滚轮**
  - 锚点：A8
  - 样式：`Widget.Zui.NumberPickerX`
  - token：`number_picker_x_height` `number_picker_x_cover_height` `number_picker_x_cover_width` `number_picker_x_textview_width` `number_picker_x_divider_height_zui` `number_picker_x_level_0_text_zui` `number_picker_x_level_1_text_zui` `number_picker_x_level_2_text_zui` `number_picker_with_selector_wheel_zui`
  - 覆盖：四端

## G. Selection widgets

- [ ] **G-01 Switch**
  - 锚点：A8（`zui.widget.Switch` 构造）
  - 样式：`Widget.Zui.CompoundButton.Switch`
  - token：`switch_thumb_zui` `switch_track_zui` `switch_track_background_zui` `ic_switch_track_*_zui` `ic_btn_switch_thumb_*_zui` `switch_thumb_padding_left_zui` `switch_thumb_padding_right_zui`
  - 覆盖：四端
  - 调研：[zui_switch_style.md](zui_switch_style.md)。钩点确认：`zui.widget.Switch` 为公开未混淆类，
    四个安装版 APK 均定义该类，且 `preference_widget_switch_zui` 与 `Widget.Zui.CompoundButton.Switch`
    在各端一致（Settings 安装版 17.5.0.260902 已核对）。勾/叉可包 thumb（或 track）drawable 实现；
    M3 风格需 `setSwitchMinWidth(52dp)` + 透明 thumb + 自绘 track drawable，不能仅换原生 thumb/track。未实现
- [ ] **G-02 CheckBox**
  - 锚点：A8
  - 样式：`Widget.Zui.CompoundButton.CheckBox`
  - token：`ic_btn_check_{enabled,disabled,focused,hovered,pressed}_{checked,unchecked}_zui` `btn_check_anim_zui` `ic_checkbox_radiobutton_common_1/2_zui`
  - 覆盖：四端
- [ ] **G-03 RadioButton**
  - 锚点：A8
  - token：`ic_btn_radio_*_zui` `btn_radio_anim_zui` `preference_radiobutton_zui` `preference_appcompat_radiobutton_zui`
  - 覆盖：四端
- [ ] **G-04 SeekBar**
  - 锚点：A8
  - 样式：`Widget.Zui.SeekBar`
  - token：`seekbar_track_zui` `seekbar_track_background_zui` `seekbar_track_radius_zui` `seekbar_track_progress_start_zui` `seekbar_track_progress_end_zui` `seekbar_track_secondary_progress_zui` `scrubber_control_focus_mtrl_zui`
  - 覆盖：四端
- [ ] **G-05 SeekBarNps**
  - 锚点：A8
  - token：`system_seekbar_track_*_zui` `system_seekbar_progress_*_zui`
  - 覆盖：**仅 SystemUI**（QS 亮度/音量滑条，已被 `ControlCenterLongPressHook` 触及）
- [ ] **G-06 进度条族**
  - 锚点：A8
  - 样式：`Widget.Zui.ProgressBar` `.Inverse` `.Small` `.SS` `.Horizontal`
  - token：`progress_background_primary_zui` `progress_background_secondary_zui` `progress_primary_horizontal_zui` `progressbar_bg_zui` `progressbar_horizontal_zui` `lenovo_progressbar_horizontal_zui`
  - 覆盖：四端

## H. Text input

- [ ] **H-01 EditTextX**
  - 锚点：A8
  - 样式：`Widget.Zui.EditTextX`
  - token：`edit_text_zui` `edit_text_background_zui` `edit_text_background_stroke_zui` `edit_text_background_corner_radius_zui` `edit_text_border_zui` `edit_text_hint_zui` `edit_text_error_bg_zui` `edit_text_error_border_zui`
  - 覆盖：**settings / SystemUI / launcher**（desktoplauncher 无）
- [ ] **H-02 SearchViewX**
  - 锚点：A8
  - 样式：`Widget.Zui.SearchViewX`
  - token：`search_view_zui` `search_view_min_width_zui` `search_view_min_padding_zui` `search_view_label_min_width_zui` `search_view_label_text_size_zui` `textfield_search_default_zui` `textfield_search_selected_zui`
  - 覆盖：**settings / SystemUI / launcher**（desktoplauncher 无）
- [ ] **H-03 光标与选择手柄**
  - 锚点：A8 / 主题属性
  - token：`text_cursor_zui` `text_cursor_width_zui` `text_cursor_height_zui` `text_selection_zui` `ic_text_select_handle_{left,middle,right}_alpha_zui`
  - 覆盖：四端
- [ ] **H-04 自定义键盘**
  - 锚点：A8（`KeyboardViewX` / `NumberKeyboardViewX` / `StandardKeyboardViewX` / `ZuiKeyboardView`）
  - token：`zui_keyboard_view` `zui_keyboard_view_anim_in` `zui_keyboard_view_anim_out`
  - 覆盖：四端

## I. Lists

- [ ] **I-01 ListViewX 行样式**
  - 锚点：A8
  - 样式：`Widget.Zui.ListViewX`
  - token：`list_item_background_zui` `list_item_background_activated_zui` `list_item_background_focused_zui` `list_item_text_zui` `list_item_text_disabled_zui` `list_item_second_line_text_normal_zui` `list_selector_zui` `list_height_one/two/three_lines_zui`
  - 覆盖：四端
- [ ] **I-02 ListViewX 侧滑与编辑模式**
  - 锚点：A8（`SlideView` / `ListViewXEditBar`）
  - token：`listview_x_edit_title_option_text_zui` `listview_x_color_edit_title_option_text_zui`
  - 覆盖：四端
- [ ] **I-03 分隔线**
  - 锚点：A8
  - token：`list_divider_zui` `list_divider_horizontal_zui` `listview_divider_zui` `listview_divider_width_zui` `dash_divider_zui` `dash_divider_height_zui`
  - 覆盖：四端
- [ ] **I-04 FastScroll**
  - 锚点：A8
  - 样式：`Widget.Zui.FastScroll`
  - token：`fastscroll_thumb_zui` `fastscroll_thumb_default_zui` `fastscroll_thumb_pressed_zui` `fastscroll_track_zui`
  - 覆盖：四端
- [ ] **I-05 AlphabeticIndexBar**
  - 锚点：A8
  - 样式：`Widget.Zui.AlphabeticIndexBar`
  - 覆盖：四端

## J. Bars, tabs, toolbars

- [ ] **J-01 SimpleToolbar**
  - 锚点：A8
  - 样式：`Widget.Zui.SimpleToolbar`
  - token：`ic_simple_toolbar_back_normal_zui` `ic_simple_toolbar_back_pressed_zui` `tool_bar_padding_start_zui` `tool_bar_padding_end_zui`
  - 覆盖：四端
- [ ] **J-02 BottomTabBar / BottomToolBar**
  - 锚点：A8
  - 样式：`Widget.Zui.BottomTabBar` `Widget.Zui.BottomToolBar`
  - token：`system_bottom_bar_background_zui`
  - 覆盖：四端
- [ ] **J-03 ActionBar 与 Tab**
  - 锚点：A8 / 主题属性
  - 样式：`Widget.Zui.ActionBar` `.Solid` `.TabBar` `.TabText` `.TabView`、`Widget.Zui.Tab`、`Widget.Zui.TabWidget`
  - token：`action_bar_default_height_zui` `actionbar_background_zui` `actionbar_menu_text_color_zui` `actionbar_tabview_text_color_zui` `tab_background_zui` `tab_indicator_zui` `tabwidget_selected_text_color_zui`
  - 覆盖：四端
- [ ] **J-04 ColorActionBar 变体**
  - 样式：`Theme.Zui.ColorActionBar`、`Widget.Zui.ColorActionBar*`、`Widget.Zui.ColorActionButton`
  - token：`primary_colorab_text_default_zui` `secondary_colorab_text_default_zui` `ic_color_ab_back_zui` `ic_color_menu_moreoverflow_zui`
  - 覆盖：四端
- [ ] **J-05 溢出菜单图标**
  - token：`ic_menu_moreoverflow_zui` `ic_menu_moreoverflow_normal_zui` `ic_menu_moreoverflow_pressed_zui` `ic_color_menu_moreoverflow_*_zui` `overflow_menu_text_color_zui`
  - 覆盖：四端

## K. Preference UI

- [ ] **K-01 卡片背景与描边**
  - 锚点：A11（settings）/ 主题属性
  - token：`preference_card_background_zui` `preference_card_stroke_zui` `common_corner_radius_zui` `max_corner_radius_zui` `common_stroke_zui`
  - 覆盖：settings 用 `LenovoRoundedCorner`；其余走主题
- [ ] **K-02 行背景与选中态**
  - 锚点：主题属性
  - token：`preference_item_background_zui` `preference_background_focused_not_pressed_zui` `preference_background_activated_and_focused_zui` `preference_selected_background_zui` `list_item_background_focused_not_pressed_zui`
  - 覆盖：四端
- [ ] **K-03 PreferenceCategory**
  - 样式：`Preference.Zui.PreferenceCategoryX`、`Preference.Zui.Category.Horizontal`
  - token：`preference_category_bg_zui` `preference_category_text_zui` `preference_category_x_divider_zui` `preference_category_text_padding_zui` `text_size_preference_category_title_zui`
  - 覆盖：四端
- [ ] **K-04 SwitchPreference**
  - 样式：`Preference.Zui.CheckBoxPreference`
  - token：`preference_switch_offset_zui` `preference_widget_switch_zui` `preference_switch_appcompat_zui`
  - 覆盖：`zui.appcompat.preference.*` 四端；`zui.preference.SwitchPreference` **SystemUI 无**
- [ ] **K-05 SeekBarPreference**
  - 样式：`Preference.Zui.SeekBarPreference`
  - token：`preference_widget_seekbar_zui` `preference_dialog_seekbar_zui` `preference_volume_slider_zui`
  - 覆盖：四端
- [ ] **K-06 箭头与右侧状态文字**
  - token：`preference_item_arrow_zui` `preference_arrow_zui` `preference_right_state_text_zui` `preference_title_maxWidth_zui`
  - 覆盖：四端
- [ ] **K-07 内联编辑与对话框 Preference**
  - 锚点：A9 / A10
  - 样式：`Preference.Zui.InLineEditTextPreference` `Preference.Zui.DialogPreference.YesNoPreference` `Preference.Zui.RingtonePreference`
  - token：`preference_inline_edittext_zui` `preference_dialog_edittext_zui` `preference_placeholder_zui`
  - 覆盖：四端
- [ ] **K-08 Preference 列表容器**
  - token：`preference_list_content_zui` `preference_list_content_single_zui` `preference_list_fragment_zui` `dash_divider_preference_zui`
  - 覆盖：四端

## L. Feedback surfaces

- [ ] **L-01 Toast**
  - token：`toast_frame_zui` `toast_text_zui` `text_size_toast_zui`
  - 覆盖：四端
- [ ] **L-02 PopupMenu / ContextPopupMenu**
  - 样式：`Widget.Zui.PopupMenu` `.Overflow` `Widget.Zui.ContextPopupMenu`
  - token：`popup_background_zui` `popup_window_corner_radius_zui` `popup_window_padding_vertical_zui` `popup_enter_zui` `popup_exit_zui`
  - 覆盖：四端
- [ ] **L-03 SystemUI 电源菜单**
  - 锚点：**A12**（不是 `Dialog`，走 `WindowManager.addView`）
  - 样式：`Theme.Zui.ShutdownDialog`
  - 覆盖：仅 SystemUI
- [ ] **L-04 AnimTextView**
  - 锚点：A8
  - 说明：`AnimationType` 枚举，纯代码动画，无资源 token
  - 覆盖：四端

## M. Cross-cutting

- [ ] **M-01 字号梯度**
  - 样式：`TextAppearance.Zui.*`（69 个）
  - token：`text_size_{display_1..4,headline,subhead,title,large,medium,small,body_1,body_2,caption,button,dialog_title,list_item,menu,message,toast,hint}_zui`
  - 覆盖：四端
- [ ] **M-02 文字颜色体系**
  - token：`primary_text_zui` `primary_text_{default,activated}_zui` `primary_text_disable_only_zui` `secondary_text_zui` `secondary_text_{default,activated}_zui` `text_title_zui` `text_subhead_zui`
  - 覆盖：四端
- [ ] **M-03 系统主色与图标色**
  - token：`system_primary_color_zui` `system_primary_color_on_zui` `system_primary_color_on_{pressed,hovered}_zui` `system_primary_color_disabled_zui` `system_icon_primary_color_zui` `system_icon_off_color_zui` `system_icon_thumb_color_zui`
  - 覆盖：四端
- [ ] **M-04 背景与透明度**
  - token：`background_zui` `system_background_zui` `settings_background_zui` `title_background_zui` `navigationbar_background_zui` `activated_alpha_zui` `disabled_alpha_zui` `pressed_alpha_zui`
  - 覆盖：四端
- [ ] **M-05 Activity 转场动画**
  - 样式：`Animation.Zui.Activity_horizontal`
  - token：`activity_open_enter_zui` `activity_open_exit_zui` `activity_close_enter_zui` `activity_close_exit_zui`
  - 覆盖：四端
- [ ] **M-06 View 标签映射（`ZuiAppCompatViewInflater`）**
  - 说明：XML 里的 `zui.widget.*` 标签由它实例化；要批量改控件实现，这是唯一入口
  - 覆盖：**仅 settings / launcher**

## 3. Known constraints

* **Per-APK pruning.** SystemUI has no `ProgressDialogX` / `DatePickerDialogX` /
  `TimePickerDialogX` / `UnitPickerDialog` / `AlertActivity` / `ModalActivity` /
  `ListFragmentX`, and `zui.preference.SwitchPreference` is absent there.
  desktoplauncher has no `EditTextX` / `SearchViewX`. Every lookup needs a fallback.
* **Obfuscation is partial.** Public names survive, but the dialog base is `zui.app.a`,
  the widget base `zui.widget.a`, controllers have `$a…$e` inner classes and single-letter
  fields. Resolve members through the offline DexKit index, never by hardcoded name.
* **SystemUI's R class is not `zui.platform.R`.** `zui/app/MessageDialog.java` imports
  `com.android.wm.shell.R` directly there.
* **Resource replacement is process-global.** `dialog_background_zui` is shared with
  `preference_card_background_zui` and `float_dialog_background_color`; changing the
  resource changes every consumer in that process.
* **Do not hook `LayoutInflater.inflate`.** It is the hottest path in the process; use A1 /
  A2 / A3 instead.
* **Hot reload, not force-stop, is the restart mechanism** for the UI components feature.
