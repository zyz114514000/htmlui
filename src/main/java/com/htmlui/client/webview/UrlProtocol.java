package com.htmlui.client.webview;

import java.util.function.Consumer;

/**
 * URL 协议解析 - 处理 <a href="cmd:xxx"> 等自定义协议
 *
 * 协议格式:
 *   cmd:<命令>          - 以玩家身份执行命令（如 cmd:say hello）
 *   page:<pageId>       - 跳转到另一个 HTML 页面（如 page:help）
 *   action:<动作>       - 发送 action 到服务端（如 action:buy?data=sword）
 *   close:              - 关闭界面
 *   refresh:            - 刷新当前页
 *
 * 对于 http/https/about 链接，视为外部链接，不处理。
 */
public class UrlProtocol {

    public enum ActionType {
        COMMAND,        // cmd:xxx
        PAGE,           // page:xxx
        SERVER_ACTION,  // action:xxx
        CLOSE,          // close:
        REFRESH,        // refresh:
        EXTERNAL,       // http/https 外部链接（忽略）
        UNKNOWN         // 未知协议
    }

    public static class ParsedUrl {
        public final ActionType type;
        public final String content;     // 命令/页面id/动作名
        public final String data;       // action 的 data 参数

        ParsedUrl(ActionType type, String content, String data) {
            this.type = type;
            this.content = content;
            this.data = data;
        }
    }

    /** 解析 URL */
    public static ParsedUrl parse(String url) {
        if (url == null || url.isEmpty()) return new ParsedUrl(ActionType.UNKNOWN, "", "");

        // 去除前后空白
        url = url.trim();

        // 外部链接
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return new ParsedUrl(ActionType.EXTERNAL, url, "");
        }

        // about:blank
        if (url.startsWith("about:")) {
            return new ParsedUrl(ActionType.UNKNOWN, url, "");
        }

        // cmd: 命令
        if (url.startsWith("cmd:")) {
            return new ParsedUrl(ActionType.COMMAND, url.substring(4), "");
        }

        // page: 页面跳转
        if (url.startsWith("page:")) {
            return new ParsedUrl(ActionType.PAGE, url.substring(5), "");
        }

        // action: 服务端动作
        if (url.startsWith("action:")) {
            String rest = url.substring(7);
            // 支持 action:name?data=value 格式
            int qIdx = rest.indexOf('?');
            if (qIdx >= 0) {
                String name = rest.substring(0, qIdx);
                String query = rest.substring(qIdx + 1);
                // 提取 data 参数
                String data = "";
                for (String pair : query.split("&")) {
                    int eq = pair.indexOf('=');
                    if (eq >= 0 && pair.substring(0, eq).equals("data")) {
                        data = decode(pair.substring(eq + 1));
                    }
                }
                return new ParsedUrl(ActionType.SERVER_ACTION, name, data);
            }
            return new ParsedUrl(ActionType.SERVER_ACTION, rest, "");
        }

        // close: 关闭
        if (url.startsWith("close:") || url.equals("close")) {
            return new ParsedUrl(ActionType.CLOSE, "", "");
        }

        // refresh: 刷新
        if (url.startsWith("refresh:") || url.equals("refresh")) {
            return new ParsedUrl(ActionType.REFRESH, "", "");
        }

        // 没有协议前缀，默认当作 page: 处理（直接是页面名）
        if (!url.contains(":")) {
            return new ParsedUrl(ActionType.PAGE, url, "");
        }

        return new ParsedUrl(ActionType.UNKNOWN, url, "");
    }

    /** URL 解码 */
    private static String decode(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length()) {
                try {
                    sb.append((char) Integer.parseInt(s.substring(i + 1, i + 3), 16));
                    i += 2;
                } catch (NumberFormatException e) {
                    sb.append(c);
                }
            } else if (c == '+') {
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
