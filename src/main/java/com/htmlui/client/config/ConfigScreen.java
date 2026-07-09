package com.htmlui.client.config;

import com.htmlui.client.HtmlUiClientMod;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * HTML UI 配置屏幕 - 在 ModMenu 里点「设置」打开
 *
 * 自适应布局：
 * - 根据屏幕高度动态调整行距（26→22→20）
 * - 内容垂直居中（如果空间够）
 * - 内容超出屏幕时启用滚动（鼠标滚轮）
 *
 * 选项：开关、默认缩放、分辨率等级、透明背景、调试覆盖层、文本回退、国内镜像、MCEF 状态、下载离线包
 * 底部按钮：重置、完成
 */
public class ConfigScreen extends Screen {

    private final Screen parent;
    private final List<AbstractWidget> widgets = new ArrayList<>();

    /** 滚动偏移（向下滚动为正） */
    private int scrollOffset = 0;
    /** 内容总高度（所有按钮 + 间距） */
    private int contentHeight = 0;
    /** 滚动区域顶部 Y（标题下方） */
    private int scrollTop = 0;
    /** 滚动区域底部 Y（底部按钮上方） */
    private int scrollBottom = 0;
    /** 最大滚动偏移 */
    private int maxScroll = 0;

    public ConfigScreen(Screen parent) {
        super(Component.literal("HTML UI 设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        widgets.clear();
        HtmlUiConfig cfg = HtmlUiConfig.get();
        int cx = this.width / 2;
        int rowH = 26;

        // 滚动区域：标题(30+10) 下方到底部按钮上方
        scrollTop = 44;
        scrollBottom = this.height - 30;
        int visibleH = scrollBottom - scrollTop;

        // 计算需要的总高度（11 个按钮 + 1 个间距分隔）
        int buttonCount = 10; // 8 个选项 + MCEF状态 + 下载离线包
        int separatorGap = 10; // 下载离线包前的额外间距
        int neededH = buttonCount * rowH + separatorGap;

        // 如果装不下，减小行距
        if (neededH > visibleH) {
            rowH = 22;
            neededH = buttonCount * rowH + separatorGap;
            if (neededH > visibleH) {
                rowH = 20;
                neededH = buttonCount * rowH + separatorGap;
            }
        }

        contentHeight = neededH;
        maxScroll = Math.max(0, contentHeight - visibleH);

        // 限制滚动偏移在有效范围
        if (scrollOffset < 0) scrollOffset = 0;
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        // 起始 Y：如果内容比可见区小，垂直居中；否则从顶部开始
        int startY = scrollTop + Math.max(0, (visibleH - contentHeight) / 2);
        int y = startY - scrollOffset;

        // 总开关
        widgets.add(Button.builder(
                Component.literal("HTML UI: " + (cfg.enabled ? "开启" : "关闭")),
                b -> {
                    cfg.enabled = !cfg.enabled;
                    HtmlUiConfig.save();
                    clearWidgets();
                    init();
                }).bounds(cx - 100, y, 200, 20).build());
        y += rowH;

        // 默认缩放
        widgets.add(Button.builder(
                Component.literal("默认缩放: " + String.format("%.2f", cfg.defaultScale)),
                b -> {
                    float[] scales = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
                    int idx = 0;
                    for (int i = 0; i < scales.length; i++) {
                        if (Math.abs(scales[i] - cfg.defaultScale) < 0.01f) { idx = i; break; }
                    }
                    cfg.defaultScale = scales[(idx + 1) % scales.length];
                    HtmlUiConfig.save();
                    clearWidgets();
                    init();
                }).bounds(cx - 100, y, 200, 20)
                .tooltip(Tooltip.create(Component.literal("越大字越大（渲染分辨率越低）")))
                .build());
        y += rowH;

        // 分辨率等级
        int recLevel = HardwareEvaluator.recommendedLevel();
        String recTag = (recLevel == cfg.resolutionLevel)
                ? ChatFormatting.GREEN + " (推荐)"
                : ChatFormatting.YELLOW + " (推荐 " + levelName(recLevel) + ")";
        widgets.add(Button.builder(
                Component.literal("分辨率: " + levelName(cfg.resolutionLevel) + recTag),
                b -> {
                    int next = cfg.resolutionLevel % 4 + 1;
                    cfg.resolutionLevel = next;
                    HtmlUiConfig.save();
                    clearWidgets();
                    init();
                }).bounds(cx - 100, y, 200, 20)
                .tooltip(Tooltip.create(Component.literal(
                        "等级越高越清晰但开销越大\n" +
                        HardwareEvaluator.describe())))
                .build());
        y += rowH;

        // 透明背景
        widgets.add(Button.builder(
                Component.literal("透明背景: " + (cfg.transparent ? "开启" : "关闭")),
                b -> {
                    cfg.transparent = !cfg.transparent;
                    HtmlUiConfig.save();
                    clearWidgets();
                    init();
                }).bounds(cx - 100, y, 200, 20).build());
        y += rowH;

        // 调试覆盖层
        widgets.add(Button.builder(
                Component.literal("调试信息: " + (cfg.debugOverlay ? "开启" : "关闭")),
                b -> {
                    cfg.debugOverlay = !cfg.debugOverlay;
                    HtmlUiConfig.save();
                    clearWidgets();
                    init();
                }).bounds(cx - 100, y, 200, 20).build());
        y += rowH;

        // 文本回退
        widgets.add(Button.builder(
                Component.literal("文本回退: " + (cfg.fallbackToText ? "开启" : "关闭")),
                b -> {
                    cfg.fallbackToText = !cfg.fallbackToText;
                    HtmlUiConfig.save();
                    clearWidgets();
                    init();
                }).bounds(cx - 100, y, 200, 20)
                .tooltip(Tooltip.create(Component.literal("加载失败时回退到文本渲染")))
                .build());
        y += rowH;

        // 国内镜像下载
        String mirrorLabel = cfg.useChinaMirror
                ? ChatFormatting.GREEN + "国内镜像: 开启 (阿里云/腾讯云)"
                : ChatFormatting.YELLOW + "国内镜像: 关闭 (官方源)";
        widgets.add(Button.builder(
                Component.literal(mirrorLabel),
                b -> {
                    cfg.useChinaMirror = !cfg.useChinaMirror;
                    HtmlUiConfig.save();
                    clearWidgets();
                    init();
                }).bounds(cx - 100, y, 200, 20)
                .tooltip(Tooltip.create(Component.literal(
                        "开启：优先从国内镜像下载 JCEF 内核（快）\n" +
                        "关闭：直接用官方 Maven Central（国外源）\n" +
                        "镜像下载失败会自动回退到官方源\n" +
                        "仅对在线下载版有效，full jar 已预打包内核")))
                .build());
        y += rowH;

        // MCEF 状态
        String status;
        if (HtmlUiClientMod.isMcefFailed()) {
            status = ChatFormatting.RED + "MCEF 初始化失败";
        } else if (HtmlUiClientMod.isMcefReady()) {
            status = ChatFormatting.GREEN + "MCEF 就绪";
        } else {
            status = ChatFormatting.YELLOW + "MCEF 初始化中";
        }
        widgets.add(Button.builder(
                Component.literal("浏览器内核状态: " + status),
                b -> HtmlUiClientMod.initMcefAsync()).bounds(cx - 100, y, 200, 20)
                .tooltip(Tooltip.create(Component.literal("点击重新触发初始化")))
                .build());
        y += rowH;

        // 下载离线包
        widgets.add(Button.builder(
                Component.literal(ChatFormatting.AQUA + "下载离线包"),
                b -> Minecraft.getInstance().setScreenAndShow(new OfflinePackageSelectionScreen(this))
        ).bounds(cx - 100, y, 200, 20)
         .tooltip(Tooltip.create(Component.literal(
                 "在线下载过慢/失败时，可从网盘下载免下载版 jar\n" +
                 "免下载版无需联网即可使用")))
         .build());
        y += rowH + separatorGap;

        // 底部按钮（固定在屏幕底部，不随滚动）
        int bw = 80, gap = 8;
        int totalW = bw * 2 + gap;
        int bx = cx - totalW / 2;
        int bottomY = this.height - 24;
        widgets.add(Button.builder(Component.literal("完成"), b -> onClose())
                .bounds(bx, bottomY, bw, 20).build());
        widgets.add(Button.builder(Component.literal("重置"), b -> {
            HtmlUiConfig fresh = new HtmlUiConfig();
            cfg.enabled = fresh.enabled;
            cfg.defaultScale = fresh.defaultScale;
            cfg.transparent = fresh.transparent;
            cfg.debugOverlay = fresh.debugOverlay;
            cfg.fallbackToText = fresh.fallbackToText;
            cfg.resolutionLevel = HardwareEvaluator.recommendedLevel();
            cfg.useChinaMirror = fresh.useChinaMirror;
            HtmlUiConfig.save();
            clearWidgets();
            init();
        }).bounds(bx + bw + gap, bottomY, bw, 20).build());

        for (AbstractWidget w : widgets) addRenderableWidget(w);
    }

    /** 鼠标滚轮滚动 */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll > 0 && scrollY != 0) {
            // scrollY > 0 向上滚（减少 offset），< 0 向下滚（增加 offset）
            int delta = (int) Math.signum(scrollY) * 20;
            int newOffset = scrollOffset - delta;
            if (newOffset < 0) newOffset = 0;
            if (newOffset > maxScroll) newOffset = maxScroll;
            if (newOffset != scrollOffset) {
                scrollOffset = newOffset;
                clearWidgets();
                init();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** 等级 → 名称 */
    private static String levelName(int level) {
        return switch (level) {
            case 1 -> "极低(0.5x)";
            case 2 -> "低(0.65x)";
            case 3 -> "中(0.85x)";
            case 4 -> "高(1.0x)";
            default -> "中(0.85x)";
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // 先渲染背景（在 scissor 之外）
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        var font = Minecraft.getInstance().font;

        // 标题居中
        g.centeredText(font, Component.literal("HTML UI 设置"), this.width / 2, 18, 0xFFFFD700);

        // 如果需要滚动，绘制滚动条提示
        if (maxScroll > 0) {
            // 右侧滚动条
            int barX = this.width / 2 + 110;
            int barY = scrollTop;
            int barH = scrollBottom - scrollTop;
            int thumbH = Math.max(20, barH * barH / (barH + maxScroll));
            int thumbY = barY + (barH - thumbH) * scrollOffset / maxScroll;
            g.fill(barX, barY, barX + 2, barY + barH, 0x40808080);
            g.fill(barX, thumbY, barX + 2, thumbY + thumbH, 0xFFC5A059);

            // 滚动提示文字
            String scrollHint = ChatFormatting.GRAY + "滚轮滚动查看更多";
            g.centeredText(font, Component.literal(scrollHint), this.width / 2, this.height - 38, 0xFF8A8A8A);
        }
    }

    @Override
    public void onClose() {
        HtmlUiConfig.save();
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }
}
