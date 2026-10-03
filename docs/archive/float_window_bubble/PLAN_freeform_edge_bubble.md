# PLAN: Freeform Edge Bubble (小窗贴边气泡) — ✅ 已完成并实机验收 (2026-09-29)

Replicates the OnePlus "shrink a freeform window into an edge bubble" feature on
Lenovo ZUX. Final architecture diverged from the original plan during bring-up; this
document reflects the SHIPPED state. Reference:
`../../research/oplus-float-handle-gesture-spec.md`.

## Shipped architecture

- **system_server side** — `hook/modules/systemframework/FreeformEdgeBubbleHook.kt`
  (`SystemHookModule`, scope `system`):
  - App↔server channel = **ordered broadcasts** (`FREEFORM_BUBBLE_COMMAND` targeted at
    package "android", result carries return values; events broadcast back to ZTool).
    A published binder service is impossible: SELinux `service_contexts` rejects
    undeclared service names.
  - Command receiver registered on `ActivityThread.getSystemContext()` (retry loop);
    uid gate root/shell/ZTool via `getSentFromUid`.
  - Commands: 1 minimizeTask, 2 restoreTask, 3 listMinimized, 4 getPackageForTask,
    5 listFreeformTasks (bounds+visibility snapshot), 9 toggle edge-probe logging.
  - Task ops via ATMS reflection (getInstance → LocalServices field-type scan fallback)
    under the WMS global lock. **Minimize = ZUI native
    `OvfWmHideShowController.bringToBack` WITHOUT its shell transition** (the TO_BACK
    transition's BLAST sync waits on the paused task and times out after 5s — the
    transition-free `setAlwaysOnTop(false)`+`moveTaskToBack`+`resumeFocusedTasks` is
    instant). Restore = `bringToFront` + bounds nudged 96px inward (horizontal AND
    vertical) so the window returns fully on-screen and clear of the 48px edge zone.
    Plain `Task.setBounds` is only a fallback — ZUI clamps freeform bounds to its slot
    layout, so bounds-offscreen docking does NOT work on ZUX.
  - `OvFreeformService.checkPermission` passthrough for the ZTool uid.
- **App side**:
  - `EdgeBubbleService` (foreground `specialUse`) polls cmd-5 every 600ms — app-side
    edge detection (the hook-side `WindowContainer.setBounds` hook never sees ZUI shell
    drags; the `onRequestedOverrideConfigurationChanged`/`OvfTaskInjector` experiments
    were reverted after they destabilized boot). Visible task flushed within 48px of
    the display edge → minimize + bubble. Task leaving freeform → bubble removed.
  - `EdgeBubbleView`: 1:1 Oplus `FloatHandleView` replica — opaque themed container
    (#f0f0f0/#444444, 94×64dp = icon 48 + 2×8 + screenMargin 30), squircle outline
    (18dp, weight≈3), `clipToOutline` + elevation 3.33dp + `#38000000` shadows, icon
    marginStart 38dp (left) / 8dp (right). Window anchored at the FULL position
    (12dp…24dp hang; final 24dp); HALF = translation ±modeShift leaving 16dp visible
    with whole-view alpha 0.45. Vertical-only dragging (x locked to anchor; side
    switching is done through the window itself). 600ms FULL showcase on every
    expansion and drag release, then auto-collapse. Bubble height persisted per task.
  - `FreeformBubbleBridge` utils + service auto-start on app launch when the switch
    is on.
- **Frontend**: switch row `settings_detail_edge_bubble` in `ZuiSettingsDetailScreen`
  force-config section, `PreferenceKeys.FREEFORM_EDGE_BUBBLE`, SearchIndex entry,
  strings (values + values-en), manifest `specialUse` service.

## Steps

- [x] P1 Hook side: `FreeformEdgeBubbleHook.kt` + HookManager registration +
      `PreferenceKeys.FREEFORM_EDGE_BUBBLE`.
- [x] P2 App side: bridge client, `EdgeBubbleService`, `EdgeBubbleView` gesture stack.
- [x] P3 Edge detection — final form: app-side 600ms polling of the cmd-5 snapshot
      (hook-side detection experiments reverted; see memory
      `oplus-bubble-replication-on-zux` for the full post-mortem).
- [x] P4 Frontend wiring + strings + manifest.
- [x] Verify: `assembleDebug` + `SearchIndexConsistencyTest` green; committed
      (no-gpg-sign after GPG timeouts).

## Verified on device (2026-09-29, user acceptance)

- [x] Dock: freeform window dragged flush to the display edge → docks behind home
      instantly (no 5s transition stall), bubble appears at that edge.
- [x] Bubble: 600ms FULL showcase → auto half-collapse (16dp sliver, 0.45 alpha,
      dimmed icon), on first dock, on expansion, and after drag release.
- [x] Tap bubble → window restored fully on-screen (96px inward margin, corner-safe),
      bubble removed; vertical position remembered per task across cycles.
- [x] Vertical-only bubble dragging with side locked; no edge-to-edge flip.
- [x] adb determinism: `am broadcast -a com.qimian233.ztool.action.FREEFORM_BUBBLE_COMMAND
      --ei cmd {1|2|3|5|9}` (uid gate allows root/shell); service auto-start after
      reboot via MainActivity.

## Known remaining polish (non-blocking)

- Drag-to-edge trigger has the 600ms poll latency (Oplus detects the drag gesture
  itself); a hook-based instant trigger (`OvfTaskInjector.configChanged2OvFreeform`)
  is the candidate if instant feel is ever required — implement in ONE commit with
  the lessons from the reverted experiments.
- Polling runs only while `EdgeBubbleService` lives; battery impact unmeasured.
