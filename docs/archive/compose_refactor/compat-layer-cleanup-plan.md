# Compat Layer Cleanup Plan

## Goal

Eliminate "patch-Material-to-look-like-Miuix" anti-patterns in the `ZTool*` compat layer.
Replace them with proper native Miuix atomic components in Miuix branches and native Material3
components in Material branches. Unify row heights. Clean up inline style checks in pages.

## Key Principle

> In Miuix mode, use Miuix's own atomic components (BasicComponent, Switch, Slider, Checkbox,
> TextField, HorizontalDivider, etc.). In Material mode, use Material3's native implementations.
> Never hand-mix Miuix font sizes/colors onto Material `Text` composables.

## Phase Plan

### Phase 1: Refactor ZToolSwitchRow
**File:** `ui/components/ZToolSettings.kt` — `ZToolSwitchRow`

Miuix branch: replace the hand-crafted `Row { Column { Text(title+color+fontSize...); Text(summary+...)}; MiuixSwitch }`
with `MiuixBasicComponent(title, summary, startAction=icon, endActions={ MiuixSwitch(...) }, onClick=toggle)`.
This lets Miuix handle text colors, sizes, and spacing natively.

Material branch: keep the Row+Text+Switch pattern cleanly (no Miuix references).

### Phase 2: Refactor ZToolPopupMenuSettingRow
**File:** `ui/components/ZToolSettings.kt` — `ZToolPopupMenuSettingRow`

Same pattern as Phase 1: Miuix branch uses `MiuixBasicComponent` with popup trigger in `endActions`.

### Phase 3: Unify row heights
**File:** `ui/components/ZToolSettings.kt`

- Remove the extra `padding(vertical = 8.dp)` outer modifier from `ZListItem` (Material branch)
- Ensure all rows (`ZListItem`, `ZToolSwitchRow`, `ZToolPopupMenuSettingRow`) use consistent
  `minHeight` (approx 64.dp) and `horizontal = 24.dp` padding
- Miuix branches use `insideMargin = PaddingValues(vertical = 16.dp)` consistently

### Phase 4: Add missing compat components
**File:** `ui/components/ZToolSettings.kt` / `ZToolSettingsModel.kt`

| New wrapper | Miuix impl | Material3 impl | Replaces |
|---|---|---|---|
| `ZToolCheckboxRow` | `MiuixBasicComponent` + `miuix Checkbox` | Row + `Material3 Checkbox` + Text | AuditRoute, FloatingWindow, etc. |
| `ZToolSlider` | `miuix Slider` | `Material3 Slider` | LauncherSettings, FrameworkSettings |
| `ZToolOutlinedTextField` | `miuix TextField` | `Material3 OutlinedTextField` | AuditRoute, OtaSettings, etc. |
| `ZToolHorizontalDivider` | `miuix HorizontalDivider` | `Material3 HorizontalDivider` | HomeRoute, OtaSettings |

### Phase 5: Clean inline style checks in pages
**Files:**
- `settingactivity/ota/OtaSettings.kt:339-340` — replace manual `LocalZToolThemeSpec` checks
- `SettingsAboutRoute.kt:290-308` — `AboutActionRow` → use `ZListItem` instead

### Phase 6: Replace direct Material3 atom usage in pages
Scan all pages and replace raw Material3 `Checkbox`, `OutlinedTextField`, `Slider`,
`HorizontalDivider`, `Button`, `TextButton` with ZTool* wrappers.

## Build Command

```powershell
cmd.exe /c ".\gradlew.bat assembleDebug"
```

Run from `/mnt/f/GitHub/ZUX-ZTool`. If both PowerShell and cmd forms fail, stop and ask user to compile.

## Commit Rules

- One file per commit unless downstream changes cascade (e.g., changing a component signature
  requires updating all callers in the same commit)
- GPG signing may timeout on Windows — if it does, pause and ask user to commit manually
