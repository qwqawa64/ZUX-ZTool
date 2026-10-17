# Freeform Hub Entry — Feasibility And Design

## Question

Can the module expose a new Features card (like the "系统界面组件" / UI Components
card) that consolidates the scattered small-window settings:

- `ZuiSettingsDetailScreen`: 一视界 section and 小窗与分屏 section
- `SystemUiMiscSettingsScreen`: fullscreen threshold, bottom-edge proportional
  resize, forehead removal, corner-handle auto-hide
- `LauncherSettingsScreen`: all-apps freeform entry, freeform keep-alive
- `FrameworkSettingsScreen`: force freeform on relative launch

The user's proposal is that the page should ask for a system reboot instead of
offering a scope restart. This note records the analysis and the resulting design.

## Verdict

Feasible. It is a pure front-end aggregation: no new hook module, no new
preference key, and no new LSPosed scope package is required. The only real
design constraint is restart, and reboot-only is the correct model for this page.

## Feature → owner → effective restart

| Row | Persistence / mechanism | Process that reads it | Effective restart |
| --- | --- | --- | --- |
| 去除黑名单限制 | `xposed_module_config` (`remove_blacklist`) | `OneVisionCompletion` @ `com.android.settings` | settings process |
| 一视界拓展模块 install/remove | Magisk module `zuxos_embedding` | Magisk mount at boot | system reboot |
| 自定义横屏视图 / 结果 / 已适配策略 | app-side config + floating window | ZTool only | none |
| 强制分屏 / 自由小窗 / 固定比例小窗 (SDK ≥ 36) | `/data/system/zui/ov_common_persist_user_0.xml` | ZUI window manager service | system reboot |
| 小窗贴边气泡 | `xposed_module_config` (`freeform_edge_bubble`) | `FreeformEdgeBubbleHook` @ `system_server` | system reboot |
| 强制小窗 / 强制分屏 (SDK < 36) | global `force_resizable_activities` + `Split_Screen_mandatory` | `SplitScreenMandatory` @ settings **and** `system_server` | settings process + system reboot |
| 自定义全屏阈值 (+2 sliders, preview) | `system_ui_freeform_fullscreen_threshold` etc. | `FreeformFullscreenThresholdHook` @ `com.android.systemui` | SystemUI process |
| 底边拖动等比缩放 | `system_ui_freeform_bottom_edge_resize` | `FreeformBottomEdgeResizeHook` @ SystemUI | SystemUI process |
| 消除小窗额头 | `system_ui_freeform_no_forehead` | `FreeformForeheadOverlayHook` @ SystemUI | SystemUI process |
| 自动隐藏角落把手 | `system_ui_corner_handle_auto_hide` | `FreeformCornerHandleIndicatorHook` @ SystemUI | SystemUI process |
| 所有应用显示小窗入口 | `launcher_force_freeform_entry` | `LauncherFreeformEntryHook` @ `com.zui.launcher` | launcher process |
| 小窗应用后台保活 | `freeform_keep_alive_enabled` + package list | launcher hook **and** `FreeformKeepAliveSystemHook` @ `system_server` | launcher process + system reboot |
| 关联启动时强制小窗打开 | `force_relative_app_freeform` | `ForceRelativeAppFreeform` @ `system_server` | system reboot |

Union of processes: `com.android.settings`, `com.android.systemui`,
`com.zui.launcher`, `android` / `system`.

## Why a per-scope restart button cannot work

1. `ScopeUtils.restartScope()` deliberately skips `HowToRestart.Reboot` scopes
   (framework cannot be restarted per package) but still returns `Success` when
   no package failed. A single FAB on a union that contains `android`/`system`
   would report success while the framework half never reloads.
2. 一视界 module install/remove is a Magisk mount, not a process; no `am
   force-stop` or `killall` can apply it. The `embedding_config.json` written by
   the config wizard lives in `/data/adb/modules/zuxos_embedding/` and is only
   mounted at boot.
3. 小窗应用后台保活 is a split switch (launcher + system_server). Restarting only
   the launcher leaves the OOM pin inactive.
