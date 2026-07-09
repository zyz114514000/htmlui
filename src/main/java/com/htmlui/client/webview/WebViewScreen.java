package com.htmlui.client.webview;

import com.htmlui.client.HtmlUiClientMod;
import com.htmlui.client.NotificationToast;
import com.htmlui.client.config.HtmlUiConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.dimaskama.mcef.api.MCEFApi;
import net.dimaskama.mcef.api.MCEFBrowser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.network.chat.Component;
import org.cef.browser.CefBrowser;
import org.joml.Matrix3x2f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * HTML UI 渲染屏幕 - 基于 MCEF Modern (JCEF/Chromium)
 *
 * 关键设计（与 testmod 一致）：
 * - browser 在 init 里只创建一次（if null 保护），resize 时只调 browser.resize()
 * - blit 区域用 Screen 的 width/height（1:1，无 scale 转换，避免纹理/坐标不匹配）
 * - HTML 用 data:text/html;base64,XXX URL 加载（JCEF 146 无 loadString）
 *
 * URL 协议拦截由 HtmlUiClientMod 注册的全局 CefRequestHandler 处理，
 * 通过 handleUrlIntercepted 静态方法转发给当前活跃实例。
 */
public class WebViewScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger("HTMLUI-WebView");

    /** 当前活跃实例（用于 URL 拦截转发）。WeakHashMap 以 CefBrowser native ref 为 key */
    private static final Map<CefBrowser, WebViewScreen> ACTIVE = new WeakHashMap<>();

    private final String html;
    private final String pageId;
    private MCEFBrowser browser;

    public WebViewScreen(String html, String pageId) {
        super(Component.literal("HTML UI"));
        this.html = html;
        this.pageId = pageId;
    }

    @Override
    protected void init() {
        HtmlUiConfig cfg = HtmlUiConfig.get();
        // 关键：browser 只创建一次。init() 会在 resize 时被重复调用，
        // 如果每次都创建新 browser，旧的 onPaint 还在触发，纹理错乱 → 花屏
        if (browser == null) {
            try {
                MCEFApi api = MCEFApi.getInstance();
                // 用 data: URL 加载 HTML（JCEF 146 无 loadString，用 base64 编码避免转义问题）
                String dataUrl = "data:text/html;charset=utf-8;base64," +
                        Base64.getEncoder().encodeToString(html.getBytes(StandardCharsets.UTF_8));
                browser = api.createBrowser(dataUrl, cfg.transparent);
                ACTIVE.put(browser.getCefBrowser(), this);
                LOGGER.info("[HTML UI] WebView 已创建 page={} size={}x{}", pageId, width, height);
            } catch (Throwable t) {
                LOGGER.error("[HTML UI] 创建 browser 失败: {}", t.getMessage(), t);
                NotificationToast.error("浏览器创建失败: " + t.getMessage());
                onClose();
                return;
            }
        }
        // 每次 init（含 resize）都更新尺寸。
        // 性能优先：browser 内部分辨率 = 物理像素 × 分辨率等级系数。
        // 等级低 → 内部分辨率小 → GPU 开销小（但放大显示会模糊）
        // 等级 4 (1.0x) = 物理像素 = 游戏窗口分辨率（1:1 清晰）
        float ts = totalScale();
        int bw = Math.max(1, (int) (width * ts));
        int bh = Math.max(1, (int) (height * ts));
        browser.resize(bw, bh);
        browser.setFocus(true);
    }

    /** 分辨率等级 → browser 内部渲染缩放系数（最高 1.0x=窗口分辨率） */
    private static float resolutionScale(int level) {
        return switch (level) {
            case 1 -> 0.5f;   // 极低（性能优先，模糊）
            case 2 -> 0.65f;  // 低
            case 3 -> 0.85f;  // 中（默认）
            case 4 -> 1.0f;   // 高（1:1 窗口分辨率，清晰）
            default -> 0.85f;
        };
    }

    /**
     * 逻辑坐标 → browser 内部像素坐标的总缩放系数 = GUI scale × 分辨率等级系数。
     * Screen.width/height 是逻辑坐标，乘以此系数得到 browser 内部分辨率/坐标。
     */
    private static float totalScale() {
        double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        return (float) (guiScale * resolutionScale(HtmlUiConfig.get().resolutionLevel));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (browser != null) {
            GpuTextureView tv = browser.getTextureView();
            if (tv != null) {
                // 全屏 blit，1:1 与 testmod 完全一致
                guiGraphics.guiRenderState.addGuiElement(new BlitRenderState(
                        RenderPipelines.GUI_TEXTURED,
                        TextureSetup.singleTexture(tv, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)),
                        new Matrix3x2f(guiGraphics.pose()),
                        0, 0, width, height,
                        0.0F, 1.0F, 0.0F, 1.0F,
                        0xFFFFFFFF,
                        guiGraphics.scissorStack.peek()
                ));
            }
            guiGraphics.requestCursor(browser.getCursorType());
        }

        NotificationToast.render(guiGraphics);

        if (HtmlUiConfig.get().debugOverlay && browser != null) {
            var font = Minecraft.getInstance().font;
            var win = Minecraft.getInstance().getWindow();
            double gs = win.getGuiScale();
            float rs = resolutionScale(HtmlUiConfig.get().resolutionLevel);
            int bw = (int) (width * gs * rs);
            int bh = (int) (height * gs * rs);
            int physW = (int) (width * gs);
            int physH = (int) (height * gs);
            guiGraphics.text(font, Component.literal(
                    String.format("page=%s gui=%dx%d phys=%dx%d browser=%dx%d (gs=%.1f rs=%.2f)",
                            pageId, width, height, physW, physH, bw, bh, gs, rs)),
                    4, 4, 0xFFFFFF00, true);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (browser != null) {
            // 屏幕坐标 → browser 内部坐标（考虑分辨率缩放）
            MouseButtonEvent e = remapMouse(event);
            browser.onMouseClicked(e, doubled);
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (browser != null) {
            MouseButtonEvent e = remapMouse(event);
            browser.onMouseReleased(e);
        }
        return super.mouseReleased(event);
    }

    @Override
    public void mouseMoved(double x, double y) {
        if (browser != null) {
            int[] bp = toBrowserCoords((int) x, (int) y);
            browser.onMouseMoved(bp[0], bp[1]);
        }
        super.mouseMoved(x, y);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (browser != null) {
            int[] bp = toBrowserCoords((int) mouseX, (int) mouseY);
            browser.onMouseScrolled(bp[0], bp[1], verticalAmount);
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /** 屏幕坐标（逻辑） → browser 内部像素坐标 */
    private int[] toBrowserCoords(int x, int y) {
        float ts = totalScale();
        return new int[]{ (int) (x * ts), (int) (y * ts) };
    }

    /** MouseButtonEvent 坐标转换（逻辑坐标 → browser 像素坐标） */
    private MouseButtonEvent remapMouse(MouseButtonEvent event) {
        float ts = totalScale();
        double nx = event.x() * ts;
        double ny = event.y() * ts;
        return new MouseButtonEvent(nx, ny, event.buttonInfo());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (browser != null) browser.onKeyPressed(event);
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (browser != null) browser.onKeyReleased(event);
        return super.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (browser != null) browser.onCharTyped(event);
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        if (browser != null) {
            try {
                ACTIVE.remove(browser.getCefBrowser());
                browser.close();
            } catch (Throwable t) {
                LOGGER.warn("[HTML UI] 关闭 browser 异常: {}", t.getMessage());
            }
            browser = null;
        }
        if (minecraft != null) minecraft.gui.setScreen(null);
    }

    @Override
    public void removed() {
        if (browser != null) {
            try {
                ACTIVE.remove(browser.getCefBrowser());
                browser.close();
            } catch (Throwable t) {
                LOGGER.warn("[HTML UI] removed 关闭 browser 异常: {}", t.getMessage());
            }
            browser = null;
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    // ==================== URL 拦截处理（由全局 CefRequestHandler 转发） ====================

    public static void handleUrlIntercepted(CefBrowser cefBrowser, UrlProtocol.ParsedUrl parsed) {
        WebViewScreen screen = ACTIVE.get(cefBrowser);
        if (screen == null) {
            LOGGER.warn("[HTML UI] URL 拦截未找到对应 screen: {}", parsed.type);
            return;
        }
        screen.handleUrl(parsed);
    }

    private void handleUrl(UrlProtocol.ParsedUrl parsed) {
        // 关键：此方法在 JCEF IO 线程调用，所有 MC API 操作必须 submit 到主线程
        switch (parsed.type) {
            case COMMAND -> {
                String json = String.format(
                        "{\"action\":\"cmd:%s\",\"page\":\"%s\",\"data\":\"\"}",
                        esc(parsed.content), esc(pageId));
                HtmlUiClientMod.sendUiAction(json);
                runOnMainThread(() -> NotificationToast.info("执行命令: " + parsed.content));
            }
            case PAGE -> {
                HtmlUiClientMod.queryHtml(parsed.content);
                runOnMainThread(() -> NotificationToast.info("跳转: " + parsed.content));
            }
            case SERVER_ACTION -> {
                String json = String.format(
                        "{\"action\":\"%s\",\"page\":\"%s\",\"data\":\"%s\"}",
                        esc(parsed.content), esc(pageId), esc(parsed.data));
                HtmlUiClientMod.sendUiAction(json);
            }
            case CLOSE -> runOnMainThread(this::onClose);
            case REFRESH -> {
                HtmlUiClientMod.queryHtml(pageId);
                runOnMainThread(() -> NotificationToast.info("刷新: " + pageId));
            }
            default -> {}
        }
    }

    /** 把操作提交到 MC 主线程执行（JCEF 回调在 IO 线程，不能直接调 MC API） */
    private static void runOnMainThread(Runnable r) {
        Minecraft.getInstance().execute(r);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
