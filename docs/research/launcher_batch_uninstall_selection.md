# ZUI launcher edit-mode selection: apps vs. shortcut icons

Reverse-engineering notes behind `hook/modules/launcher/misc/BatchUninstall.kt`.
Sources are the `com.zui.launcher` APK decompiled in Jadx (same APK the hook targets);
all names below are the unobfuscated ones the hook reflects on.

## Item types (`LauncherSettings.Favorites`, from smali)

| value | constant | notes |
| --- | --- | --- |
| 0 | `ITEM_TYPE_APPLICATION` | app icon, `WorkspaceItemInfo` |
| 1 | `ITEM_TYPE_SHORTCUT` | deprecated legacy shortcut |
| 2 | `ITEM_TYPE_FOLDER` | folder |
| 4 | `ITEM_TYPE_APPWIDGET` | widget |
| 7 | `ITEM_TYPE_LEOSWIDGET` | Lenovo/LeOS widget |
| 8 | `ITEM_TYPE_RECOMMEND_SHORTCUT` | "dummy" icon for a missing app, drawn with `SettingsValue.getAppDummyIcon` |
| 9 | `ITEM_TYPE_DEEP_SHORTCUT` | deep shortcut dragged out of the app popup / drawer menu |
| 11 | `ITEM_TYPE_TASK` | task/recents shortcuts |
| 14 | `ITEM_TYPE_APP_PAIR` | split-screen pair (`AppPairInfo`) |
| 15 | `ITEM_TYPE_CLOUD_APP` | cloud-folder app, cannot be selected in edit mode |

`WorkspaceItemInfo.isAppType()` returns `itemType == 0 || itemType == 8`, i.e. the launcher
itself groups a real app icon and its "removed app" placeholder together.

## Edit-mode multi-select

`com.zui.launcher.uiextend.ZuiEditModePanel`:

- The selected set is a private `ArrayList<ItemInfo>`; `switchSelectedState(View)` adds
  `view.getTag()` to it only when the tag is a `WorkspaceItemInfo` or `AppPairInfo`
  (folders and widgets are therefore never selectable), caps the selection at 24, and
  returns early for `itemType == 15` (cloud apps).
- `getSelectedViews()` walks the workspace (`Workspace.mapOverItems`) plus folder contents
  and returns one view per selected `ItemInfo`; views for folder children are inflated on
  the fly, still carrying the `ItemInfo` tag.
- `getSelectedCount()`, `getSelectedItemIds()`, `isSelectedItem(ItemInfo)`, `isSelectedView(View)`
  are public helpers. There is no public getter for the selected `ItemInfo` list, so the
  hook reads `view.tag` from `getSelectedViews()`.

## Removing icons (launcher's own path)

`EditModeRemoveDropTarget` is the "remove icons" drop target / bottom-bar button:

- `onClick` → `m(getSelectedCount(), listenerA, null)` builds `zui.app.MessageDialog.Builder`
  (`edit_mode_remove_icons_confirm_plurals`, positive button `remove_drop_target_label`),
  window type `2038` (`TYPE_APPLICATION_OVERLAY`).
- Confirm → `ZuiEditModePanel.removeAllSelectedIcon()`.

`removeAllSelectedIcon()`:

```text
for each selected ItemInfo:
    launcher.removeItem(workspace.findViewByInfo(info), info, true, EditModeRemoveDropTarget.REMOVE_REASON)
clearSelectedItems()
workspace.f1(); workspace.addExtraEmptyScreens(); workspace.setCurrentPage(currentPage)
```

`Launcher.removeItem(View, ItemInfo, boolean, String)` deletes the icon row from the
launcher DB and the view; for `itemType == 0` it does **not** uninstall anything, and for
`itemType == 8` it skips the app-store cleanup that the popup-menu "remove" goes through
(`DeleteInfo.removeItemByInfo`). Both limits are inherited by design here: the hook only
reuses exactly what the native edit-mode remove button does.

`ZuiEditModePanel.removeSelectedIcon(List<ItemInfo>)` is *not* a removal API — it only
drops the given items from the selection (`selected.removeAll(list)` + `X(...)` to refresh
dot badges). The hook uses it to take the app icons that are about to be uninstalled out of
the selection before calling `removeAllSelectedIcon()`, so the batch uninstall keeps its
app icons until `PACKAGE_REMOVED` clears them.

## What can actually be uninstalled

`DeleteInfo.getUninstallTarget` / `Utilities.getUninstallTarget` (the rule the launcher's
own popup menu uses): `itemType == 0`, resolve through `LauncherApps.resolveActivity`, then
reject when `ApplicationInfo.flags & FLAG_SYSTEM != 0`. A failed resolve aborts. The
launcher shows `uninstall_system_app_text` when the result is null. The hook mirrors this
with `LauncherApps.resolveActivity` + `FLAG_SYSTEM`, so:

- main-user, resolvable, non-system app icon → root uninstall;
- every other selected item → remove icon.

## Icons left behind by a removed preinstalled app

The launcher keeps the "removed preinstalled" knowledge in the `Constants.REMOVED_PREINSTALLED_PACKAGES`
string set (`Utilities.isRemovedPreinstalledApp`, cleared by `resetRemovedPreinstalledApps`
on `PackageUpdatedTask.OP_ADD`). `ItemInflater.e()` also deletes widget rows whose package is
in that set. For app icons the launcher instead keeps a placeholder icon:
`ITEM_TYPE_RECOMMEND_SHORTCUT` (8) / promise icons, rendered with
`SettingsValue.getAppDummyIcon`.

Tapping such a placeholder enters the launcher's app-store download flow:

- `Launcher.showLauncherDialog(10 | 11 | 12, Bundle, boolean)` builds
  `zui.app.ActionDialog` (10, 12) or `zui.app.MessageDialog` (11) with
  `dummy_app_installed_message` / `dummy_notinstalled_app_download` /
  `dummy_notinstalled_app_cancel`.
- `LauncherDownLoadDialog.handleThreeDialog` builds another `zui.app.MessageDialog`
  ("download one / download all / cancel").

Those are the "ZUI dialogs" whose content is installed by
`com.zui.internal.app.DialogController.installContent` (MessageDialog) and
`com.zui.internal.app.ActionController` (ActionDialog). They are a *click* path, not a
*remove* path: nothing in the edit-mode remove flow can trigger them. The hook therefore
does not need to talk to `DialogController`; it only has to stop excluding item type 8 and
other non-app items from the selection.

## Consequences for the hook

1. Selection is split three ways by the rules above: root-uninstallable apps, icons to
   remove (any non-app item, plus app icons whose package no longer resolves), and
   installed app icons left untouched (system apps, other profiles, unresolvable targets).
2. Icons are removed only after the uninstall Intent was started; the kept items are
   deselected first (see above) so a failed root/uninstall never strips shortcuts.
3. A selection made only of installed system apps still reports "nothing to do" — those
   icons cannot be uninstalled by root either, and deleting them is the launcher's own
   "remove icons" action rather than this feature's.
4. Hook target methods (`onFinishInflate`, `getEditModeTranslateAnimViews`,
   `switchSelectedState`, `clearSelectedItems`, `initSelectedItemByIds`,
   `getSelectedViews`, `getSelectedCount`, `removeSelectedIcon`, `removeAllSelectedIcon`)
   are stable public names; no DexIndex lookup is required.
