package com.htmlui.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.htmlui.HtmlUiPayload;
import com.htmlui.client.config.DownloadMonitor;
import com.htmlui.client.config.DownloadProgressHud;
import com.htmlui.client.config.HtmlUiConfig;
import com.htmlui.client.webview.UrlProtocol;
import com.htmlui.client.webview.WebViewScreen;
import net.dimaskama.mcef.api.MCEFApi;
import net.dimaskama.mcef.api.MCEFBrowser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.cef.CefClient;
import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.network.CefRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTML UI 客户端入口（1.2.3 - 基于 MCEF Modern/JCEF）
 *
 * 网络通道: htmlui:main（保持与服务端兼容）
 * S→C open_html: {"type":"open_html","pageId":"xxx","html":"<html>...</html>"}
 * C→S query_html: {"type":"query_html","page":"xxx"}
 * C→S ui_action:  {"type":"ui_action","action":"xxx","page":"xxx","data":"...","inputs":{...}}
 *
 * 渲染：通过 MCEF Modern API 创建 JCEF 浏览器实例，离屏渲染为 GPU 纹理
 */
public class HtmlUiClientMod implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("HTMLUI");

    private static String pendingHtml;
    private static String pendingPageId;
    private static boolean mcefInitialized = false;
    private static boolean mcefInitFailed = false;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[HTML UI] 客户端初始化中 (v1.2.3 - MCEF/JCEF)...");

        // 加载配置
        HtmlUiConfig.load();

        // 注册网络通道
        PayloadTypeRegistry.serverboundPlay().register(HtmlUiPayload.TYPE, HtmlUiPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HtmlUiPayload.TYPE, HtmlUiPayload.STREAM_CODEC);

        ClientPlayNetworking.registerGlobalReceiver(HtmlUiPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> handleServerPacket(payload.json()));
        });

        // 注册下载进度 HUD（右上角显示 JCEF 内核下载/初始化进度）
        DownloadProgressHud.register();

        // 异步初始化 MCEF（会从 maven 下载 JCEF natives，约 100MB，首次运行需要联网）
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            if (HtmlUiConfig.get().enabled) {
                initMcefAsync();
            }
        });

        // 主线程 tick：打开待显示界面
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (pendingHtml != null && client.player != null) {
                openPending(client);
            }
        });

        LOGGER.info("[HTML UI] 客户端初始化完成");
    }

    /** 异步初始化 MCEF（首次会下载 JCEF natives） */
    public static void initMcefAsync() {
        if (mcefInitialized || mcefInitFailed) return;
        MCEFApi.initialize().getFuture().whenComplete((api, err) -> {
            if (err != null) {
                LOGGER.error("[HTML UI] MCEF 初始化失败: {}", err.getMessage());
                mcefInitFailed = true;
                DownloadMonitor.get().onFailed();
                return;
            }
            LOGGER.info("[HTML UI] MCEF 初始化完成，JCEF 已就绪");
            mcefInitialized = true;
            DownloadProgressHud.markDone();
            // 注册全局 URL 拦截器（拦截 cmd:/page:/action:/close:/refresh: 协议）
            try {
                CefClient client = api.getClient();
                client.addRequestHandler(new CefRequestHandlerAdapter() {
                    @Override
                    public boolean onBeforeBrowse(org.cef.browser.CefBrowser browser,
                                                   org.cef.browser.CefFrame frame,
                                                   CefRequest request,
                                                   boolean userGesture,
                                                   boolean isRedirect) {
                        String url = request.getURL();
                        if (url == null) return false;
                        // 拦截自定义协议（非 http/https/about/data）
                        UrlProtocol.ParsedUrl parsed = UrlProtocol.parse(url);
                        if (parsed.type == UrlProtocol.ActionType.EXTERNAL
                                || parsed.type == UrlProtocol.ActionType.UNKNOWN) {
                            return false;
                        }
                        // 自定义协议：交给当前 WebViewScreen 处理
                        WebViewScreen.handleUrlIntercepted(browser, parsed);
                        return true; // 阻止真实导航
                    }
                });
                LOGGER.info("[HTML UI] 全局 URL 拦截器已注册");
            } catch (Throwable t) {
                LOGGER.warn("[HTML UI] 注册 URL 拦截器失败: {}", t.getMessage());
            }
        });
    }

    public static boolean isMcefReady() { return mcefInitialized; }
    public static boolean isMcefFailed() { return mcefInitFailed; }

    private static void handleServerPacket(String json) {
        try {
            JsonObject data = JsonParser.parseString(json).getAsJsonObject();
            String type = data.has("type") ? data.get("type").getAsString() : "";
            if ("open_html".equals(type)) {
                String html = data.has("html") ? data.get("html").getAsString() : "";
                String pageId = data.has("pageId") ? data.get("pageId").getAsString() : "";
                LOGGER.info("[HTML UI] 收到: {} ({} chars)", pageId, html.length());
                pendingHtml = html;
                pendingPageId = pageId;
            } else if ("error".equals(type)) {
                String msg = data.has("message") ? data.get("message").getAsString() : "未知错误";
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.sendSystemMessage(Component.literal("[HTML UI] " + msg).withStyle(ChatFormatting.RED));
                }
            }
        } catch (Exception e) {
            LOGGER.error("[HTML UI] 解析数据包失败: {}", e.getMessage());
        }
    }

    private static void openPending(Minecraft client) {
        String html = pendingHtml;
        String pageId = pendingPageId;
        pendingHtml = null;
        pendingPageId = null;

        if (!HtmlUiConfig.get().enabled) {
            if (client.player != null) {
                client.player.sendSystemMessage(Component.literal("[HTML UI] 已禁用（配置中 enabled=false）")
                        .withStyle(ChatFormatting.YELLOW));
            }
            return;
        }

        if (!mcefInitialized) {
            if (mcefInitFailed) {
                if (client.player != null) {
                    client.player.sendSystemMessage(Component.literal("[HTML UI] MCEF 初始化失败，无法打开界面")
                            .withStyle(ChatFormatting.RED));
                    client.player.sendSystemMessage(Component.literal("[HTML UI] 检查日志，确认 JCEF natives 是否下载成功")
                            .withStyle(ChatFormatting.YELLOW));
                }
                return;
            }
            // MCEF 还在初始化，触发一次初始化，保留 pending 让下一 tick 重试
            initMcefAsync();
            pendingHtml = html;
            pendingPageId = pageId;
            if (client.player != null) {
                client.player.sendSystemMessage(Component.literal("[HTML UI] 浏览器内核初始化中，请稍候...")
                        .withStyle(ChatFormatting.YELLOW));
            }
            return;
        }

        try {
            client.gui.setScreen(new WebViewScreen(html, pageId));
        } catch (Exception e) {
            LOGGER.error("[HTML UI] WebView 启动失败: {}", e.getMessage());
            if (client.player != null) {
                client.player.sendSystemMessage(Component.literal("[HTML UI] WebView 启动失败: " + e.getMessage())
                        .withStyle(ChatFormatting.RED));
            }
        }
    }

    // ==================== C→S 发送方法 ====================

    /** 请求服务端发送指定页面的 HTML */
    public static void queryHtml(String pageId) {
        send("{\"type\":\"query_html\",\"page\":\"" + esc(pageId) + "\"}");
    }

    /** 发送按钮 action 到服务端 */
    public static void sendUiAction(String actionJson) {
        send("{\"type\":\"ui_action\"," + actionJson.substring(1));
    }

    private static void send(String json) {
        try {
            ClientPlayNetworking.send(new HtmlUiPayload(json));
        } catch (Exception e) {
            LOGGER.error("[HTML UI] 发送失败: {}", e.getMessage());
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
