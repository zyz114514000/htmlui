package com.htmlui.client.config;

import com.htmlui.client.HtmlUiClientMod;
import com.htmlui.client.Theme;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;

/**
 * JCEF natives 下载进度 HUD - 右上角显示。
 *
 * 显示内容：
 * - 当前阶段（下载中/解压中/安装中/初始化中）
 * - 进度百分比 + 进度条
 * - 下载速度（仅 DOWNLOADING 阶段）
 *
 * 检测超时/慢速时弹出 ConfirmScreen，引导用户下载离线包。
 */
public final class DownloadProgressHud implements HudElement {

    private static boolean registered = false;
    /** 完成后 HUD 淡出时长（毫秒） */
    private static final long FADE_OUT_MS = 3000L;
    /** 完成时间戳，0 表示未完成 */
    private static volatile long doneMs = 0L;
    /** 当前弹出的警告屏幕已展示，避免同一帧多次弹 */
    private static volatile boolean warnScreenShown = false;

    private DownloadProgressHud() {}

    /** 注册到 Fabric HUD 系统（只注册一次） */
    public static void register() {
        if (registered) return;
        registered = true;
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("htmlui", "download_progress"),
                new DownloadProgressHud()
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        DownloadMonitor mon = DownloadMonitor.get();

        // 完成或未开始 → 不渲染（但有淡出）
        net.dimaskama.mcef.api.MCEFApi.Initialization.Stage s = mon.getStage();
        boolean done = s == net.dimaskama.mcef.api.MCEFApi.Initialization.Stage.DONE;
        boolean notStarted = s == net.dimaskama.mcef.api.MCEFApi.Initialization.Stage.NOT_STARTED
                && !HtmlUiClientMod.isMcefReady() && !HtmlUiClientMod.isMcefFailed();

        if (done) {
            if (doneMs == 0L) doneMs = System.currentTimeMillis();
            if (System.currentTimeMillis() - doneMs > FADE_OUT_MS) return;
        } else {
            doneMs = 0L;
        }

        // 未启动初始化时不显示
        if (notStarted && !HtmlUiClientMod.isMcefFailed()) return;

        // 检测警告 → 弹 ConfirmScreen
        checkWarning(g);

        // 渲染进度面板
        render(g, mon, s, done);
    }

    private void checkWarning(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        // 已在配置/选择/确认屏幕里就不弹
        Screen cur = mc.gui.screen();
        if (cur instanceof ConfirmScreen || cur instanceof OfflinePackageSelectionScreen
                || cur instanceof ConfigScreen) return;
        if (warnScreenShown) return;

        DownloadMonitor.WarnType warn = DownloadMonitor.get().pollWarning();
        if (warn == null) return;

        warnScreenShown = true;
        Screen parent = cur; // 保存当前屏幕作为返回父屏幕
        String msg = warn.label + "\n\n建议下载离线包，安装后无需联网即可使用。\n是否现在查看下载渠道？";
        ConfirmScreen screen = new ConfirmScreen(
                (BooleanConsumer) confirmed -> {
                    if (confirmed) {
                        mc.setScreenAndShow(new OfflinePackageSelectionScreen(parent));
                    } else {
                        // 关闭确认屏幕，回到游戏或原屏幕
                        mc.gui.setScreen(parent);
                    }
                    warnScreenShown = false;
                },
                Component.literal("\u00A7eJCEF 下载警告"),
                Component.literal(msg),
                Component.literal("查看下载渠道"),
                Component.literal("稍后")
        );
        mc.setScreenAndShow(screen);
    }

