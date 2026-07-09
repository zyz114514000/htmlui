package com.htmlui.server;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 按钮 action 处理器
 *
 * 内置: open:<pageId> / refresh / close / cmd:<命令> / msg:<消息> / tell:<消息>
 * 自定义: registerHandler(action, handler)
 */
public class ActionHandler {

    private final Map<String, Consumer<ActionContext>> handlers = new HashMap<>();

    public ActionHandler() {
        registerBuiltins();
    }

    public void handle(ServerPlayer player, String action, String page, String data, Map<String, String> inputs) {
        HtmlUiServerMod.LOGGER.info("[HTML UI] Action: {} from {} (page={}, data={}, inputs={})",
                action, player.getName().getString(), page, data, inputs);

        if (action.startsWith("open:")) {
            HtmlUiServerMod.getHtmlLoader().sendTo(player, action.substring(5));
            return;
        }
        if (action.equals("refresh") || action.equals("reload")) {
            if (page != null && !page.isEmpty()) HtmlUiServerMod.getHtmlLoader().sendTo(player, page);
            return;
        }
        if (action.equals("close") || action.equals("exit")) return;
        if (action.startsWith("cmd:")) { executeCommand(player, action.substring(4)); return; }
        if (action.startsWith("msg:") || action.startsWith("tell:")) {
            player.sendSystemMessage(Component.literal(action.substring(action.indexOf(':') + 1)));
            return;
        }

        Consumer<ActionContext> handler = handlers.get(action);
        if (handler != null) {
            try {
                handler.accept(new ActionContext(player, action, page, data, inputs));
            } catch (Exception e) {
                HtmlUiServerMod.LOGGER.error("[HTML UI] 处理器异常: {} - {}", action, e.getMessage());
                player.sendSystemMessage(Component.literal("[HTML UI] 动作执行出错: " + action)
                        .withStyle(ChatFormatting.RED));
            }
            return;
        }

        player.sendSystemMessage(Component.literal("[HTML UI] 未知动作: " + action).withStyle(ChatFormatting.YELLOW));
    }

    public void registerHandler(String action, Consumer<ActionContext> handler) {
        handlers.put(action, handler);
    }

    private void registerBuiltins() {
        registerHandler("test_echo", ctx -> ctx.tell("§a[测试回执] §f玩家=" + ctx.player.getName().getString()
                + " §7page=" + ctx.page + " §7data=" + ctx.data));
        registerHandler("greet", ctx -> {
            String who = ctx.inputs.getOrDefault("who", ctx.player.getName().getString());
            String msg = ctx.inputs.getOrDefault("msg", "");
            ctx.tell("§b[" + who + "] §f" + (msg.isEmpty() ? "你好！" : msg));
        });
    }

    private void executeCommand(ServerPlayer player, String cmd) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        server.execute(() -> {
            try {
                server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), cmd);
            } catch (Exception e) {
                HtmlUiServerMod.LOGGER.error("[HTML UI] 执行命令失败: {} - {}", cmd, e.getMessage());
            }
        });
    }

    public class ActionContext {
        public final ServerPlayer player;
        public final String action;
        public final String page;
        public final String data;
        public final Map<String, String> inputs;

        ActionContext(ServerPlayer player, String action, String page, String data, Map<String, String> inputs) {
            this.player = player; this.action = action; this.page = page;
            this.data = data; this.inputs = inputs;
        }

        public void tell(String msg) { player.sendSystemMessage(Component.literal(msg)); }
        public void open(String pageId) { HtmlUiServerMod.getHtmlLoader().sendTo(player, pageId); }
        public void refresh() { if (page != null && !page.isEmpty()) HtmlUiServerMod.getHtmlLoader().sendTo(player, page); }
        public void command(String cmd) { executeCommand(player, cmd); }
        public MinecraftServer server() { return player.level().getServer(); }
    }
}
