package com.htmlui.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * HTML UI 客户端配置 - 持久化到 config/htmlui-client.json
 */
public class HtmlUiConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("HTMLUI-Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("htmlui-client.json");

    private static volatile HtmlUiConfig instance = new HtmlUiConfig();

    /** HTML UI 总开关 */
    public boolean enabled = true;
    /** 默认缩放（1.0 = 适配窗口，越大字越大） */
    public float defaultScale = 1.0f;
    /** 透明背景（让 MC 背景透出来） */
    public boolean transparent = false;
    /** 显示调试信息（FPS/页面大小） */
    public boolean debugOverlay = false;
    /** 加载失败时回退到文本渲染（兼容旧版 <ui> 标签） */
    public boolean fallbackToText = true;
    /**
     * 页面渲染分辨率等级（1-4）
     * 1=极低(0.5x) 2=低(0.65x) 3=中(0.85x) 4=高(1.0x=窗口分辨率)
     * 等级越高渲染越清晰但 GPU/CPU 开销越大，低配机器可能卡顿或崩溃
     * 最高 1.0x = 游戏窗口分辨率，不再超采样
     */
    public int resolutionLevel = 3;

    /**
     * 是否优先使用国内镜像下载 JCEF 内核（默认开启）。
     * 开启后：先尝试阿里云/腾讯云镜像下载，失败自动回退到官方 Maven Central。
     * 关闭后：直接用官方 Maven Central（国外源，可能较慢）。
     * 仅对在线下载版（非 full jar）生效；full jar 已预打包内核，无需下载。
     */
    public boolean useChinaMirror = true;

    public static HtmlUiConfig get() { return instance; }

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            save();
            return;
        }
        try {
            String json = Files.readString(CONFIG_PATH);
            HtmlUiConfig loaded = GSON.fromJson(json, HtmlUiConfig.class);
            if (loaded != null) {
                // 钳制 resolutionLevel 到 1-4（移除 1.25x 超采样，最高 1.0x=窗口分辨率）
                if (loaded.resolutionLevel < 1) loaded.resolutionLevel = 1;
                if (loaded.resolutionLevel > 4) loaded.resolutionLevel = 4;
                instance = loaded;
            }
        } catch (Exception e) {
            LOGGER.warn("读取配置失败，使用默认值: {}", e.getMessage());
        }
    }

    public static void save() {
        try {
            Files.writeString(CONFIG_PATH, GSON.toJson(instance));
        } catch (IOException e) {
            LOGGER.warn("保存配置失败: {}", e.getMessage());
        }
    }
}
