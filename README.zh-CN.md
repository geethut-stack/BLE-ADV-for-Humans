<div align="center">

# BLE ADV for Humans · BLE ADV 人话版

**一款面向蓝牙广播灯具和吊扇灯的友好 Android 遥控器**
LampSmart Pro · FanLamp Pro · Zhi Jia · Zhi Guang · Zhi Mei · 以及更多

无需账号 · 无需联网 · 无需服务器 · 英文 + 简体中文

**[English](README.md)** · 简体中文

![License](https://img.shields.io/badge/license-MIT-blue)
![Android](https://img.shields.io/badge/Android-8.0%2B-green)
![Languages](https://img.shields.io/badge/UI-English%20%7C%20%E4%B8%AD%E6%96%87-orange)
![Status](https://img.shields.io/badge/status-early%20release-yellow)

<p align="center">
  <a href="https://github.com/geethut-stack/BLE-ADV-for-Humans/releases/download/0.1.0/app-debug.apk">
    <img alt="Download APK" src="https://img.shields.io/badge/Download-APK_0.1.0-9A5B00?style=for-the-badge&logo=android&logoColor=white">
  </a>
</p>

</div>

---

## 目录

1. [为什么要做这个项目](#为什么要做这个项目)
2. [功能介绍](#功能介绍)
3. [截图](#截图)
4. [支持的灯具与协议](#支持的灯具与协议)
5. [快速上手](#快速上手)
6. [配对原理](#配对原理)
7. [故障排除与常见问题](#故障排除与常见问题)
8. [技术原理概览](#技术原理概览)
9. [我们遇到的问题与实际影响](#我们遇到的问题与实际影响)
10. [如何验证正确性](#如何验证正确性)
11. [从源码构建](#从源码构建)
12. [项目结构](#项目结构)
13. [局限与路线图](#局限与路线图)
14. [参与贡献与反馈你的灯](#参与贡献与反馈你的灯)
15. [安全与隐私](#安全与隐私)
16. [致谢、许可证与免责声明](#致谢许可证与免责声明)

---

## 为什么要做这个项目

市面上有数以百万计的廉价吊扇灯、床头灯和 LED 灯带，出厂时都配有一个**小遥控器和一个配套手机 App**
（LampSmart Pro、FanLamp Pro、Zhi Jia、Zhi Guang、智美灯控 Zhi Mei Deng Kong 等）。它们既不用 Wi-Fi，也不用普通的蓝牙连接。
遥控器或 App 只是**发出简短的蓝牙广播**，灯则只听从带有它已记住的 ID 的那些广播。

这种设计成本很低，却让刚买到灯的人很头疼：

- **配对脆弱，说明又不清楚。** 灯通电后只会在几秒内接受新的控制器。LampSmart Pro 的商店页面本身就要求用户在灯接通电源后 **5 秒内**按下同步按钮。
  错过时间窗口，App 就只会显示"找不到设备"。
- **这些 App 以中文为先，翻译往往很差。** 智美灯控（Zhi Mei Deng Kong）在 App Store 的评论提到：App 找不到设备、说明不清楚、界面仍有部分中文；
  还有用户是给灯断电重启后、在大约十秒内点击配对，才终于成功。
- **许多 App 已被弃用或下架。** 下面列表中的几个 App 已不在 Google Play 上
  （参考项目称 Smart Light / Argrace 已被弃用；旧的 FanLamp / ControlSwitch 应用已被下架）。如果你的灯配的是其中之一，
  遥控器的配套 App 可能已经不存在了。
- **商店评分反映了这种挫败感。** 撰写本文时，LampSmart Pro 在 **App Store（墨西哥区）** 的评分为 **1.9 / 5，共 19 个评分**
  （仅一个地区，样本很小——但这是公开信号，不是编造的）。

### 有多少人受到影响？

没有人公布过确切数字，我们也不想编造。我们能指出的公开信息是：

| 信号 | 数值（2026 年 10 月核查） |
|---|---|
| 开源项目 **ha-ble-adv**（针对这类灯具唯一严肃的开源工作） | 约 19 个月内获得 **263 个 star、33 个 fork** |
| 该项目上提交的 issue | 共 **152** 个，其中约 **90** 个提到配对（关键词搜索，因此是上限估计） |
| LampSmart Pro 在 App Store（墨西哥区）的评分 | **1.9 / 5**，19 个评分 |

如果你有这类灯，你大概已经懂这种感觉。

### 有替代方案吗？

据我们所知，除了厂商自己的 App，**没有独立的手机 App** 可以控制这些灯。唯一的开源替代品是面向 **Home Assistant** 的
[**ha-ble-adv**](https://github.com/NicoIIT/ha-ble-adv) 集成——它是出色的作品（本项目就建立在它之上，见[致谢](#致谢许可证与免责声明)）——
但它需要一台运行中的 Home Assistant 服务器（通常是运行 Linux/Docker 的树莓派、迷你电脑或 NAS），
外加蓝牙适配器或 ESPHome 蓝牙代理。对于只想给床头灯调个光的人来说，这要求太高了。

> **本项目的目标是补上这块缺失的拼图：一款简单、免费、离线的 Android App，任何人都能安装、一分钟配对，
> 并用自己的语言使用——不需要拥有智能家居服务器。**

---

## 功能介绍

- 💡 **支持多盏灯**，每盏灯可有一个或多个*连接*（许多灯具 App 会同时广播两个协议版本，我们会保留所有有效的版本）。
- ➕ **三种添加灯的方式**
  1. **从遥控器复制**——在按灯自带遥控器（或*另一部*手机上的灯具 App）的按钮时进行监听。灯本身不会有任何改动。
  2. **粘贴抓取的数据包**——适合进阶用户（nRF Connect、蓝牙抓包）。
  3. **配对为新遥控器**——给灯断电重启，点击**配对**。如果你只有手机，这是最佳选择。
- ✅ **闪烁测试**，用来找出哪些连接真正能控制灯（灯不会给出任何反馈）。
- 🎛️ **控制功能：** 开 / 关、亮度、色温、**RGB 彩色**（协议支持时）、第二个灯、**定时关灯**。
- 📱 每盏灯都有**桌面小组件**（开/关与定时）和**启动器快捷方式**（开关切换与 1–5 分钟定时）。
- 🌐 **英文和简体中文**，可从侧边栏切换；侧边栏还包含使用说明、兼容的灯具、配对原理、关于和许可证。
- 👓 **大字号、高对比度的界面**，每个人都能轻松阅读，包括年长用户。
- 🔒 **不收集数据、无需账号、没有联网权限。**

## 截图

<p align="center">
  <img width="49%" alt="Weixin Image_20261016033634_176_12" src="https://github.com/user-attachments/assets/f1e000dc-5845-47c6-b474-ffc7c8bba66f" />
  <img width="49%" alt="Weixin Image_20261016033633_175_12" src="https://github.com/user-attachments/assets/64104740-e5ce-4a8c-a96e-f1b54d3b6da4" />
</p>

---

<p align="center">
  <img width="49%" alt="Sidebar" src="https://github.com/user-attachments/assets/38c08262-d590-486c-8a05-de33baadffa4">
  <img width="49%" alt="Add a lamp" src="https://github.com/user-attachments/assets/6c733319-1ea3-406f-9111-437a0d3f21f4">
</p>

---

## 支持的灯具与协议

支持的协议就是下列 Android 手机 App（大多数也有 iOS 版）所使用的协议。此列表是从
[ha-ble-adv](https://github.com/NicoIIT/ha-ble-adv) 的列表中**筛选出本应用目前实际能控制的灯具**（灯光；请见下方的风扇说明）。

**图例**

| 标记 | 含义 |
|---|---|
| ✅ | **已在真实灯具上测试**（LampSmart Pro，由作者测试） |
| 🧪 | **协议已验证**：与参考实现的测试数据逐字节一致，但**尚未在真实灯具上试过**——欢迎[反馈](#参与贡献与反馈你的灯) |
| 🔗 | 可以在本应用中**配对**为新遥控器 |
| 📋 | 通过**复制遥控器** / 粘贴数据包添加（没有可用的配对步骤） |

**应用列表**

| App | 状态 | 添加方式 | 备注 |
|---|---|---|---|
| [LampSmart Pro](https://play.google.com/store/apps/details?id=com.jingyuan.lamp) | ✅ | 🔗 | 参考灯具，已在真实硬件上测试 |
| [FanLamp Pro](https://play.google.com/store/apps/details?id=com.jingyuan.fan_lamp) | 🧪 | 🔗 | 与 LampSmart Pro 同一协议系列 |
| [ApplianceSmart](https://play.google.com/store/apps/details?id=com.jingyuan.smart_home) | 🧪 | 🔗 | 同一厂商；请选择 LampSmart / FanLamp 选项 |
| [Vmax smart](https://play.google.com/store/apps/details?id=com.jingyuan.vmax_smart) | 🧪 | 🔗 | 同一厂商；请选择 LampSmart / FanLamp 选项 |
| [Zhi Jia](https://play.google.com/store/apps/details?id=com.cxw.cxwblelight) | 🧪 | 🔗 | |
| [Zhi Guang](https://play.google.com/store/apps/details?id=com.cxw.zhiguang) | 🧪 | 🔗 | 两个版本：一个基于 Zhimei，一个基于 Zhijia |
| [Zhi Mei Deng Kong（智美灯控）](http://mihuan.iotworkshop.com/zhiguang/) | 🧪 | 🔗 | 不在 Play 商店 |
| [Mantra Lighting](https://play.google.com/store/apps/details?id=com.newenergy.baolilan) | 🧪 | 📋 | 型号 Coin、Groenland |
| [Smart Light / Argrace Smart](https://apkpure.com/argrace-smart/ai.argrace.oem) | 🧪 | 🔗 | 无 RGB；部分遥控器仅有部分支持；已停止维护 |
| [LE Light Pro / 乐智光Pro](https://openapi.lelight.top/dl/cqan) | 🧪 | 🔗 | 不在 Play 商店 |
| [RuiXin](https://rx-etech.com/rxzn.html) | 🧪 | 🔗 | Sanweyter 设备；无 RGB、无定时 |
| [RW.LIGHT](https://play.google.com/store/apps/details?id=com.rw.rwblelight) | 🧪 | 🔗 | 不支持分组控制；不支持 AURA / IR / FIBER 类型 |
| [Smart Elfin](https://play.google.com/store/apps/details?id=com.warpfuture.wfiot.g) | 🧪 | 🔗 | |
| [GMIMA](https://www.jasonghost.com/lampSmartGmima/) | 🧪 | 📋 | 不在 Play 商店 |
| [SmartHomeLight](https://wosk.top/zd.html) | 🧪 | 📋 | 不在 Play 商店 |
| [eWeLink](https://play.google.com/store/apps/details?id=com.coolkit) | 🧪 | 📋 | **仅限蓝牙广播（BLE ADV）设备**——**绝不**适用于标准 BLE GATT 设备。请[先确认](https://github.com/NicoIIT/ha-ble-adv/wiki/How-to-know-if-my-Physical-Remote-is-using-BLE-Advertising-to-control-my-device) |
| [Hxlight](https://play.google.com/store/apps/details?id=com.dxc.delight) / [LED-Spots](https://play.google.com/store/apps/details?id=com.hmwin.bt.broadcast) | 🧪 | 📋 | 仅支持开关、亮度、色温；无夜灯模式 |
| *旧版应用* — 'FanLamp'、'ControlSwitch'、'Lamp Smart Pro - Soft Lighting' | 🧪 | 🔗 | 已从 Play 商店下架；使用相同的 LampSmart / FanLamp 协议 |

**已筛除 / 暂时无法使用**

* [Dudugu](https://app.dudugu.com/duduguapp/app/) — **仅风扇**设备，且没有直接配对。协议已移植并通过测试，但本应用目前还没有风扇控制，因此暂时无法使用。

**吊扇：** 对于同时控制*风扇和灯*的 App（FanLamp Pro、智美灯控「风扇版」等），当前版本**只控制灯光部分**。
风扇的风速 / 方向界面是下一步计划的功能——风扇协议已经移植并通过验证。

**完全不支持：** 使用 **射频（433 MHz）或红外**遥控的灯，以及使用普通蓝牙 **GATT 连接**的设备。
不确定你的是哪一种？请阅读上方 eWeLink 条目中链接的 ha-ble-adv 维基页面。

---

## 快速上手

### 安装

<p>
  <a href="https://github.com/geethut-stack/BLE-ADV-for-Humans/releases/download/0.1.0/app-debug.apk">
    <img alt="Download APK" src="https://img.shields.io/badge/Download-APK_0.1.0-9A5B00?style=for-the-badge&logo=android&logoColor=white">
  </a>
</p>

1. 从仓库的 [Releases](../../releases) 页面下载最新的 APK（或自行构建——见[从源码构建](#从源码构建)）。
2. 当 Android 提示时，允许浏览器 / 文件管理器"安装未知来源的应用"。
3. 打开 **BLE ADV 人话版**。需要 Android 8.0 及以上版本。

### 权限（及原因）
| 权限 | 原因 |
|---|---|
| **附近的设备 → 蓝牙广播**（Android 12+） | 用于向灯*发送*指令 |
| **附近的设备 → 蓝牙扫描**（Android 12+）· **位置信息**（Android 8–11） | 仅用于*从遥控器复制*（监听模式）。旧版 Android 要求有位置权限才能进行蓝牙扫描；本应用不会使用你的位置。 |

本应用**没有联网权限**——它无法把任何数据发送到任何地方。

### 添加你的第一盏灯
1. 打开应用 → **添加灯**。
2. **任选一种**方式：
   - **配对为新遥控器** *（最简单）*：拔掉灯的电源（或关闭墙壁开关），等待 5 秒，重新通电，然后立即在灯旁点击**配对**。在「灯具 App」对话框中选择你的灯所配的 App（不确定时可试试 *LampSmart Pro* 或 *FanLamp Pro*）。
   - **从遥控器复制**：点击**开始监听**，然后按一下灯的实体遥控器上的按钮（或在另一部手机上使用它的 App）。
   - **粘贴抓取的数据包**：粘贴一条广播的十六进制数据，然后点击**解析**。
3. 在**已找到**列表中，逐个点击每个连接的**闪烁**。**保留能让灯闪动的连接**，取消勾选其他的。
4. 给灯命名，选择类型（亮度 + 色温 / RGB / 仅开关），点击**保存灯**。
5. 点击这盏灯即可控制。使用**侧边栏（☰）**查看使用说明、切换语言等。

### 小组件与快捷方式
- 长按桌面 → **小组件** → *BLE ADV 人话版*。小组件控制带有 **★** 标记的灯（在灯的页面中用「用于小组件」设置）。
- 在每盏灯里，你可以把**开 / 关快捷方式**和 **1–5 分钟定时快捷方式**添加到桌面。长按应用图标也会列出快捷方式。

---

## 配对原理

这些灯**没有反馈通道**：App 发出广播，灯在听，但不会有任何回应。

- 灯只听从包含它**已记住的 ID** 的消息。"配对"只是让灯记住一个新的 ID。
- 通电后的几秒内，大多数灯会接受新的 ID。此时遥控器 / App 会发送一条包含自己 ID 的*配对*消息。
- 大多数灯能记住多个控制器，所以配对本应用通常**不会让你原来的遥控器失效**。少数灯会忘记最早的一个。
- **复制**遥控器的 ID（监听模式）完全不需要配对：两者都能继续使用。
- 许多灯具 App 会**同时发送两个协议版本**（V1 和 V2），它们的 ID 不同。这就是一盏灯可能有多个*连接*的原因；应用会保留所有有效的连接。
- 因为灯从不回应，应用使用**闪烁测试**，并记住**最后发送的指令**（它无法得知真实状态）。

---

## 故障排除与常见问题

**灯没有任何反应。**
靠近灯。关闭灯原来的 App（它可能产生干扰）。打开该灯 → *编辑与测试* → 逐个点击每个连接的**闪烁**。给灯断电重启后重新配对。

**配对"没成功"。**
你可能错过了短暂的配对窗口。拔电、等待约 5 秒、重新通电，然后立即点击**配对**。让手机靠近灯。如果你有多盏灯，配对时请把其他灯完全断电。

**为什么不能复制同一部手机上运行的原 App？**
Android 无法接收自己手机发出的蓝牙广播。请改用灯的实体遥控器、另一部手机、粘贴数据包，或直接配对。

**配对后原来的遥控器会失效吗？**
通常不会（大多数灯可以记住多个）。有些灯会忘记最早的一个；如果原遥控器不能用了，请重新配对它。

**开 / 关切换显示的状态不对。**
灯从不报告状态；应用只记得最后发送的指令。如果有人用过实体遥控器，请按一次开或关来重新同步。

**定时功能没有反应。**
定时功能发送的是灯*自己的*定时指令，取决于灯的固件。它在作者的 LampSmart Pro 上可用；其他灯可能不支持
（当没有任何连接支持定时时，定时卡片会被隐藏）。

**能在 iPhone 上用吗？**
不能。iOS 不允许应用广播任意的广播数据；本应用仅支持 Android。

**需要 root 吗？会耗电吗？**
不需要 root。只有你按下按钮时，手机才会发射一秒左右。

**我的品牌不在列表里。**
请提交 issue——如果它使用蓝牙广播，可能是某个已支持协议的变体。

---

## 技术原理概览

```
[ UI ] -> LampController -> entity logic -> command translators -> brand codec -> advertisement -> Android BLE advertiser -> lamp
   (开、亮度、RGB、定时)    (ble_adv 规则)      (生成的表)            (加密/格式)       (UUID 列表 / 服务数据 / 厂商数据)
```

1. **无连接：** 应用广播 BLE *广播包*；灯扫描并执行匹配的广播。
2. **编解码器（codec）：** 每个品牌都会把一条简短指令（命令 + 参数 + ID + 滚动计数器）用自己的校验和、白化 / 异或或 AES 包装起来。
   ha-ble-adv 的全部 53 个 codec 都已移植到 Kotlin（`core/`）。
3. **生成的表：** 每个 codec 的头部 / 长度参数和**指令转换表**（"亮度 50% → 这些字节"）都由 Python 源码生成到 `codec_specs.txt`，没有任何手工重打。
4. **Android 编码：** Android 只允许应用构建 UUID 列表、服务数据和厂商数据，所以每个 codec 的负载都会映射到这些格式上
   （例如 26 字节的负载会变成 13 个 16 位 UUID）。
5. **每盏灯的身份：** 每个*连接*保存 codec + ID + 滚动计数器 / 种子（每次发送后更新）。

---

## 我们遇到的问题与实际影响

做这个项目让我们明白了为什么厂商 App 这么难用。以下是对实际使用有影响的问题：

1. **根本没有可连接的东西。** 这些灯没有蓝牙连接（GATT），也没有 Wi-Fi，所以普通的"扫描并连接"工具找不到任何有用的东西。
   我们不得不录下手机的蓝牙流量（HCI snoop 日志）并解码广播。*实际影响：* 任何针对这些灯的工具都必须精确地使用这种广播协议。
2. **每盏灯都有自己的秘密 ID。** 灯会忽略所有不带它已记住 ID 的消息，所以通用的"万能遥控器"行不通——每盏灯都需要学习或配对它的身份。
   *影响：* 应用围绕"灯 + 身份"来设计，并提供三种获取身份的方式。
3. **一盏灯，多个身份。** 原版 App 会交替广播两个协议版本（V1 和 V2），它们的 ID *不同*。
   *影响：* 一盏灯可以有多个*连接*，而**闪烁测试**就是找出真正有效的那些的办法。
4. **重放录音是不够的。** 数据包里有滚动计数器、种子、校验和以及混淆 / AES。重复发送复制来的数据包会被忽略。
   *影响：* 我们实现了完整的编码器，并逐字节验证。
5. **配对是一个没有反馈的时间窗口。** 灯只在通电后很短的时间内接受新 ID，并且从不确认。
   *影响：* 提供引导步骤、配对按钮，以及用闪烁来验证。
6. **Android 听不到自己。** 显而易见的"从同一部手机上的原 App 学习"是不可能的。
   *影响：* 监听模式需要遥控器或第二部手机；粘贴和配对覆盖其余情况。
7. **Android 限制了能广播的内容。** 只允许 UUID 列表、服务数据和厂商数据；小小的"标志（flags）"字节由系统控制，
   传统广播只有 31 字节，而且有一种遥控器格式使用的是根本无法发送的原始射频字节。
   *影响：* 我们把负载映射到 Android 允许的格式上，数据包过大时改用不可连接模式重试，并把无法发送的 codec 标记为"无法从 Android 发送"。
   已测试的灯和参考解码器一样会忽略标志字节。
8. **扫描结果带有零填充。** Android 的扫描记录末尾有零字节，会破坏长度检查。*影响：* 解码器会在去掉填充后重试。
9. **状态未知。** 灯从不报告开 / 关、亮度或颜色。*影响：* 切换按钮使用最后发送的指令，如果你用了其他遥控器就可能出现偏差。
10. **定时功能不是标准功能。** 参考项目不会发送灯端定时指令（它只能从遥控器那里看到）。我们复用了观察到的指令；
    它们在作者的灯上可用，但取决于固件。
11. **硬件稀缺。** 只有一个系列的灯可用于真实测试。*影响：* 其他品牌是对照参考代码的测试数据验证的，而不是在硬件上——
    因此才有 ✅ / 🧪 标记，以及对[灯具反馈](#参与贡献与反馈你的灯)的请求。
12. **身份的隐私。** 早期代码里包含作者自己灯的 ID。发布前已移除；现在 ID 只保存在用户的手机上。
13. **语言与无障碍。** 厂商 App 以中文为先，英文不完整。*影响：* 完整的英文 + 简体中文界面、大字号、粗大的控件和高对比度分区。

---

## 如何验证正确性

- **来自真实实现的标准测试向量（golden vectors）。** `tools/gen_vectors2.py` 会运行 ha-ble-adv 的*真实 Python codec*，并记录它们的输出。Kotlin 移植版必须与之一致：
  **318 个数据包向量**（编码 → 解码 → 完整扫描流程）、**236 个指令转换向量**（实体变化 → 指令字节）和 **40 个色温向量**，覆盖全部 53 个 codec。
- **一次真实抓包。** 作者的 LampSmart Pro 抓包可被解码并逐字节重新编码。
- **生成的数据可复现。** `tools/gen_specs.py` 会重新生成 `codec_specs.txt`；两个工具都能精确复现已提交的文件。
- **这些*不能*证明的：** 某一盏真实的灯一定会有反应。这正是需要硬件反馈的原因。

在 Android Studio 中运行 `CoreVectorsTest` 即可执行这些检查。

---

## 从源码构建

要求：**Android Studio**（Koala 或更新版本）、JDK 17、一部 Android 8.0+ 的手机（需要真机——模拟器无法发射 BLE）。

```bash
git clone <this repository>
# 在 Android Studio 中打开该文件夹，等待 Gradle 同步，然后运行
```

重新生成协议数据 / 测试（可选，需要 Python 3.12+）：

```bash
git clone https://github.com/NicoIIT/ha-ble-adv
pip install pycryptodome
HA_BLE_ADV=./ha-ble-adv python3 tools/gen_specs.py       # -> app/src/main/resources/codec_specs.txt
HA_BLE_ADV=./ha-ble-adv python3 tools/gen_vectors2.py    # -> app/src/test/.../GoldenVectors.kt
```

---

## 项目结构

```
app/src/main/java/com/BLE ADV for Humans/
  MainActivity / ControlActivity / AddDeviceActivity / InfoActivity / BaseActivity   界面 + 侧边栏
  Ui.kt                         主题化界面工具包（卡片、粗滑块、分隔线）
  LampController.kt             用户操作 -> 实体逻辑 -> codec -> 广播器
  LampAdvertiser.kt             Android BLE 广播（UUID 列表、服务数据、厂商数据）
  LampDevice.kt                 灯、连接、持久化
  Shortcuts / ShortcutActivity / LampWidgets / LampActionReceiver   快捷方式 + 小组件
  core/                         纯 Kotlin 协议层（不依赖 Android）
    Spec.kt  Translators.kt  SpecCodec.kt  Models.kt  Bits.kt
    FanLampCodecs.kt  Brands1.kt  Brands2.kt  Brands3.kt     各品牌 codec
app/src/main/resources/codec_specs.txt    【生成】codec + 指令转换表
app/src/main/res/                         资源，英文（values）+ 中文（values-zh）
app/src/test/.../core/                    【生成】标准测试向量 + 检查
tools/                                    生成器（Python）
```

---

## 局限与路线图

**已知局限**

- 仅支持 Android；无法从同一部手机上的原 App 学习；无法设置广播标志字节；无法发送原始负载（一种智美风扇遥控器格式）。
- 灯的状态是假定的，从不读回。
- 小组件只控制一盏灯（带 ★ 的那盏）。
- 只有 LampSmart Pro 在真实硬件上测试过。

**路线图**
- [ ] 风扇控制（风速、方向）以及 Dudugu 这类仅风扇设备
- [ ] 灯光效果 / 场景 / 夜灯模式
- [ ] 为每个小组件单独选择灯
- [ ] 便于提交 bug 报告的"复制调试信息"按钮
- [ ] 根据社区反馈填充"已确认灯具"表
- [ ] 截图、已签名的发布版 APK、CI（每次提交都构建并测试）
- [x] 中文 README（`README.zh-CN.md`）

---

## 参与贡献与反馈你的灯

最有价值的贡献是一份**灯具反馈**：告诉我们你的灯的品牌、它配的是哪个 App、你是怎么添加的，以及哪些功能可用。
请使用 *Lamp report* issue 模板。一旦有人在真实硬件上验证，我们就会把相应品牌标记为 ✅ 已确认。

其他帮忙的方式：翻译、截图、界面打磨、风扇 / 灯效支持、硬件测试。

> ⚠️ **请不要公开发布抓取的原始数据包或灯的 ID。** 它们能让附近的任何人控制你的灯。

联系方式：[github.com/geethut-stack](https://github.com/geethut-stack)

---

## 安全与隐私

- **没有任何数据离开你的手机。** 没有联网权限、没有统计分析、没有账号。
- **ID 保存在本地**（应用存储），只用于和你自己的灯通信。
- **蓝牙广播没有身份验证。** 范围内任何记录下你的灯的 ID 的人都可以控制它。这些灯从来就不是按安全标准设计的——请不要把它们用于任何涉及安全的场合。

---

## 致谢、许可证与免责声明

- **协议：** 本项目是 **NicoIIT** 的 [**ha-ble-adv**](https://github.com/NicoIIT/ha-ble-adv)（MIT 许可证）中 codec 的 Kotlin 移植版。没有你的工作，就没有这一切——谢谢你。
- **许可证：** MIT © 2026 geethut-stack（见 LICENSE）；第三方声明见 THIRD_PARTY_LICENSES.md。AndroidX 和 Material Components 采用 Apache-2.0。
- **免责声明：** 这是一个独立项目，**与** LampSmart、FanLamp、Jingyuan、Zhi Jia、Zhi Guang、Zhi Mei 或任何其他灯具厂商 / App 开发者**没有任何关联**。
  产品名称仅用于说明兼容性。软件按"原样"提供，不附带任何担保。逆向工程仅为与作者自己拥有的设备实现互操作。
