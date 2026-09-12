# TB Engine（UDS 实时连接引擎）Hook 计划与实现说明

目标应用：`com.lenovo.tbengine`（ZUI 平板系统更新后台引擎，`android:persistent="true"`，无桌面入口）。
本文档记录功能范围、Hook 选点依据、前端入口和已知限制，供后续迭代参考。

## 1. 范围

本期实现 4 个 Hook（用户需求 #2/#3/#4/#5），**不含** payload 签名检查绕过（#1）与数据上报禁用（#6）：

| 开关 | 目标行为 | 状态 |
|---|---|---|
| `disable_tbengine_auto_download` | 禁用固件包自动下载 | 已实现 |
| `disable_tbengine_auto_install` | 禁用自动安装（AB 后台安装 / 重启进 Recovery） | 已实现 |
| `disable_tbengine_app_update` | 禁用预装应用/数据包自动更新 | 已实现 |
| `disable_tbengine_push` | 禁用 UPS 推送通道注册 | 已实现 |

所有开关默认关闭；关闭时引擎行为完全不变。

## 2. Hook 选点依据（基于 V1.1.1.260616 反编译结论）

tbengine 是一个状态机驱动的后台引擎（`MainService` 内两条工作流：OTA 与 AppData），
自动路径与用户手动路径在 `ServiceController` 层是**分开的方法**，因此可以精准拦截自动行为而不影响手动操作：

### 2.1 禁用自动下载（`DisableTbEngineAutoDownload`）

- hook `com.lenovo.tbengine.core.services.ServiceController.startOrResumeDownload(Context)` → no-op。
  该方法只被自动路径调用（新版本发现后的自动推进）；用户手动点击走 `userStartOrResumeDownload(Context)`，不受影响。
- hook `com.lenovo.tbengine.core.policy.OtaPolicy.setmSettingNormalAutoDownload(boolean)` → 强制参数 false，
  `getmSettingNormalAutoDownload()` → 强制返回 false。对齐 `ota` 模块 `NoAutoOtaInstall` 的做法，
  确保 Wi-Fi 自动下载策略位永远为关，防止其它代码路径重新打开。

### 2.2 禁用自动安装（`DisableTbEngineAutoInstall`）

- hook `ServiceController.startABInstalling(Context)` → no-op：阻断 A/B 无缝更新校验通过后的自动后台安装。
- hook `android.os.RecoverySystem.installPackage(Context, File)` → no-op：A-only 设备上"重启进 Recovery 安装"的总闸。
  注意该方法同时是用户确认后的重启入口，本开关语义为"禁止引擎自动重启安装"，开启后用户在联想中心手动确认重启也不会执行（开关默认关闭，文档需向用户说明）。
- hook `OtaPolicy.setmSettingNormalAutoInstall(boolean)` / `getmSettingNormalAutoInstall()` → 强制 false（夜间自动安装策略位）。

### 2.3 禁用预装应用自动更新（`DisableTbEngineAppUpdate`）

- hook `ServiceController.startOrResumeAppDataDownload(Context)` → no-op。
  AppData 工作流的自动推进（`AppDataNewVersionFound.doMyUiBackgroundJob`）与 Whatsnew 用户确认路径均收敛到该方法，
  用户路径 `userStartOrResumeAppDataDownload(Context)` 保留。

### 2.4 禁用 UPS 推送（`DisableTbEnginePush`）

- hook `com.lenovo.tbengine.core.push.PushControls.initPushChannel()` → no-op。
- hook `PushControls.registerInitReceiver()` → no-op。
  入口只有 `MyApplication.onCreate()`（PRC 区域）和设备开通完成（`SmartScenarioController`）两处，均经由上述方法。
  开关关闭时不注册 token、不向 `tb-zui.lenovo.com/engine/push-message/register` 上报。

### 2.5 通用注意事项

- tbengine 是 persistent 系统应用且带自熔断保护（Service 5 分钟内重启 ≥7 次不再拉起），
  所有 hook 的 Chain SAM 内部异常必须吞掉并记日志，绝不能抛出。
- 方法名基于该版本反编译结果；后续版本若改混淆，需要重建 DexIndex 或更新 hook 点。
- 作用域：`ScopeKeys.TB_ENGINE` 已存在，`scope.list` 已包含 `com.lenovo.tbengine`，无需新增。

## 3. 前端设计

- 新增 `FeatureDestination.TbEngine("feature/tb-engine")`，功能卡入口显示条件：`com.lenovo.tbengine` 已安装。
- `ScopeUtils.getScopes(FeatureDestination.TbEngine) = [ScopeKeys.TB_ENGINE]`，重启方式 `AmStop`
  （persistent 应用 am force-stop 后系统会自动重新拉起，等效于重启引擎）。
- 新页面 `screens/tbengine/TbEngineSettingsScreen.kt`，Repository 在 `data/tbengine/TbEngineSettingsRepository.kt`，
  ViewModel 在 `viewmodel/TbEngineSettingsViewModel.kt`。页面仅 4 个 `SettingItem.Switch` + 右下角"重启引擎"FAB（复用 Ota 页确认弹窗模式）。
- 字符串：`res/values/strings.xml` 与 `res/values-en/strings.xml` 成对添加。

## 4. 改动文件清单

后端：
- `app/src/main/java/com/qimian233/ztool/hook/modules/tbengine/DisableTbEngineAutoDownload.kt`（新增）
- `app/src/main/java/com/qimian233/ztool/hook/modules/tbengine/DisableTbEngineAutoInstall.kt`（新增）
- `app/src/main/java/com/qimian233/ztool/hook/modules/tbengine/DisableTbEngineAppUpdate.kt`（新增）
- `app/src/main/java/com/qimian233/ztool/hook/modules/tbengine/DisableTbEnginePush.kt`（新增）
- `app/src/main/java/com/qimian233/ztool/hook/base/HookManager.kt`（注册 4 个模块）
- `app/src/main/java/com/qimian233/ztool/data/keys/PreferenceKeys.kt`（新增 4 个 BoolKey）

前端：
- `app/src/main/java/com/qimian233/ztool/screens/features/FeaturesRoute.kt`（新目的地 + 卡片）
- `app/src/main/java/com/qimian233/ztool/utils/ScopeUtils.kt`（作用域映射）
- `app/src/main/java/com/qimian233/ztool/navigation/ZToolNavHost.kt`（路由 + 索引）
- `app/src/main/java/com/qimian233/ztool/screens/tbengine/TbEngineSettingsScreen.kt`（新增）
- `app/src/main/java/com/qimian233/ztool/viewmodel/TbEngineSettingsViewModel.kt`（新增）
- `app/src/main/java/com/qimian233/ztool/data/tbengine/TbEngineSettingsRepository.kt`（新增）
- `app/src/main/res/values/strings.xml`、`app/src/main/res/values-en/strings.xml`

## 5. 未实现项（后续迭代）

- **payload 签名检查绕过**：tbengine 自身只做 MD5 校验，payload 签名校验在 update_engine（native）与
  `RecoverySystem.verifyPackage`（framework）内，需要 system-framework 级 hook，且缺少"喂入第三方包"的入口，暂缓。
- **数据上报禁用**：`PromptUtils.collectUNData` 与混淆包 `a.a.a.b.*` / `com.chilkatsoft` 的上报入口分散，
  需要先为 tbengine 建立 DexIndex 离线索引再实施，暂缓。
