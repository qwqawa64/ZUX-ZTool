# PLAN: Freeform Edge Bubble (小窗贴边气泡)

AGENT-oriented implementation plan. Reference: `docs/research/oplus-float-handle-gesture-spec.md`.

## Goal

Replicate the OnePlus "shrink a freeform window into an edge bubble" feature on Lenovo ZUX:
when a freeform (小窗) task is dragged mostly offscreen, it docks fully offscreen and an
edge bubble appears; tapping the bubble restores the window; the bubble supports
full/half-hidden/free-drag states.

## Architecture

- **system_server side** (new `SystemHookModule`):
  - Publishes a hand-rolled Binder service `ztool.freeform_bubble` (raw Parcel protocol,
    no AIDL) via `ServiceManager.addService` — the only channel app→system_server.
  - Executes task operations inside the WMS global lock using ATMS reflection
    (`mRootWindowContainer.anyTaskForId` → `Task.setBounds`), holding
    restore-bounds in a server-side map.
  - Hooks `OvFreeformService.checkPermission` to pass calls from ZTool uid through
    (unlocks `IOvFreeformService` helpers).
  - Hooks `WindowContainer.setBounds(Rect)` to detect freeform tasks dragged to the
    screen edge → notifies the app via a callback binder (bubble add/remove events).
- **App side**:
  - `EdgeBubbleService` (foreground, `specialUse`) hosts a single full-screen
    `TYPE_APPLICATION_OVERLAY` window with a custom-drawn capsule View.
  - Gesture state machine copied from the Oplus spec (400 ms long-press, 50 ms re-hide,
    fling dual-threshold 1000 px/s + 100 px, 24 dp free-drag slop, spring bounce 0.22 /
    response 0.6 s, half-hidden exposes 16 dp).
  - Restore/minimize commands go through the binder bridge; hidden-API access via the
    existing `hiddenapibypass` dependency.
- **Frontend**: switch row in `ZuiSettingsDetailScreen` force-config section, new
  `PreferenceKeys.FREEFORM_EDGE_BUBBLE` (`xposed_module_config`), SearchIndex entry,
  conflict-warning row pattern reused.

## Binder protocol (descriptor `com.qimian233.ztool.hook.IFreeformBubbleBridge`)

| Code | Direction | Payload |
|---|---|---|
| 1 minimizeTask | app→server | int taskId, int side(0 left/1 right) → boolean |
| 2 restoreTask | app→server | int taskId → boolean |
| 3 listMinimized | app→server | → int[] {taskId, side, ...} |
| 4 getPackageForTask | app→server | int taskId → String |
| 5 registerEventCallback | app→server | IBinder callback |
| 11 onBubbleAdded | server→app | int taskId, int side, String pkg |
| 12 onBubbleRemoved | server→app | int taskId |

## Steps

- [ ] P1 Hook side: `FreeformEdgeBubbleHook.kt` (bridge service, ATMS resolve fallbacks,
      minimize/restore/list, checkPermission passthrough) + register in HookManager +
      `PreferenceKeys.FREEFORM_EDGE_BUBBLE`.
- [ ] P2 App side: `FreeformBubbleBridge` client (ServiceManager + HiddenApiBypass),
      `EdgeBubbleService` (manifest, specialUse FG), `EdgeBubbleView` + controller
      implementing the gesture spec.
- [ ] P3 WMS edge-drag detection hook + callback events + recursion guard.
- [ ] P4 Frontend: Repository/UiState/ViewModel fields, switch row + warning row in
      `ZuiSettingsDetailScreen`, `SearchIndex` entry, strings (values + values-en),
      manifest service declaration.
- [ ] Verify: `gradlew.bat assembleDebug` + `SearchIndexConsistencyTest`; commit
      (no-gpg-sign fallback after two GPG timeouts).
