# Feature Compose Navigation Migration Plan

## Goal

Move ZTool toward a Compose-native application structure:

- One app Activity hosts app-level theme, navigation, system bars, and platform wiring.
- App pages are Compose Navigation destinations, not Activity boundaries.
- Feature packages own their `Route`, `Screen`, state wiring, and platform effects.
- Predictive-back animation state is maintained in one NavHost transition policy.

This plan intentionally does not treat existing feature Activity class names, package names,
or Manifest entries as long-term contracts. They may be deleted after their screens have
Compose route replacements.

## Target Shape

```text
com.qimian233.ztool
  MainActivity.kt
  app/
    ZToolApp.kt
    ZToolNavGraph.kt
    ZToolRoutes.kt
  feature/
    home/
    features/
    audit/
    settings/
      theme/
    packageinstaller/
    safecenter/
    systemframework/
    gametool/
    launcher/
    ota/
    systemui/
      statusbar/
      lockscreen/
      controlcenter/
    systemsettings/
      magicwindow/
      floatingwindow/
  core/
    data/
    platform/
    shell/
    ui/
```

The current codebase can migrate toward this shape gradually. A temporary bridge may keep
old Activities compiling while the active in-app path moves to Compose destinations.

## Route Boundary

Each feature page should expose:

- `XxxRoute`: creates/loads the ViewModel, collects state, handles platform effects, and
  receives `onBack`/navigation callbacks.
- `XxxScreen`: pure UI receiving state and callbacks.
- Optional `XxxDialogs` or `XxxEffects` files for large feature-specific dialogs/effects.

Activity-specific work should not live in `Screen`. Platform operations such as Toast,
Clipboard, app chooser dialogs, file pickers, overlay permission launchers, and external
system intents belong in `Route` or `core/platform`.

## Navigation Policy

All in-app feature pages should be destinations in the same Compose Navigation hierarchy
owned by `MainActivity`/the app NavHost. They must use the same transition lambdas as Home,
Features, Audit, Settings, and theme settings:

- predictive-back enabled: horizontal slide container transitions.
- predictive-back disabled: `EnterTransition.None` and `ExitTransition.None`.

`FeaturesRoute` should select feature destinations by stable feature ids/routes, not by
Activity classes.

## Migration Order

1. Create the feature route model and route-string map for all existing Features entries.
2. Migrate the simplest top-level feature pages first:
   - package installer
   - safe center
   - system framework
   - game tool
   - launcher
   - OTA
3. Migrate the System UI tree as one graph:
   - system UI aggregate
   - status bar
   - lock screen
   - control center
4. Migrate system settings detail as its own graph:
   - settings detail
   - magic-window search
   - floating-window/overlay guide controller
5. Remove old feature Activity launch paths, Manifest entries, Activity transition helpers,
   and unused Intent extras after all in-app routes are Compose-owned.
6. Split `MainActivity` into app-level files once the graph is stable:
   - `app/ZToolApp.kt`
   - `app/ZToolNavGraph.kt`
   - `app/ZToolRoutes.kt`

## First Implementation Slice

Start with a transitional bridge:

- Add stable `FeatureDestination` route ids.
- Change `FeaturesMainRoute` so it can navigate through Compose for migrated destinations.
- Add Compose destinations for package installer and safe center.
- Keep non-migrated feature cards on their existing Activity launch path until their route
  replacements land.
- Verify with `.\gradlew.bat assembleDebug`.

This slice validates the shared back animation path without requiring a risky all-at-once
feature migration.
