package com.htmlui.client.config;

import com.htmlui.client.Theme;
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
 * 离线包下载渠道选择屏幕 - 列出三个网盘，点击用系统浏览器打开。
 *
 * 用途：
 * - 用户主动从 ConfigScreen「下载离线包」按钮进入
 * - 下载超时/慢速时由 DownloadProgressHud 弹 ConfirmScreen 引导进入
 *
 * 三个渠道：123云盘（不限速）、百度网盘、QQ群文件
 * 居中布局，无描述文字。
 */
public class OfflinePackageSelectionScreen extends Screen {

    private final Screen parent;
    private final List<AbstractWidget> widgets = new ArrayList<>();

    public OfflinePackageSelectionScreen(Screen parent) {
        super(Component.literal("下载离线包"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        widgets.clear();
        int cx = this.width / 2;
        int rowH = 28;

        // 计算内容总高度，垂直居中
        int buttonCount = OfflinePackageLinks.ALL.length; // 3 个网盘
        int copyRowH = 24;
        int separatorGap = 10;
        int totalContentH = buttonCount * rowH + separatorGap + copyRowH + separatorGap + 20 + 10 + 20; // 复制行+安装说明+返回
        int startY = Math.max(50, (this.height - totalContentH) / 2);
        int y = startY;

        // 网盘按钮（居中）
        for (OfflinePackageLinks.Entry entry : OfflinePackageLinks.ALL) {
            String label = entry.label + "  " + ChatFormatting.GRAY + entry.hint;
            widgets.add(Button.builder(
                    Component.literal(label),
                    b -> OfflinePackageLinks.openInBrowser(entry.url)
            ).bounds(cx - 150, y, 300, 20)
             .tooltip(Tooltip.create(Component.literal(
                     "点击用系统浏览器打开\n" + entry.url)))
             .build());
            y += rowH;
        }

        // 复制链接按钮组
        y += separatorGap;
        int bw = 90, gap = 6;
        int totalW = bw * 3 + gap * 2;
        int bx = cx - totalW / 2;
        for (int i = 0; i < OfflinePackageLinks.ALL.length; i++) {
            final OfflinePackageLinks.Entry e = OfflinePackageLinks.ALL[i];
            String shortName = switch (i) {
                case 0 -> "复制 123云盘";
                case 1 -> "复制 百度网盘";
                default -> "复制 QQ群";
            };
            widgets.add(Button.builder(
                    Component.literal(shortName),
                    b -> copyToClipboard(e.url)
            ).bounds(bx + i * (bw + gap), y, bw, 20).build());
        }
        y += copyRowH;

        // 安装说明
        y += separatorGap;
        widgets.add(Button.builder(
                Component.literal(ChatFormatting.AQUA + "查看安装说明"),
                b -> OfflinePackageLinks.openInBrowser("https://qm.qq.com/q/vVd4wpgKgo")
        ).bounds(cx - 100, y, 200, 20).build());
        y += 28;

        // 返回按钮
        widgets.add(Button.builder(Component.literal("完成"), b -> onClose())
                .bounds(cx - 60, y, 120, 20).build());

        for (AbstractWidget w : widgets) addRenderableWidget(w);
    }

    private void copyToClipboard(String text) {
        try {
            Minecraft mc = Minecraft.getInstance();
            mc.keyboardHandler.setClipboard(text);
            com.htmlui.client.NotificationToast.success("链接已复制到剪贴板");
        } catch (Throwable t) {
            com.htmlui.client.NotificationToast.warning("复制失败: " + t.getMessage());
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        var font = Minecraft.getInstance().font;
        // 标题居中
        g.centeredText(font, Component.literal("下载离线包"), this.width / 2, 20, Theme.TEXT_GOLD);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }
}
