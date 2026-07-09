# HTML UI 开发文档

> 用网页技术做 Minecraft 交互界面 - 完整开发指南

## 目录

- [快速开始](#快速开始)
- [核心概念](#核心概念)
- [模板变量](#模板变量)
- [URL 协议](#url-协议)
- [表单交互](#表单交互)
- [Action 动作系统](#action-动作系统)
- [完整示例页面](#完整示例页面)
- [服务端 API](#服务端-api)
- [客户端配置](#客户端配置)
- [常见问题](#常见问题)

---

## 快速开始

### 1. 放置 HTML 文件

在服务端创建目录 `config/htmlui/`，放入 HTML 文件。文件名就是页面 ID（不含扩展名）。

```
服务端根目录/
└── config/
    └── htmlui/
        ├── welcome.html      ← 页面ID = welcome
        ├── shop.html         ← 页面ID = shop
        └── settings.html     ← 页面ID = settings
```

### 2. 打开页面

游戏内执行命令：

```
/htmlui welcome
```

客户端会收到服务端下发的 HTML 内容，用内嵌 Chromium 浏览器渲染显示。

### 3. 最简示例

`config/htmlui/hello.html`：

```html
<h1>欢迎来到服务器</h1>
<p>玩家 {player}，你好！</p>
<button cmd="say 我进来了">广播上线</button>
<button close>关闭</button>
```

执行 `/htmlui hello` 即可打开。

---

## 核心概念

### 架构总览

```
┌──────────────── 服务端 ────────────────┐    ┌──────── 客户端 ────────┐
│                                         │    │                        │
│  config/htmlui/*.html  ←─── HTML 源文件 │    │  JCEF / Chromium 146   │
│         │                               │    │         ▲              │
│         ▼                               │    │         │              │
│  HtmlLoader  ──── 读取 + 模板变量替换 ──┼───▶│  渲染 HTML 到纹理       │
│         │                               │    │         │              │
│  ActionHandler  ←─── 处理客户端动作 ─────┼◀───│  URL 协议拦截          │
│         │                               │    │  (cmd/page/action/...) │
│         ▼                               │    │                        │
│  执行命令 / 返回数据 ────────────────────┼───▶│  更新页面              │
│                                         │    │                        │
└─────────────────────────────────────────┘    └────────────────────────┘
```

### 关键点

1. **HTML 只在服务端维护** - 客户端不需要预装任何页面文件
2. **服务端下发** - 打开页面时，服务端读取 HTML 并替换模板变量后下发给客户端
3. **客户端拦截 URL** - 所有链接点击和按钮交互通过 URL 协议拦截处理
4. **真实 Chromium** - 完整 HTML5/CSS3/JS 支持，和 Chrome 浏览器渲染效果一致

---

## 模板变量

服务端在下发 HTML 前，会自动替换以下占位符：

| 变量 | 说明 | 示例值 |
|------|------|--------|
| `{player}` | 玩家名称 | `Steve` |
| `{uuid}` | 玩家 UUID | `a3f2e1b8-...` |
| `{online}` | 当前在线人数 | `42` |
| `{world}` | 所在世界名称 | `minecraft:overworld` |
| `{time}` | 当前游戏时间 | `day:1200` |

### 使用示例

```html
<h1>欢迎，{player}！</h1>
<p>当前在线：{online} 人</p>
<p>你所在的世界：{world}</p>
<p>游戏时间：{time}</p>
```

### 注意事项

- 变量替换是**纯文本替换**，注意 XSS 防护（服务端可控内容无需担心）
- 变量区分大小写，必须全小写
- 未知的 `{xxx}` 不会被替换，保持原样显示

---

## URL 协议

HTML 中的 `<a href="...">` 链接支持 5 种自定义协议：

### 1. `cmd:` - 执行命令

以玩家身份执行原版命令。

```html
<!-- 链接式 -->
<a href="cmd:say hello">广播消息</a>

<!-- 按钮式（推荐，更美观） -->
<button onclick="location.href='cmd:say hello'">广播消息</button>
```

### 2. `page:` - 跳转页面

跳转到另一个 HTML 页面。

```html
<a href="page:shop">前往商店</a>
<a href="page:settings">设置界面</a>
```

### 3. `action:` - 触发动作

发送自定义动作到服务端处理。详见 [Action 动作系统](#action-动作系统)。

```html
<a href="action:buy:iron:10">购买 10 个铁锭</a>
<button onclick="location.href='action:vote'">投票</button>
```

### 4. `close:` - 关闭界面

关闭当前 HTML UI 界面。

```html
<a href="close:">关闭</a>
<button onclick="location.href='close:'">关闭</button>
```

### 5. `refresh:` - 刷新界面

重新从服务端加载当前页面（更新模板变量）。

```html
<a href="refresh:">刷新数据</a>
<button onclick="location.href='refresh:'">刷新</button>
```

### 协议速查表

| 协议 | 格式 | 触发位置 | 执行者 |
|------|------|----------|--------|
| `cmd:` | `cmd:命令 参数` | 客户端拦截 | 玩家身份 |
| `page:` | `page:页面ID` | 客户端拦截 | 客户端跳转 |
| `action:` | `action:动作名:参数` | 服务端处理 | 服务端逻辑 |
| `close:` | `close:` | 客户端拦截 | 客户端关闭 |
| `refresh:` | `refresh:` | 客户端拦截 | 客户端重载 |

---

## 表单交互

HTML `<input>` 元素的值可以通过 `{inputName}` 占位符在 URL 协议中引用。

### 基础示例

```html
<h2>发送消息</h2>
<input name="msg" placeholder="输入消息内容">

<button onclick="location.href='cmd:say {msg}'">广播</button>
<button onclick="location.href='cmd:me {msg}'">动作</button>
<button onclick="location.href='cmd:tell {player} {msg}'">对自己说</button>
```

**工作流程**：
1. 玩家在 `<input name="msg">` 输入框输入内容
2. 点击按钮时，`{msg}` 被替换为输入框的值
3. 替换后的命令发送到服务端执行

### 多输入框示例

```html
<h2>传送请求</h2>
<label>目标玩家：</label>
<input name="target" placeholder="玩家名">

<label>消息（可选）：</label>
<input name="text" placeholder="附言">

<button onclick="location.href='cmd:tpa {target}'">请求传送</button>
<button onclick="location.href='cmd:msg {target} {text}'">发私聊</button>
```

### 表单提交模式

```html
<h2>邮件系统</h2>
<input name="recipient" placeholder="收件人">
<input name="subject" placeholder="主题">
<textarea name="body" placeholder="正文"></textarea>

<button onclick="location.href='action:send_mail:{recipient}:{subject}:{body}'">
  发送邮件
</button>
```

### 注意事项

- `<input>` 必须有 `name` 属性才能被占位符引用
- 占位符名称与 `name` 属性值完全一致（区分大小写）
- 输入值会做 URL 编码，服务端解码后使用
- 建议对用户输入做服务端校验

---

## Action 动作系统

Action 是扩展性最强的交互方式，允许服务端自定义处理逻辑。

### 内置动作

| 动作 | 格式 | 说明 |
|------|------|------|
| `test_echo` | `action:test_echo:消息` | 测试用，在控制台打印消息 |
| `greet` | `action:greet:名字` | 返回问候语 |
| `msg` | `action:msg:消息内容` | 给玩家发消息 |
| `open` | `action:open:页面ID` | 服务端主动打开页面 |
| `tell` | `action:tell:玩家:消息` | 私聊其他玩家 |
| `close` | `action:close` | 关闭界面（服务端触发） |

### 自定义 Action

在服务端 mod 中注册自定义动作处理器：

```java
import com.htmlui.server.ActionHandler;
import net.minecraft.server.level.ServerPlayer;

public class MyMod {
    public static void init() {
        // 注册自定义动作
        ActionHandler.registerHandler("buy", (player, args) -> {
            // args = ["iron", "10"]
            String item = args[0];
            int amount = Integer.parseInt(args[1]);

            // 扣钱逻辑...
            boolean success = Economy.deduct(player, item, amount);

            if (success) {
                // 给物品
                player.getInventory().add(new ItemStack(Items.IRON_INGOT, amount));
                // 提示
                ActionHandler.sendMsg(player, "购买成功！");
                // 跳转到成功页面
                ActionHandler.openPage(player, "shop_success");
            } else {
                ActionHandler.sendMsg(player, "余额不足");
            }
        });
    }
}
```

### HTML 端调用

```html
<button onclick="location.href='action:buy:iron:10'">
  购买 10 铁锭（$100）
</button>

<button onclick="location.href='action:buy:diamond:1'">
  购买 1 钻石（$1000）
</button>
```

### Action 参数格式

```
action:动作名:参数1:参数2:参数3...
```

- 动作名和参数之间用 `:` 分隔
- 参数通过 `args` 数组传入处理器
- 空参数传空数组，不是 null

---

## 完整示例页面

### 示例 1：服务器欢迎页

`config/htmlui/welcome.html`：

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <style>
        body {
            margin: 0;
            padding: 24px;
            font-family: -apple-system, "Microsoft YaHei", sans-serif;
            background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
            color: #e0e0e0;
        }
        .card {
            background: rgba(255,255,255,0.05);
            border: 1px solid rgba(255,255,255,0.1);
            border-radius: 12px;
            padding: 20px;
            margin: 12px 0;
        }
        h1 { color: #ffd700; margin: 0 0 8px; }
        .btn {
            display: inline-block;
            padding: 8px 16px;
            margin: 4px;
            background: #ffd700;
            color: #1a1a2e;
            text-decoration: none;
            border-radius: 6px;
            font-weight: bold;
            cursor: pointer;
            border: none;
        }
        .btn-secondary {
            background: transparent;
            color: #e0e0e0;
            border: 1px solid #444;
        }
        .stat {
            display: inline-block;
            margin: 0 16px 0 0;
            color: #aaa;
            font-size: 14px;
        }
        .stat b { color: #fff; font-size: 18px; }
    </style>
</head>
<body>
    <h1>欢迎来到逸致生存服</h1>

    <div class="card">
        <span class="stat">玩家：<b>{player}</b></span>
        <span class="stat">在线：<b>{online}</b></span>
        <span class="stat">世界：<b>{world}</b></span>
        <span class="stat">时间：<b>{time}</b></span>
    </div>

    <div class="card">
        <h3>快速操作</h3>
        <a class="btn" href="cmd:say 我上线啦">广播上线</a>
        <a class="btn btn-secondary" href="page:shop">前往商店</a>
        <a class="btn btn-secondary" href="page:rules">服务器规则</a>
        <a class="btn btn-secondary" href="close:">关闭</a>
    </div>
</body>
</html>
```

### 示例 2：商店系统

`config/htmlui/shop.html`：

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <style>
        body {
            margin: 0;
            padding: 20px;
            font-family: -apple-system, "Microsoft YaHei", sans-serif;
            background: #0f0f0f;
            color: #e0e0e0;
        }
        h1 { color: #ffd700; border-bottom: 1px solid #333; padding-bottom: 8px; }
        .item {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 12px;
            margin: 6px 0;
            background: #1a1a1a;
            border-radius: 8px;
            border: 1px solid #2a2a2a;
        }
        .item:hover { border-color: #ffd700; }
        .item-info { flex: 1; }
        .item-name { font-weight: bold; color: #fff; }
        .item-price { color: #4caf50; margin-left: 8px; }
        .item-desc { color: #888; font-size: 13px; margin-top: 4px; }
        .qty {
            width: 60px;
            padding: 4px 8px;
            background: #0a0a0a;
            border: 1px solid #333;
            color: #fff;
            border-radius: 4px;
            margin: 0 8px;
        }
        .buy-btn {
            padding: 6px 14px;
            background: #ffd700;
            color: #000;
            border: none;
            border-radius: 4px;
            cursor: pointer;
            font-weight: bold;
        }
        .nav { margin: 12px 0; }
        .nav a {
            color: #888;
            margin-right: 12px;
            text-decoration: none;
            font-size: 14px;
        }
        .nav a:hover { color: #ffd700; }
    </style>
</head>
<body>
    <div class="nav">
        <a href="page:welcome">← 返回主页</a>
        <a href="page:shop">刷新</a>
        <a href="close:">关闭</a>
    </div>

    <h1>商店</h1>

    <div class="item">
        <div class="item-info">
            <span class="item-name">铁锭</span>
            <span class="item-price">$10/个</span>
            <div class="item-desc">基础合成材料</div>
        </div>
        <input class="qty" name="qty_iron" value="1" type="number" min="1" max="64">
        <button class="buy-btn"
                onclick="location.href='action:buy:iron:{qty_iron}'">
            购买
        </button>
    </div>

    <div class="item">
        <div class="item-info">
            <span class="item-name">钻石</span>
            <span class="item-price">$500/个</span>
            <div class="item-desc">稀有矿石，可用于制作装备</div>
        </div>
        <input class="qty" name="qty_diamond" value="1" type="number" min="1" max="64">
        <button class="buy-btn"
                onclick="location.href='action:buy:diamond:{qty_diamond}'">
            购买
        </button>
    </div>

    <div class="item">
        <div class="item-info">
            <span class="item-name">金苹果</span>
            <span class="item-price">$200/个</span>
            <div class="item-desc">恢复生命值，提供抗性</div>
        </div>
        <input class="qty" name="qty_golden_apple" value="1" type="number" min="1" max="64">
        <button class="buy-btn"
                onclick="location.href='action:buy:golden_apple:{qty_golden_apple}'">
            购买
        </button>
    </div>
</body>
</html>
```

### 示例 3：玩家信息面板（含 JS 交互）

`config/htmlui/profile.html`：

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <title>玩家信息</title>
    <style>
        * { box-sizing: border-box; }
        body {
            margin: 0;
            padding: 24px;
            font-family: -apple-system, "Microsoft YaHei", sans-serif;
            background: #1e1e1e;
            color: #d4d4d4;
        }
        .header {
            display: flex;
            align-items: center;
            gap: 16px;
            padding-bottom: 16px;
            border-bottom: 1px solid #333;
            margin-bottom: 20px;
        }
        .avatar {
            width: 64px;
            height: 64px;
            border-radius: 50%;
            background: linear-gradient(135deg, #667eea, #764ba2);
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 32px;
            color: #fff;
            font-weight: bold;
        }
        .player-info h1 { margin: 0; color: #fff; }
        .player-info .uid { color: #888; font-size: 13px; margin-top: 4px; }
        .stats {
            display: grid;
            grid-template-columns: repeat(3, 1fr);
            gap: 12px;
            margin-bottom: 20px;
        }
        .stat-card {
            background: #252526;
            border: 1px solid #333;
            border-radius: 8px;
            padding: 16px;
            text-align: center;
        }
        .stat-card .value {
            font-size: 28px;
            font-weight: bold;
            color: #4ec9b0;
        }
        .stat-card .label {
            color: #888;
            font-size: 12px;
            margin-top: 4px;
            text-transform: uppercase;
        }
        .section { margin-bottom: 20px; }
        .section h2 {
            color: #569cd6;
            font-size: 16px;
            margin: 0 0 10px;
            border-bottom: 1px solid #333;
            padding-bottom: 6px;
        }
        .btn {
            display: inline-block;
            padding: 8px 16px;
            margin: 2px;
            border-radius: 6px;
            text-decoration: none;
            cursor: pointer;
            border: none;
            font-size: 14px;
        }
        .btn-primary { background: #007acc; color: #fff; }
        .btn-danger { background: #f14c4c; color: #fff; }
        .btn-ghost {
            background: transparent;
            color: #d4d4d4;
            border: 1px solid #444;
        }
        input[type="text"] {
            width: 100%;
            padding: 8px;
            background: #2d2d2d;
            border: 1px solid #444;
            border-radius: 4px;
            color: #fff;
            font-size: 14px;
        }
    </style>
</head>
<body>
    <div class="header">
        <div class="avatar">{player}</div>
        <div class="player-info">
            <h1>{player}</h1>
            <div class="uid">UUID: {uuid}</div>
            <div class="uid">世界: {world} | 时间: {time}</div>
        </div>
    </div>

    <div class="stats">
        <div class="stat-card">
            <div class="value">{online}</div>
            <div class="label">在线人数</div>
        </div>
        <div class="stat-card">
            <div class="value">∞</div>
            <div class="label">余额</div>
        </div>
        <div class="stat-card">
            <div class="value">{time}</div>
            <div class="label">游戏时间</div>
        </div>
    </div>

    <div class="section">
        <h2>快捷操作</h2>
        <a class="btn btn-primary" href="cmd:say 我在 {world}">广播位置</a>
        <a class="btn btn-primary" href="cmd:spawn">返回出生点</a>
        <a class="btn btn-ghost" href="page:shop">商店</a>
        <a class="btn btn-ghost" href="refresh:">刷新</a>
    </div>

    <div class="section">
        <h2>私聊</h2>
        <input type="text" name="target" placeholder="玩家名">
        <input type="text" name="text" placeholder="消息内容" style="margin-top:8px;">
        <div style="margin-top:8px;">
            <a class="btn btn-primary"
               href="cmd:tell {target} {text}">发送</a>
        </div>
    </div>

    <div class="section">
        <h2>其他</h2>
        <a class="btn btn-danger" href="cmd:kill">自杀（谨慎）</a>
        <a class="btn btn-ghost" href="close:">关闭</a>
    </div>
</body>
</html>
```

### 示例 4：表单 + Action 联动

`config/htmlui/mail.html`：

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <style>
        body {
            margin: 0;
            padding: 20px;
            font-family: -apple-system, "Microsoft YaHei", sans-serif;
            background: #fafafa;
            color: #333;
        }
        h1 { color: #1a1a1a; }
        .form-group { margin-bottom: 12px; }
        label {
            display: block;
            margin-bottom: 4px;
            color: #555;
            font-size: 14px;
        }
        input, textarea {
            width: 100%;
            padding: 8px;
            border: 1px solid #ddd;
            border-radius: 4px;
            font-size: 14px;
            font-family: inherit;
        }
        input:focus, textarea:focus {
            outline: none;
            border-color: #007acc;
        }
        .btn {
            padding: 10px 24px;
            background: #007acc;
            color: #fff;
            border: none;
            border-radius: 4px;
            cursor: pointer;
            font-size: 14px;
        }
        .btn:hover { background: #0062a3; }
        .btn-secondary {
            background: #fff;
            color: #333;
            border: 1px solid #ddd;
        }
    </style>
</head>
<body>
    <h1>发送邮件</h1>

    <div class="form-group">
        <label>收件人</label>
        <input name="to" placeholder="输入玩家名">
    </div>

    <div class="form-group">
        <label>主题</label>
        <input name="subject" placeholder="邮件主题">
    </div>

    <div class="form-group">
        <label>正文</label>
        <textarea name="body" rows="6" placeholder="邮件内容..."></textarea>
    </div>

    <div class="form-group">
        <button class="btn"
                onclick="location.href='action:send_mail:{to}:{subject}:{body}'">
            发送
        </button>
        <button class="btn btn-secondary"
                onclick="location.href='page:inbox'">
            收件箱
        </button>
        <button class="btn btn-secondary"
                onclick="location.href='close:'">
            取消
        </button>
    </div>
</body>
</html>
```

### 示例 5：Chromium 高级特性

由于使用真实 Chromium 内核，你可以使用所有现代 Web 特性：

`config/htmlui/dashboard.html`：

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <style>
        /* CSS Grid + Flexbox 完全支持 */
        body {
            margin: 0;
            padding: 16px;
            font-family: system-ui, sans-serif;
            background: #0d1117;
            color: #c9d1d9;
        }
        .grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 12px;
        }
        .card {
            background: #161b22;
            border: 1px solid #30363d;
            border-radius: 6px;
            padding: 16px;
            transition: transform 0.2s, box-shadow 0.2s;
        }
        .card:hover {
            transform: translateY(-2px);
            box-shadow: 0 4px 12px rgba(0,0,0,0.4);
            border-color: #58a6ff;
        }

        /* CSS 动画 */
        @keyframes pulse {
            0%, 100% { opacity: 1; }
            50% { opacity: 0.5; }
        }
        .live-dot {
            display: inline-block;
            width: 8px;
            height: 8px;
            background: #3fb950;
            border-radius: 50%;
            animation: pulse 2s infinite;
        }

        /* CSS 变量 */
        :root {
            --accent: #58a6ff;
            --danger: #f85149;
            --success: #3fb950;
        }
        .btn {
            padding: 6px 12px;
            border: 1px solid #30363d;
            border-radius: 4px;
            background: #21262d;
            color: var(--accent);
            cursor: pointer;
            font-size: 13px;
        }
        .btn:hover { background: #30363d; }
    </style>
</head>
<body>
    <div style="display:flex; justify-content:space-between; align-items:center;">
        <h2 style="margin:0;">服务器仪表盘</h2>
        <span><span class="live-dot"></span> 实时</span>
    </div>

    <div class="grid" style="margin-top:16px;">
        <div class="card">
            <div style="color:#8b949e; font-size:12px;">在线人数</div>
            <div style="font-size:32px; color:var(--success);">{online}</div>
        </div>
        <div class="card">
            <div style="color:#8b949e; font-size:12px;">玩家</div>
            <div style="font-size:20px; color:#fff;">{player}</div>
        </div>
        <div class="card">
            <div style="color:#8b949e; font-size:12px;">世界</div>
            <div style="font-size:16px;">{world}</div>
        </div>
        <div class="card">
            <div style="color:#8b949e; font-size:12px;">时间</div>
            <div style="font-size:16px;">{time}</div>
        </div>
    </div>

    <div class="card" style="margin-top:12px;">
        <h3>操作</h3>
        <button class="btn" onclick="location.href='cmd:spawn'">返回出生点</button>
        <button class="btn" onclick="location.href='cmd:home'">回家</button>
        <button class="btn" onclick="location.href='page:shop'">商店</button>
        <button class="btn" onclick="location.href='refresh:'">刷新数据</button>
    </div>
</body>
</html>
```

---

## 服务端 API

### HtmlLoader

服务端主动操作 HTML 页面。

```java
import com.htmlui.server.HtmlLoader;
import net.minecraft.server.level.ServerPlayer;

// 主动给玩家打开页面
HtmlLoader.sendTo(player, "welcome");

// 打开页面并等待加载完成（CompletableFuture）
HtmlLoader.sendToAsync(player, "shop").thenAccept(success -> {
    if (success) {
        System.out.println("页面已加载");
    }
});
```

### ActionHandler

注册自定义动作处理器。

```java
import com.htmlui.server.ActionHandler;
import net.minecraft.server.level.ServerPlayer;

// 注册动作
ActionHandler.registerHandler("my_action", (player, args) -> {
    // args 是参数数组
    String arg1 = args.length > 0 ? args[0] : "";
    String arg2 = args.length > 1 ? args[1] : "";

    // 处理逻辑...
    player.sendSystemMessage(Component.literal("收到动作: " + arg1));

    // 可选：返回响应
    ActionHandler.sendMsg(player, "操作完成");
});

// 内置工具方法
ActionHandler.sendMsg(player, "消息");              // 给玩家发消息
ActionHandler.openPage(player, "page_id");          // 打开页面
ActionHandler.closePage(player);                    // 关闭页面
ActionHandler.refreshPage(player);                  // 刷新页面
```

### HtmlUiCommand

`/htmlui` 命令支持以下用法：

```
/htmlui <pageId>          打开页面（自己）
/htmlui <pageId> <玩家>   给指定玩家打开页面（管理员）
```

权限节点：

| 节点 | 说明 | 默认 |
|------|------|------|
| `htmlui.command` | 使用 /htmlui 命令 | OP 2 |

---

## 客户端配置

配置文件位置：`config/htmlui-client.json`

```json
{
    "enabled": true,
    "defaultScale": 1.0,
    "resolutionLevel": 3,
    "transparent": false,
    "debugOverlay": false,
    "fallbackToText": true,
    "useChinaMirror": true
}
```

| 字段 | 说明 | 默认值 |
|------|------|--------|
| `enabled` | 总开关 | `true` |
| `defaultScale` | 默认缩放（0.5-2.0） | `1.0` |
| `resolutionLevel` | 分辨率等级 1-4 | `3` |
| `transparent` | 透明背景 | `false` |
| `debugOverlay` | 调试信息（左上角） | `false` |
| `fallbackToText` | 加载失败回退文本 | `true` |
| `useChinaMirror` | 国内镜像下载 | `true` |

### 分辨率等级

| 等级 | 名称 | 缩放 | 说明 |
|------|------|------|------|
| 1 | 极低 | 0.5x | 最省资源，略模糊 |
| 2 | 低 | 0.65x | 省资源 |
| 3 | 中 | 0.85x | 默认，平衡 |
| 4 | 高 | 1.0x | 清晰，开销大 |

等级 4 渲染分辨率为游戏窗口物理分辨率，1920×1080 窗口下就是 1920×1080。等级越低内部分辨率越小，GPU 开销越低但越模糊。

---

## 常见问题

### Q: 页面打不开 / 白屏

**A:**
1. 检查 HTML 文件是否在 `config/htmlui/` 目录
2. 文件名是否全小写（部分系统区分大小写）
3. 客户端是否安装了 mod
4. 查看 `debugOverlay` 是否开启以获取错误信息

### Q: 中文乱码

**A:** HTML 文件必须声明编码：

```html
<meta charset="UTF-8">
```

或

```html
<meta http-equiv="Content-Type" content="text/html; charset=utf-8">
```

### Q: 命令没有权限执行

**A:** `cmd:` 协议以**玩家身份**执行命令，玩家必须有对应权限。对于需要 OP 权限的命令，改用 `action:` 协议在服务端以控制台身份执行。

### Q: 页面太大 / 显示不全

**A:** HTML UI 占据游戏窗口，内容超出时用 CSS 处理：

```css
body {
    overflow: auto;
    max-height: 100vh;
}
```

### Q: 如何调试

**A:**
1. 客户端配置开启 `debugOverlay`，左上角显示渲染信息
2. HTML 里加 `<script>console.log(...)</script>`，日志输出到客户端日志
3. 用 `action:test_echo:消息` 测试 action 通路是否正常

### Q: 可以用外部图片吗

**A:** 可以。Chromium 会正常加载 `<img src="https://...">`，但需要客户端联网。本地图片建议用 base64：

```html
<img src="data:image/png;base64,iVBORw0KGgo...">
```

### Q: 可以用 JavaScript 吗

**A:** 完全支持。所有 ES6+ 语法、fetch、WebSocket、localStorage 等浏览器 API 都可用。但注意：
- `alert()` / `confirm()` 等弹窗 API 不可用（没有原生弹窗）
- 用 `location.href = 'cmd:...'` 触发 URL 协议

### Q: 多个玩家会互相影响吗

**A:** 不会。每个玩家有独立的浏览器实例和页面状态。服务端下发的 HTML 是每个玩家一份。

### Q: 如何更新页面内容

**A:** 三种方式：
1. **刷新**：`<a href="refresh:">` 重新从服务端加载（更新模板变量）
2. **跳转**：`<a href="page:other">` 跳到新页面
3. **JS 更新**：用 JavaScript 动态修改 DOM

---

## 附录：标签支持

HTML UI 支持完整的 HTML5 标准标签，以下是常用标签：

| 标签 | 用途 | 示例 |
|------|------|------|
| `<div>` | 容器 | `<div class="box">内容</div>` |
| `<span>` | 行内文本 | `<span class="name">{player}</span>` |
| `<h1>`~`<h6>` | 标题 | `<h1>标题</h1>` |
| `<p>` | 段落 | `<p>文字</p>` |
| `<a>` | 链接 | `<a href="cmd:say hi">说话</a>` |
| `<button>` | 按钮 | `<button onclick="...">点击</button>` |
| `<input>` | 输入框 | `<input name="msg">` |
| `<textarea>` | 多行输入 | `<textarea name="body"></textarea>` |
| `<select>` | 下拉选择 | `<select name="t"><option>A</option></select>` |
| `<img>` | 图片 | `<img src="...">` |
| `<table>` | 表格 | 完整支持 |
| `<ul>`/`<ol>`/`<li>` | 列表 | 完整支持 |
| `<form>` | 表单 | 完整支持 |
| `<iframe>` | 内嵌框架 | **不支持**（安全限制） |

---

## 附录：URL 协议速查

```
┌─────────────────────────────────────────────────────────────┐
│  cmd:命令                    → 以玩家身份执行命令            │
│  page:页面ID                 → 跳转到指定页面                │
│  action:动作名:参数1:参数2   → 触发服务端自定义动作          │
│  close:                      → 关闭界面                      │
│  refresh:                    → 刷新当前页面                  │
└─────────────────────────────────────────────────────────────┘
```

---

## 附录：模板变量速查

```
┌────────────────────────────────────────────┐
│  {player}  → 玩家名称                      │
│  {uuid}    → 玩家 UUID                     │
│  {online}  → 当前在线人数                  │
│  {world}   → 所在世界                      │
│  {time}    → 游戏时间                      │
│  {inputName} → <input name="inputName">的值 │
└────────────────────────────────────────────┘
```
