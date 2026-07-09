# <p align="center">ZTool - 更适合 ZUXOS 体质的 LSPosed 自定义模块</p>

<div align="center">
  <img src="/app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" alt="ZTool Logo">

  <a href="https://github.com/qwqawa64/ZUX-ZTool"><img alt="Static Badge" src="https://img.shields.io/badge/GitHub-ZUX--ZTool-%23ADD8E6?style=for-the-badge"></a>
  <a href="https://github.com/LSPosed/LSPosed"><img alt="Static Badge" src="https://img.shields.io/badge/Framework-LSPosed-%23F48FB1?style=for-the-badge&color=%23F48FB1">
  <a href="https://www.zuxos.com/"><img alt="Static Badge" src="https://img.shields.io/badge/Target-ZUXOS%2FZUI-%23E2231A?style=for-the-badge"></a>
  <a href="https://github.com/qwqawa64/ZUX-ZTool/commits/master/"><img alt="GitHub commits since latest release" src="https://img.shields.io/github/commits-since/qwqawa64/ZUX-ZTool/latest?style=for-the-badge"></a>
</a>
</div>

> "Make ZUXOS Great, Not 'Again'."

> [!tip]
> 当前文档基于版本 `20260710` 编写。
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
- 状态栏网速: 使用双排网速（同时显示上下行速率）或者使用更易读的形式，支持控制刷新间隔
- 状态栏电量: 使用位于电池图标外部的电量百分比
- AOD 支持: 强行启用原生 Android 的 AOD 或联想为 OLED 设备设计的息屏显示
- 锁屏一言: 使用自己的一言 API 为锁屏签名添加抽卡的乐趣（笑）
- 充电信息显示: 在锁屏页面显示实际充电瓦数或握手协议瓦数
- 通知图标自定义: 允许限制状态栏通知图标最大数量；允许使用原生通知图标
- 控制中心自定义: 允许修改控制中心日期显示格式
- 字体自定义: 允许导入和使用自定义字体
- 充电动画: 允许关闭充电动画，或者为非Y700系列启用Y700充电动画
- 控制中心磁贴无字模式、颜色自定义、圆角半径自定义
- 控制中心模糊强度自定义
- 音量/亮度条百分比显示
- 自定义关于设备面板中的一些信息
- 亮灭屏过渡动画
- 在应用信息字段显示更多内容，例如 Target SDK ，并支持长按复制到剪贴板

### 系统更新功能
- 开启本地安装
- 获取增量更新包信息
- 获取线刷包下载链接
- 伪装 OTA 版本
- 关闭系统更新可用时的小红点提示

### 启动器功能
- 解锁 Dock 栏固定应用数上限
- 允许关闭 Dock 栏
- 桌面无字模式
- 调整划卡杀后台行为
- 最近任务显示内存信息
- 净化全局搜索
- 允许自定义桌面网格大小

### 一视界/一视窗功能

- 横屏适配: 支持强制横屏显示
- 黑名单管理: 清除应用限制名单
- 动态配置: 可手动适配平行视窗界面参数
- 解除小窗 / 分屏白名单限制
- 分屏支持: 绕过应用分屏限制
- 悬浮窗口: 增强多窗口管理

### 超级互联功能

- 移除互传开启警告
- 禁止互传自动关闭
- 自动接受互传请求

### 安装器功能

- 移除推荐广告
- 跳过警告页
- 允许使用原生安装器
- 总是在 APP 安装时授予权限

### 杂项

- 杜比音效: 允许在外放时关闭杜比音效
- APP 权限管理器: 允许使用原生 APP 权限管理器
- AI 功能强化: 自定义 AI 全局输入唤醒符
- 无视系统框架限制: 强制允许截屏、绕过“无法使用此文件夹”的限制等


> 更多功能正在慢慢更新...

## 使用要求

- 系统: ZUXOS
- 环境: Root + LSPosed 框架

> [!important]
> 和非官方 libxposed API 实现的兼容性尚未经过测试，如果您使用 Vector 等第三方框架并碰到了模块相关问题，我们可能不会响应您的 Issue 。

## 安装步骤

1. 下载 [Releases](https://github.com/qwqawa64/ZUX-ZTool/releases) 中的最新APK文件
2. 安装并授予 Root 权限
3. 在 LSPosed 管理器中启用并勾选作用域 APP
4. 重启系统完成激活

## 获取测试版

在项目的 [GitHub Actions](https://github.com/qwqawa64/ZUX-ZTool/actions) 页面可以获取尚未 Release 的测试版本。

可以查看 [更新日志](/更新日志.txt) 了解最新功能变更。通过 [待办列表](/TODOS.md) 查看可能会被添加到新版本中的功能。

## 注意事项

- 本模块仅供学习交流使用，请勿用于非法用途。
- 部分功能需要 Magisk/KernelSU 模块支持。
- 部分修改可能导致设备运行异常，请提前备份数据并确保掌握紧急恢复的方法。

## 致谢

[dantmnf](https://github.com/dantmnf) 的 [UnfuckZUI](https://github.com/dantmnf/UnfuckZUI) 项目，ZTool中的以下功能使用了此项目的实现：
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

[墨染_nlx](https://github.com/morannlx) 的 [ZUXOS+](https://github.com/morannlx/me.inkdye.zuxos) 促使本模块不断进步，并让我们知道原来还有下面这些呼声很高的功能可以实现：
  - 在设置内显示快捷跳转入口
  - 自定义关于设备面板中的信息
  - 控制中心磁贴无字模式
  - 控制中心透明度自定义
  - 控制中心磁贴颜色自定义
  - 控制中心磁贴圆角半径自定义
  - 关闭新版本系统可用的小红点提示
  - 最近任务显示内存信息
  - 支持在应用信息字段显示更多内容
