# Plan: Translate Chinese Comments & Log Messages to English

## Scope
- **In scope**: Chinese comments (`//`, `/* */`, KDoc) and Chinese log messages in
  `app/src/main/java/**/*.kt` (~140 files, ~2500 lines), plus `app/proguard-rules.pro` comments.
- **Excluded (per user instruction)**: Magisk/KernelSU module boilerplate —
  `app/src/main/assets/embedding/**`, `tools/convert_to_embedding.py` — must NOT be modified.
- **Excluded by task definition**: XML comments (AndroidManifest, colors.xml, drawables),
  localized string resources (`res/values*/strings.xml`), user-facing content
  (`res/raw/agreement.md`, `UpdateCheck.json`, `更新日志.txt`), and documentation
  (`*.md`, `AGENTS.MD`, `TODOS.md`).

## Hard rules for every edit
1. Only translate: (a) Chinese comments → English; (b) Chinese strings passed to
   logger/Log/Xposed log calls → English.
2. NEVER translate functional Chinese string literals:
   - Strings matched/compared against other apps' UI text (hook matching).
   - User-visible strings injected by hooks (e.g. OwnerInfo, date formatter terms
     农历/节气/时辰, network speed units, launcher labels).
   - Pinyin search data, preference keys, file names, resource names.
3. Before translating any log message string, `rg` the repo for that exact Chinese
   string elsewhere; if another file parses/matches it (e.g. audit log parser),
   leave it unchanged and report it.
4. UTF-8 encoding preserved; no reformatting; minimal diffs.
5. Uncertain strings: leave unchanged, list in the agent's final report.

## Batches (parallel general-purpose agents, disjoint file sets)
- B1: root *.kt (5) + data/** (12) + viewmodel/** (7)
- B2: dexindex/** (10) + hook/base/** (9)
- B3: hook/modules/{documentsui,gametool,launcher} (10)
- B4: hook/modules/{mobiledesktop,ota,packageinstaller,pp} (11)
- B5: hook/modules/{safecenter,setting,systemframework} (28)
- B6: hook/modules/systemui/** (29)
- B7: hook/modules/{tbengine,wallpaper} + navigation + screens/** (11)
- B8: search, service, ui, uninstall, utils/** (20)

## Verification & delivery
1. `rg '[\p{Han}]'` re-scan of `app/src/main/java` — remaining hits must be functional
   strings only (verify each manually).
2. `rg` for Chinese in embedding/tools to confirm untouched.
3. `./gradlew.bat assembleDebug` must pass.
4. Commit with `git commit --no-gpg-sign` (user cannot input passphrase; skip signing),
   message per `.gitmessage`: `chore: translate Chinese comments and log messages to English`.

## Status
- [ ] B1–B8 batches translated
- [ ] Re-scan + functional-string review
- [ ] assembleDebug passes
- [ ] Committed (no GPG signing)
