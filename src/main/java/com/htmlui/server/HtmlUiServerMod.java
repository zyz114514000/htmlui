package com.htmlui.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.htmlui.HtmlUiPayload;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * HTML UI 服务端入口
 *
 * 注册 htmlui:main 通道，接收客户端 query_html / ui_action，
 * 加载 HTML 文件并推送给客户端。
 *
 * HTML 文件目录: config/htmlui/*.html
 */
public class HtmlUiServerMod implements DedicatedServerModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("HTMLUI-Server");

    private static HtmlLoader htmlLoader;
    private static ActionHandler actionHandler;
    private static MinecraftServer server;
    private static final Gson gson = new Gson();

    @Override
    public void onInitializeServer() {
        LOGGER.info("[HTML UI] 服务端初始化中...");

        PayloadTypeRegistry.serverboundPlay().register(HtmlUiPayload.TYPE, HtmlUiPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HtmlUiPayload.TYPE, HtmlUiPayload.STREAM_CODEC);

        htmlLoader = new HtmlLoader();
        actionHandler = new ActionHandler();

        ServerPlayNetworking.registerGlobalReceiver(HtmlUiPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> handlePacket(context.player(), payload.json()));
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                HtmlUiCommand.register(dispatcher, htmlLoader));

        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;
            htmlLoader.init();
            LOGGER.info("[HTML UI] 服务端就绪，HTML 目录: config/htmlui/");
        });

        LOGGER.info("[HTML UI] 服务端初始化完成");
    }

    private static void handlePacket(ServerPlayer player, String json) {
        try {
            JsonObject data = JsonParser.parseString(json).getAsJsonObject();
            String type = data.has("type") ? data.get("type").getAsString() : "";
            switch (type) {
                case "query_html" -> {
                    String page = getStr(data, "page");
                    if (page.isEmpty()) { sendError(player, "query_html 需要 page 字段"); return; }
                    if (!htmlLoader.sendTo(player, page)) sendError(player, "未找到页面: " + page);
                }
                case "ui_action" -> {
                    String action = getStr(data, "action");
                    String page = getStr(data, "page");
                    String dataField = getStr(data, "data");
                    Map<String, String> inputs = extractMap(data, "inputs");
                    if (action.isEmpty()) { sendError(player, "ui_action 需要 action 字段"); return; }
                    actionHandler.handle(player, action, page, dataField, inputs);
                }
                default -> sendError(player, "未知消息类型: " + type);
            }
        } catch (Exception e) {
            LOGGER.error("[HTML UI] 处理数据包失败: {}", e.getMessage());
        }
    }

    // ==================== S→C 发送 ====================

    public static void sendOpenHtml(ServerPlayer player, String pageId, String html) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "open_html");
        obj.addProperty("pageId", pageId);
        obj.addProperty("html", html);
        sendJson(player, obj);
    }

    public static void sendError(ServerPlayer player, String message) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "error");
        obj.addProperty("message", message);
        sendJson(player, obj);
    }

    private static void sendJson(ServerPlayer player, JsonObject obj) {
        ServerPlayNetworking.send(player, new HtmlUiPayload(gson.toJson(obj)));
    }

    public static MinecraftServer getServer() { return server; }
    public static HtmlLoader getHtmlLoader() { return htmlLoader; }
    public static ActionHandler getActionHandler() { return actionHandler; }

    // ==================== JSON 辅助 ====================

    private static String getStr(JsonObject json, String key) {
        if (json != null && json.has(key) && !json.get(key).isJsonNull()) {
            try { return json.get(key).getAsString(); } catch (Exception ignored) {}
        }
        return "";
    }

    private static Map<String, String> extractMap(JsonObject json, String key) {
        Map<String, String> result = new HashMap<>();
        if (json == null || !json.has(key) || !json.get(key).isJsonObject()) return result;
        try {
            JsonObject obj = json.getAsJsonObject(key);
            for (String k : obj.keySet()) {
                if (!obj.get(k).isJsonNull()) {
                    try { result.put(k, obj.get(k).getAsString()); } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        return result;
    }
}
