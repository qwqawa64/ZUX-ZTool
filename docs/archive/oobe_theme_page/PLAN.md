# OOBE Theme Page Plan

Status: complete (2026-10-20). Verified on device: update-only replay lands on the
theme page straight after Splash, style switching re-themes live, 完成 records
`THEME=1` and closes the flow.

Goal: make the theme settings UI reusable and add a first-run page that lets the user
configure the theme, replayed for existing users via a new schema page (version 1).

## Reusable components

1. Move `screens/ztoolsettings/theme/ThemePreviewCard.kt` and
   `LauncherIconPreviewCard.kt` to `ui/components/theme/` (shared UI must not live under
   `screens/`; `ui/firstrun` must not depend on `screens/`).
2. Add `ui/components/theme/ThemeSettingsContent.kt`: previews + full settings list,
   plus the extracted `themeSettingsSections(...)` builder and the seed-color row.
3. Reduce `ThemeSettingsRoute.kt` to route plumbing + scaffold around the shared
   `ThemeSettingsContent`.
4. Move `rememberSettingsViewModel` and its factory out of `screens/ztoolsettings/
   SettingsRoute.kt` into `viewmodel/SettingsViewModelProvider.kt` so the first-run
   route shares the same activity-scoped state without importing from `screens/`.

## First-run page

5. `FirstrunPageSchema.THEME(order = 3, schemaVersion = 1)` — old users replay it
   because their acceptance map has no THEME entry.
6. `ui/firstrun/FirstrunThemePage.kt`: `PageHeader` + guidance text + shared
   `ThemeSettingsContent` + `BottomActionBar`, last page finishes the flow.
7. Route wiring in `FirstrunAgreementRoute.kt`: `FirstrunPage.Theme` enum value,
   `toFirstrunPage()` mapping, `AnimatedContent` branch, SettingsViewModel state,
   `PageHeader`/`BottomActionBar` made `internal` for the new file.

## Strings

8. `page_firstrun_theme_title`, `_summary`, `_hint`, `_finish_button` in
   `res/values/strings.xml` and `res/values-en-rUS/strings.xml`.

## Verification

9. `.\gradlew.bat assembleDebug`
10. `python tools/check_unused_strings.py` (sync 0/0, no new UNUSED)
11. `.\gradlew.bat testDebugUnitTest --tests "com.qimian233.ztool.SearchIndexConsistencyTest"`
12. `git diff --check`, review diff, commit (TODOS.md stays untouched/unstaged).

Files touched: 2 moved, 2 added, 1 added (VM provider), 5 edited, 2 string resources.
