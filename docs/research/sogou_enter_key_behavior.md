# Sogou OEM IME Enter-key behavior

APK: `com.sohu.inputmethod.sogou.oem`
`1.0.android_pad_lenovo_2024.20260423163610` (versionCode 29615556).

## Findings

### Hardware Enter

- `defpackage.HRa` is the hardware-key handler used by both input modes.
  Its `a(int, KeyEvent, int, boolean, boolean)` only forwards Enter when
  `keyEvent.hasNoModifiers()` is true; it then calls `InterfaceC3977wra.f()`.
- `MF.f()` delegates to `WF.e()`, which posts message 59399 to the input
  worker. `C2531jja` handles that message by calling `C2509jaa.f()`.
- `C2509jaa.f()` either calls `performEditorAction(4)` (send) or sends a raw
  Enter key event / inserts a newline, depending on its `a` flag.
- That flag is computed in `WF.a(boolean, int, int, int)` as
  `ZC.j() && ZC.O() && ZC.N() && RUa.q() && ...`. `ZC.j()` reads the
  `title_enter_key_send_mode` preference (default `false`), `O()` reads
  `net_enter_key_switch_mode`, `N()` reads `net_enter_key_master_switch_mode`.
- Shift+Enter and Ctrl+Enter do **not** pass `hasNoModifiers()`, so `HRa`
  returns `false` and the event falls through to the target app. There is no
  code path that converts them into `performEditorAction(4)`.
- `defpackage.C2883mua` is the combination-key listener. It maps Shift/Ctrl
  combinations through `C1397Zsa`, but the default shortcut table only binds
  IME functions (soft/hard keyboard switch, Chinese/English switch, panel
  toggles, etc.). Enter is not bound to send.

### Soft-keyboard Enter

- `defpackage.UU` / `WU` / `C1617bV` treat the key label `换行` as newline and
  otherwise call `performEditorAction(4)`.
- `defpackage.C4032xSa.f(int)` uses the same split: `0` sends,
  non-zero commits `\r\n`.

## Conclusion

This APK contains no explicit "Ctrl+Enter / Shift+Enter -> send" interception.
Plain Enter can send when the OEM preference set above is enabled; modified
Enter is left to the host app, which may itself treat every Enter variant as
send. The hook therefore intercepts at `SogouIME.onKeyDown` / `onKeyUp`, where
the modifier state is still available, and performs the send/newline action
itself only for send editors (`IME_ACTION_SEND`, or a WeChat/QQ/TIM text field
whose action is not search/go/next/done).

## Hook mapping

| Mode | Enter | Shift/Ctrl+Enter |
| --- | --- | --- |
| `enter_send` | send | newline |
| `enter_newline` | newline | send |

Composing text is left to the IME: the hook probes Sogou's cached input
connection (`defpackage.MF.V().z()`, field `e`, method `j()`) and falls back to
`ComposingText` spans in `getExtractedText`.
