# Launcher icon switching closes the foreground task

Findings behind `data/settings/LauncherIconAliasController.kt` and the deferred alias
write in `SettingsRepository.setUseAlternativeIcon`.

## Symptom

Switching "use alternative icon" on the OOBE theme page (or the theme settings screen)
closes the app: the launcher icon changes and the user is dropped back to the home
screen, which reads like an intentional "restart to apply the icon" step.

## Cause

`SettingsRepository` swaps the alias pair (`LauncherAlias` off, `LauncherAliasAlt` on)
with `PackageManager.DONT_KILL_APP`. That flag only suppresses the process kill; the
system still finishes the task whose base intent is the launcher activity-alias that
got disabled. Reported upstream and treated as intended behavior:

- https://stackoverflow.com/questions/28436280/
- https://stackoverflow.com/questions/78090141/
- https://issuetracker.google.com/issues/279505991#comment21

Disabling the alias that launched the task is unavoidable when moving to another
alias, so no flag can keep the task alive. Whether it happens depends on which
component became the task's base intent: a fresh install always enters through the
launcher alias (OOBE always loses the app), while a screen reached through the LSPosed
`MODULE_SETTINGS` entry has `MainActivity` itself as base and survives.

## Fix

- The preference is persisted immediately, so the preview card and in-app logo follow
  the switch at once.
- `LauncherIconAliasController` records a pending alias sync and applies it from
  `onActivityStopped` once the started-activity count reaches zero. A request made while
  no activity is started applies immediately.
- A process death before that background write is covered by the existing startup
  self-heal (`MainActivity` → `healLauncherAliasState()`), which re-asserts the
  persisted choice on the next launch.
- The pair swap enables the target alias before disabling the old one: the disable is
  what can kill the process, and a kill between the two writes must not leave the
  launcher with no icon at all.
- Trade-off: the home-screen icon changes the next time the app leaves the foreground,
  not on the tap.

## Still immediate

`setLauncherIconHidden`, config restore, and the startup heal still write the alias pair
while the app may be in the foreground. Deferring them needs the requested visibility —
and the debug-only LeakCanary alias — replayed at flush time, which is a separate
change. A restore that flips the icon choice can therefore still close the task.
