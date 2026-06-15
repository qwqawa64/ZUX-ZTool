# <p align="center">ZTool - 更适合 ZUXOS 体质的 LSPosed 自定义模块</p>

<div align="center">
  <img src="/app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" alt="ZTool Logo">

  <a href="https://github.com/qwqawa64/ZUX-ZTool"><img alt="Static Badge" src="https://img.shields.io/badge/GitHub-ZUX--ZTool-%23ADD8E6?style=for-the-badge"></a>
  <a href="https://github.com/LSPosed/LSPosed"><img alt="Static Badge" src="https://img.shields.io/badge/Framework-LSPosed-%23F48FB1?style=for-the-badge&color=%23F48FB1">
  <a href="https://www.zuxos.com/"><img alt="Static Badge" src="https://img.shields.io/badge/Target-ZUXOS%2FZUI-%23E2231A?style=for-the-badge"></a>
  <a href="https://github.com/qwqawa64/ZUX-ZTool/commits/master/"><img alt="GitHub commits since latest release" src="https://img.shields.io/github/commits-since/qwqawa64/ZUX-ZTool/latest?style=for-the-badge"></a>
</a>
</div>

> Make ZUXOS Great, not Again.

> [!tip]
> 当前文档基于版本号: `Beta_251227_c295` 编写。
> 
> 部分功能可能也适用于 ZUI ，关于 ZUI 上模块功能的可用性我们无法担保。

## 概述

ZTool 是一个针对 ZUXOS 的 LSPosed 功能增强模块。

### 游戏优化功能

- CPU 频率修正: 修复游戏服务的 CPU 时钟读取逻辑
- 设备型号伪装: 启用拯救者游戏模式
- 音频控制: 禁用游戏模式音频优化，降低延迟
- 温度管理: 修正 SoC 温度读取
- 防误触管理: 允许自动开启防误触

### 界面定制功能

- 状态栏时钟: 支持格式和样式自定义
- 状态栏网速: 使用双排网速（同时显示上下行速率）或者使用更易读的形式
- 状态栏电量: 使用位于电池图标外部的电量百分比
- 分屏支持: 绕过应用分屏限制
- 悬浮窗口: 增强多窗口管理
- AOD 支持: 强行启用原生 Android 的 AOD 或联想为 OLED 设备设计的息屏显示
- 锁屏一言: 使用自己的一言 API 为锁屏签名添加抽卡的乐趣（笑）
- 充电信息显示: 在锁屏页面显示实际充电瓦数或握手协议瓦数
- 通知图标自定义: 允许限制状态栏通知图标最大数量；允许使用原生通知图标
- 控制中心自定义: 允许修改控制中心日期显示格式
- 字体自定义: 允许导入和使用自定义字体
- 充电动画: 允许关闭充电动画，或者为非Y700系列启用Y700充电动画
- 桌面布局: 允许自定义桌面网格布局

### 系统增强功能

- 安装器优化: 移除推荐广告和跳过警告页；允许使用原生安装器
- OTA 管理: 保留本地安装功能，获取增量更新包 / 线刷包下载链接
- 权限调整: 总是在 APP 安装时授予权限
- 杜比音效: 允许在外放时关闭杜比音效
- APP 权限管理器: 允许使用原生 APP 权限管理器
- 默认启动器优化: 解锁 Dock 栏固定应用数上限、调整划卡杀后台行为
- AI 功能强化: 自定义 AI 全局输入唤醒符
- 无视系统框架限制: 强制允许截屏、绕过“无法使用此文件夹”的限制等

### 一视界/一视窗功能

- 横屏适配: 支持强制横屏显示
- 黑名单管理: 清除应用限制名单
- 动态配置: 可手动适配平行视窗界面参数
- 解除小窗 / 分屏白名单限制

> 更多功能正在慢慢更新...

## 使用要求

- 系统: ZUXOS
- 环境: Root + LSPosed 框架

## 安装步骤

1. 下载 [Releases](https://github.com/qwqawa64/ZUX-ZTool/releases) 中的最新APK文件
2. 安装并授予 Root 权限
3. 在 LSPosed 管理器中启用并勾选作用域 APP
4. 重启系统完成激活

## 获取测试版

在项目的 [GitHub Actions](https://github.com/qwqawa64/ZUX-ZTool/actions) 页面可以获取尚未 Release 的测试版本。

可以查看 [更新日志](/更新日志.txt) 了解最新功能变更。

## 注意事项

- 本模块仅供学习交流使用，请勿用于非法用途。
- 部分功能需要 Magisk/KernelSU 模块支持。
- 部分修改可能导致设备运行异常，请提前备份数据并确保掌握紧急恢复的方法。

## 致谢

- [dantmnf](https://github.com/dantmnf) 的 [UnfuckZUI](https://github.com/dantmnf/UnfuckZUI) 项目，ZTool中的以下功能使用了此项目的实现：
  - 原生通知图标
  - 禁用全屏充电动画
  - 允许外放时禁用杜比
  - 禁用划卡杀后台
  - 阻止自动创建访客用户
  - 重启时保持屏幕方向为竖屏
  - 原生应用安装器
  - 原生权限对话框
  - 总是允许获取APP列表
  - 默认允许APP自启