    private void render(GuiGraphicsExtractor g, DownloadMonitor mon,
                        net.dimaskama.mcef.api.MCEFApi.Initialization.Stage s, boolean done) {
        Minecraft mc = Minecraft.getInstance();
        var font = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth();

        int panelW = 220, panelH = 64;
        int x = sw - panelW - 8;
        int y = 8;

        // 淡出 alpha
        long now = System.currentTimeMillis();
        int alpha = 0xF0;
        if (done && doneMs > 0) {
            long elapsed = now - doneMs;
            if (elapsed > 0) {
                alpha = (int) (0xF0 * (1f - elapsed / (float) FADE_OUT_MS));
                if (alpha < 0) alpha = 0;
            }
        }
        int bg = (Theme.BG_PANEL & 0x00FFFFFF) | (alpha << 24);
        int border = (Theme.BORDER_GOLD & 0x00FFFFFF) | (alpha << 24);
        int textCol = (Theme.TEXT_WHITE & 0x00FFFFFF) | (alpha << 24);
        int goldCol = (Theme.GOLD & 0x00FFFFFF) | (alpha << 24);

        // 背景
        g.fill(x, y, x + panelW, y + panelH, bg);
        // 顶部金色边
        g.fill(x, y, x + panelW, y + 2, border);

        // 阶段标签
        String stageName = stageLabel(s);
        g.text(font, Component.literal(stageName), x + 8, y + 6, goldCol, true);

        // 下载源标签（右侧）
        DownloadMonitor.Source src = mon.getSource();
        if (src != DownloadMonitor.Source.NONE) {
            String srcStr = src.label;
            int srcCol = (src == DownloadMonitor.Source.OFFICIAL)
                    ? (Theme.TEXT_DIM & 0x00FFFFFF) | (alpha << 24)
                    : (Theme.EMERALD & 0x00FFFFFF) | (alpha << 24);
            g.text(font, Component.literal(srcStr),
                    x + panelW - 8 - font.width(srcStr), y + 6, srcCol, true);
        }

        // 速度（仅下载中，第二行右侧）
        if (s == net.dimaskama.mcef.api.MCEFApi.Initialization.Stage.DOWNLOADING) {
            String speedStr = DownloadMonitor.formatSpeed(mon.getSpeedBps());
            // 慢速标红
            int speedCol = mon.isSlow() ? (Theme.TEXT_RED & 0x00FFFFFF) | (alpha << 24) : textCol;
            g.text(font, Component.literal(speedStr), x + panelW - 8 - font.width(speedStr), y + 18, speedCol, true);
        }

        // 进度条
        float pct = mon.getPercent();
        int barX = x + 8, barY = y + 30, barW = panelW - 16, barH = 10;
        g.fill(barX, barY, barX + barW, barY + barH, (0xFF151B28 & 0x00FFFFFF) | (alpha << 24));
        if (pct >= 0) {
            int fillW = (int) (barW * Math.min(100f, pct) / 100f);
            int barCol = mon.isStalled() ? (Theme.TEXT_RED & 0x00FFFFFF) | (alpha << 24)
                                          : (Theme.EMERALD & 0x00FFFFFF) | (alpha << 24);
            g.fill(barX, barY, barX + fillW, barY + barH, barCol);
        } else {
            // 未知进度，显示动画条（简单版：占一半）
            int fillW = barW / 3;
            g.fill(barX, barY, barX + fillW, barY + barH, (Theme.SKY_BLUE & 0x00FFFFFF) | (alpha << 24));
        }

        // 百分比文字
        String pctStr = pct >= 0 ? String.format("%.1f%%", pct) : "准备中...";
        g.text(font, Component.literal(pctStr), x + 8, y + 44, textCol, true);

        // 提示文字
        String hint = hintFor(s, mon);
        if (!hint.isEmpty()) {
            int hintX = x + panelW - 8 - font.width(hint);
            g.text(font, Component.literal(hint), hintX, y + 44,
                    (Theme.TEXT_DIM & 0x00FFFFFF) | (alpha << 24), true);
        }
    }

    private static String stageLabel(net.dimaskama.mcef.api.MCEFApi.Initialization.Stage s) {
        return switch (s) {
            case NOT_STARTED -> "\u00A77等待开始...";
            case DOWNLOADING -> "\u00A7b下载 JCEF 内核";
            case EXTRACTING -> "\u00A7e解压中...";
            case INSTALL -> "\u00A7e安装中...";
            case INITIALIZING -> "\u00A7d初始化内核...";
            case DONE -> "\u00A7a内核就绪";
        };
    }

    private static String hintFor(net.dimaskama.mcef.api.MCEFApi.Initialization.Stage s, DownloadMonitor mon) {
        if (s == net.dimaskama.mcef.api.MCEFApi.Initialization.Stage.DOWNLOADING) {
            if (mon.isStalled()) return "\u00A7c已停滞";
            if (mon.isSlow()) return "\u00A7c速度过慢";
        }
        return "";
    }

    /** 由 HtmlUiClientMod 在 MCEF 初始化完成时调用，触发 HUD 淡出 */
    public static void markDone() {
        doneMs = System.currentTimeMillis();
    }
}
