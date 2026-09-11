# String Resource Rename Plan

Approved convention (user confirmed):

- Grammar (all tiers): `<ownership-prefix>_<path>_<meaning>_<role>`, lowercase
  snake_case, role suffix from closed set
  `title/summary/hint/label/button/message/error/name/example` (`desc` -> `summary`
  except grandfathered `<scope>_app_description`).
- Tiers by ownership:
  1. `nav_<route>` -- navigation labels mirroring `MainRoute` (separate family, NOT under common).
  2. `common_<meaning>[_<role>]` -- referenced by >= 2 unrelated app pages.
  3. `page_<page>_<meaning>[_<role>]` -- owned by one app page
     (home, features, audit, ztoolsettings, firstrun, ...).
  4. `<scope>_<feature>[_<item>]_<role>` -- Hook feature domains. Scope tokens
     follow the existing `<scope>_app_name` family: `settings, game_tool,
     system_update, package_installer, system_ui, launcher, mobile_desktop,
     system_framework, safe_center`. Sub-features under systemui:
     `status_bar, lock_screen, control_center, animation, misc, qs`.
     `<scope>_app_name/_app_description` are grandfathered unchanged.
  5. `ztool_<hook>_<role>` -- cross-process contract strings loaded via
     getIdentifier; rename REQUIRES syncing the Hook-side `STRING_*` constants
     in the same commit.
- Screens dirs map: `screens/ztoolsettings` = app settings page,
  `screens/zuisetting` = settings-scope Hook feature screens.

## Tooling

- `tools/classify_strings.py` -> `tools/string_rename_map.csv`
  (columns: old,new,batch,evidence). Owner inferred from referencing file
  paths (screens dir, viewmodel name); shared-layer refs -> COMMON_CANDIDATE.
- `tools/rename_strings.py --batch <name> [--dry-run]` -- applies CSV rows of
  one batch: declarations in values*/*.xml, `R.string.`/`@string/` refs,
  quoted literals for dynamic rows; guards: collision detection, declaration
  diff, XML validation.
- Per batch: checker (UNUSED 0, sync 0/0) + `assembleDebug` + own commit.

## Batches (risk ascending)

- [x] 1. `nav` -- gotoHomePage/gotoFeaturePage/gotoSettingsPage (3 strings). Commit dcc05cea.
- [x] 2. `page` -- app page strings: `*Fragment_title` -> `page_*_title`,
       `firstrun_*` -> `page_firstrun_*`, ztoolsettings/audit/home owned strings.
       209 names. Commit 822f0789.
- [x] 3. `common` -- shared business strings (save/cancel/loading/...). 47 names.
       Commit e2109ead.
- [x] 4. `scope` -- the bulk: prefix-transplant `<scope>[_<feature>]_` onto
       feature-owned strings, strip redundant domain aliases. 535 names.
       Commit ccb9c553.
- [x] 5. `ztool` -- ram_formatter/ram_unavailable -> `ztool_*` + Hook constants.
       Commit 6d6f8f4c.

## Status: COMPLETE

- 3340a736: leftover manual deletions committed (clean baseline).
- 819 strings now namespaced: nav 3 / page 209 / common 47 / scope 535 /
  ztool contract 7 (2 renamed + 5 pre-conforming) / grandfathered 23
  (`<scope>_app_name/_description`).
- Every batch verified: checker UNUSED 0, values/values-en-rUS sync 0/0,
  assembleDebug green.
- PreferenceKeys preference-key literals intentionally untouched (separate
  namespace; renaming them would break existing user configs).
- Tooling kept: classify_strings.py (regenerates the map), rename_strings.py
  (batch applier), remove_unused_strings.py + check_unused_strings.py
  (android.R exclusion fix included).
