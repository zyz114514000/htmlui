package com.htmlui.server;

import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTML 文件加载器
 *
 * 目录: config/htmlui/*.html
 * pageId = 文件名（不含 .html）
 * 模板变量: {player} {uuid} {online} {world} {time}
 * 热重载: 基于文件 mtime 缓存
 */
public class HtmlLoader {

    private Path htmlDir;
    private final Map<String, CachedPage> cache = new ConcurrentHashMap<>();

    public void init() {
        htmlDir = Paths.get("config", "htmlui");
        try {
            Files.createDirectories(htmlDir);
        } catch (IOException e) {
            HtmlUiServerMod.LOGGER.error("[HTML UI] 无法创建目录: {}", e.getMessage());
        }
        if (listPages().isEmpty()) createDefaults();
        HtmlUiServerMod.LOGGER.info("[HTML UI] 可用页面: {}", listPages());
    }

    public boolean sendTo(ServerPlayer player, String pageId) {
        if (pageId == null || pageId.isEmpty()) return false;
        if (pageId.contains("..") || pageId.contains("/") || pageId.contains("\\")) return false;
        String html = load(pageId);
        if (html == null) return false;
        html = applyTemplates(html, player);
        HtmlUiServerMod.sendOpenHtml(player, pageId, html);
        HtmlUiServerMod.LOGGER.info("[HTML UI] 发送 '{}' 给 {} ({} chars)",
                pageId, player.getName().getString(), html.length());
        return true;
    }

    public List<String> listPages() {
        List<String> pages = new ArrayList<>();
        if (htmlDir == null || !Files.isDirectory(htmlDir)) return pages;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(htmlDir, "*.html")) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                if (name.endsWith(".html")) pages.add(name.substring(0, name.length() - 5));
            }
        } catch (IOException ignored) {}
        Collections.sort(pages);
        return pages;
    }

    public void clearCache() { cache.clear(); }

    // ==================== 内部 ====================

    private String load(String pageId) {
        Path file = htmlDir.resolve(pageId + ".html");
        if (!Files.exists(file)) { cache.remove(pageId); return null; }
        try {
            long mtime = Files.getLastModifiedTime(file).toMillis();
            CachedPage cached = cache.get(pageId);
            if (cached != null && cached.mtime == mtime) return cached.content;
            String content = Files.readString(file, StandardCharsets.UTF_8);
            cache.put(pageId, new CachedPage(content, mtime));
            return content;
        } catch (IOException e) {
            HtmlUiServerMod.LOGGER.error("[HTML UI] 读取失败: {} - {}", pageId, e.getMessage());
            return null;
        }
    }

    private String applyTemplates(String html, ServerPlayer player) {
        return html
                .replace("{player}", player.getName().getString())
                .replace("{uuid}", player.getUUID().toString())
                .replace("{online}", String.valueOf(HtmlUiServerMod.getServer().getPlayerCount()))
                .replace("{world}", player.level().dimension().identifier().toString())
                .replace("{time}", java.time.LocalTime.now().withNano(0).toString());
    }

    private void createDefaults() {
        write("welcome", "<ui title=\"欢迎\" width=\"300\">\n" +
                "  <h1>欢迎来到服务器</h1>\n" +
                "  <p color=\"gold\" align=\"center\">你好，{player}！</p>\n" +
                "  <hr/>\n" +
                "  <p color=\"gray\">UUID: {uuid}</p>\n" +
                "  <p color=\"gray\">在线: {online} 人</p>\n" +
                "  <p color=\"gray\">世界: {world}</p>\n" +
                "  <spacer height=\"8\"/>\n" +
                "  <button action=\"open:help\" color=\"sky\">查看帮助</button>\n" +
                "  <button action=\"test_echo\" color=\"emerald\">测试回执</button>\n" +
                "  <button cmd=\"close\" color=\"gray\">关闭</button>\n" +
                "</ui>");
        write("help", "<ui title=\"帮助\" width=\"320\">\n" +
                "  <h1>命令帮助</h1>\n" +
                "  <hr/>\n" +
                "  <h3>基础命令</h3>\n" +
                "  <p color=\"white\">/htmlui list - 列出页面</p>\n" +
                "  <p color=\"white\">/htmlui reload - 重载缓存</p>\n" +
                "  <p color=\"white\">/htmlui &lt;pageId&gt; - 打开页面</p>\n" +
                "  <spacer height=\"6\"/>\n" +
                "  <h3>HTML 标签</h3>\n" +
                "  <p color=\"gray\">ui/h1/h2/h3/p/button/input/hr/br/row/col</p>\n" +
                "  <hr/>\n" +
                "  <button action=\"open:welcome\" color=\"sky\">返回</button>\n" +
                "  <button cmd=\"close\" color=\"gray\">关闭</button>\n" +
                "</ui>");
        write("test", "<ui title=\"测试\" width=\"280\">\n" +
                "  <h1>HTML UI 测试</h1>\n" +
                "  <p color=\"gold\" align=\"center\">{player}</p>\n" +
                "  <hr/>\n" +
                "  <button cmd=\"say TEST\" color=\"green\" after=\"stay\">执行 say TEST</button>\n" +
                "  <button action=\"test_echo\" color=\"emerald\">测试 action</button>\n" +
                "  <button action=\"greet\" color=\"coral\">问候</button>\n" +
                "  <hr/>\n" +
                "  <button cmd=\"close\" color=\"gray\">关闭</button>\n" +
                "</ui>");
        write("form", "<ui title=\"表单\" width=\"320\">\n" +
                "  <h1>输入表单</h1>\n" +
                "  <p color=\"gray\">演示输入框与变量替换</p>\n" +
                "  <hr/>\n" +
                "  <p color=\"gold\">你的名字：</p>\n" +
                "  <input name=\"who\" placeholder=\"输入名字...\" default=\"{player}\"/>\n" +
                "  <p color=\"gold\">消息内容：</p>\n" +
                "  <input name=\"msg\" placeholder=\"输入消息...\" maxchars=\"50\"/>\n" +
                "  <spacer height=\"8\"/>\n" +
                "  <button cmd=\"say {who}: {msg}\" color=\"emerald\">发送</button>\n" +
                "  <button action=\"greet\" color=\"sky\">问候</button>\n" +
                "  <hr/>\n" +
                "  <button cmd=\"close\" color=\"gray\">关闭</button>\n" +
                "</ui>");
        HtmlUiServerMod.LOGGER.info("[HTML UI] 已创建 4 个示例页面");
    }

    private void write(String pageId, String content) {
        try {
            Files.writeString(htmlDir.resolve(pageId + ".html"), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            HtmlUiServerMod.LOGGER.error("[HTML UI] 创建示例失败: {} - {}", pageId, e.getMessage());
        }
    }

    private static class CachedPage {
        final String content; final long mtime;
        CachedPage(String c, long m) { content = c; mtime = m; }
    }
}