4. `ov_common_persist_user_0.xml` is consumed by the ZUI window manager at boot;
   there is no safe "reload" entry point.

Splitting the page into "restart SystemUI / restart launcher" plus "reboot for
the rest" would add two restart paths and make the resulting state hard to
explain. A single reboot is shorter, deterministic, and already an established
pattern (`FrameworkSettingsScreen.RestartSystemDialog`, `HomeRepository.reboot`).

## Proposed entry

- New `FeatureDestination.Freeform("feature/freeform")`, shown on the Features
  grid as a cross-app card (same shape as `UiComponents`):
  `packageName = ""`, `alwaysVisible = true`, no app icon (default icon).
- `scopePackages = ScopeUtils.getScopePackages(FeatureDestination.Freeform)` =
  `[SETTINGS, SYSTEM_UI, LAUNCHER, ANDROID_SYSTEM, SYSTEM_SERVER]`. Unlike
  `UiComponents` (which leaves the list empty because the user selects the
  scope), this is a fixed union, so the existing "not in scope → request scope"
  flow on the card works and asks LSPosed for exactly the packages the page
  needs.
- `ScopeUtils.getScopes()` returns that union **only for the in-scope gate**; it
  must never be fed to `restartScope()`.
- Page sections, in reading order:
  1. 一视界 — blacklist switch, module switch, landscape-view wizard, config
     selection, adapted-strategy list.
  2. 小窗与分屏 — SDK ≥ 36: force split / force freeform / fixed-ratio + edge
     bubble; SDK < 36: 强制小窗 + 强制分屏.
  3. 系统界面小窗 — threshold switch, width/height sliders + preview,
     bottom-edge resize, forehead removal, corner-handle auto-hide.
  4. 桌面小窗 — all-apps entry switch, keep-alive switch + package picker.
  5. 关联启动 — force freeform on relative launch.
- FAB: "重启系统" with a 3-second countdown, mirroring
  `FrameworkSettingsScreen.RestartSystemDialog` (ideally extracted to
  `ui/components` and reused there).

## Wiring checklist

1. `FeatureDestination`: add enum entry, card in `FeaturesRoute`, route in
   `ZToolNavHost` + `navigationRouteIndex`.
2. `ScopeUtils`: add the union branch.
3. `SearchIndex`: add `feature_card_freeform`; re-point every moved row's
   `SearchEntry.route` to `FeatureDestination.Freeform.route` and move it to a
   new `Screen(groupTitleRes = R.string.freeform_app_name)`. `SettingItem.key`
   values stay unchanged, so the id ↔ key contract is preserved.
4. Strings (both `values/` and `values-en-rUS/`): card name/description, section
   titles that do not already exist, and the reboot FAB label. Rows reuse their
   current strings.
5. Remove the moved rows and their now-unused callbacks from the four source
   screens in the same change; keep their unrelated rows and existing restart
   FABs.
6. Data layer: a `FreeformHubRepository` that composes the four existing
   repositories (lowest risk, no key movement), plus `FreeformHubViewModel` and
   `FreeformHubUiState`.

## Main cost and risks

- The 一视界 block is the bulk of the diff: module install/remove, config
  selection dialog, `AppChooserDialog`, overlay/usage-stats permissions and the
  floating-window wizard all live in `ZuiSettingsDetailScreen` today and must be
  ported, not just re-rendered.
- The page has an SDK 36 branch; both branches must be preserved.
- Moved rows must update `SearchIndex` routes in the same change, otherwise the
  debug `SearchIndexAudit` reports drift (the unit test only checks keys, not
  routes).
- A failed `su -c reboot` must still surface a toast; the app is otherwise killed
  by a successful reboot, so success needs no UI.
- Removing rows from the source pages is a deliberate UX change; keeping both
  copies would leave two owners for the same key and duplicate search hits.

## Recommended rollout

Phase 1: new route/card/page with the rows rendered from a composite state, and
the four source screens cleaned up in the same commit.

Phase 2 (optional): if the reboot-only UX proves too heavy for SystemUI/launcher
users, add a second action that restarts just those two processes, without
changing the default.
