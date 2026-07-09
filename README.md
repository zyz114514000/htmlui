# HTML UI

用网页技术做 Minecraft 交互界面的模组。

> 基于 [MCEF Modern](https://github.com/DimasKama/mcef-modern)（作者 DimasKama，LGPL-2.1）二次开发

## 简介

HTML UI 在客户端内嵌 Chromium 146 内核（通过 JCEF / MCEF Modern），让开发者用 HTML5 + CSS3 + JavaScript 编写游戏内交互界面，渲染效果与 Chrome 浏览器一致。

HTML 文件只放在服务端 `config/htmlui/` 目录，玩家执行 `/htmlui <页面ID>` 即可打开对应界面。页面内容由服务端实时下发，客户端零配置。

## 核心特性

- **真实浏览器内核** — JCEF 146 / Chromium 146，HTML5/CSS3/JS 全套支持
- **服务端下发** — 页面只在服务端维护，客户端不需要预装
- **5 种 URL 协议** — `cmd:` 执行命令 / `page:` 跳转页面 / `action:` 触发事件 / `close:` 关闭 / `refresh:` 刷新
- **模板变量** — `{player}` `{uuid}` `{online}` `{world}` `{time}` 五个内置变量
- **Action 扩展** — 内置 6 类动作 + 自定义 `registerHandler` 注册
- **国产化适配** — 国内镜像下载（阿里云/腾讯云）+ 免下载离线版

## 安装

提供两个版本（不可同时安装）：

| 版本 | 体积 | 说明 |
|------|------|------|
| 普通版 | ~300KB | 首次运行联网下载 ~150MB JCEF 内核 |
| 免下载版 | ~155MB | jar 内置内核，离线可用 |

## 快速开始

1. 将 jar 放入 `mods` 目录
2. 服务端在 `config/htmlui/` 放置 HTML 文件（如 `welcome.html`）
3. 游戏内执行 `/htmlui welcome`

## 环境要求

- Minecraft 1.21.6（26.2）
- Fabric Loader
- Java 25+

## 开发者文档

- https://github.com/zyz114514000/htmlui/blob/main/DEVELOPER.md

### URL 协议速查

| 协议 | 用途 | 示例 |
|------|------|------|
| `cmd:` | 执行命令 | `<a href="cmd:say hi">` |
| `page:` | 跳转页面 | `<a href="page:shop">` |
| `action:` | 触发事件 | `<a href="action:test_echo">` |
| `close:` | 关闭界面 | `<a href="close:">` |
| `refresh:` | 刷新界面 | `<a href="refresh:">` |

## 著作权声明

本项目整体遵循 **LGPL-2.1** 协议开源，详细著作权归属见 [NOTICE](NOTICE) 文件。

- 本项目修改与新增内容著作权 © 2026 逸致slom
- 上游 MCEF Modern 著作权 © DimasKama（保留，LGPL-2.1 要求）

## 链接

- QQ群：https://qm.qq.com/q/vVd4wpgKgo
- 123云盘：https://1814385081.share.123pan.cn/123pan/2GVbVv-0X20h?pwd=RgGx#
- 百度网盘：https://pan.baidu.com/s/1xdeQEsFA_kRQHTGQHil9iw?pwd=mmgf
