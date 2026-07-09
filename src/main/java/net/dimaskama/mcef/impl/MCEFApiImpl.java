package net.dimaskama.mcef.impl;

import me.friwi.jcefmaven.CefAppBuilder;
import me.friwi.jcefmaven.EnumProgress;
import me.friwi.jcefmaven.IProgressHandler;
import net.dimaskama.mcef.api.MCEFApi;
import net.dimaskama.mcef.api.MCEFBrowser;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.browser.CefRequestContext;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class MCEFApiImpl implements MCEFApi {

    private static volatile InitializationImpl initialization;
    private final CefApp cefApp;
    private final CefClient client;

    private MCEFApiImpl(InitializationImpl initialization) throws Exception {
        CefAppBuilder cefAppBuilder = new CefAppBuilder();
        cefAppBuilder.setInstallDir(MCEFModern.JCEF_PATH.toFile());
        cefAppBuilder.setProgressHandler(initialization);
        cefAppBuilder.addJcefArgs(
                "--autoplay-policy=no-user-gesture-required",
                "--disable-web-security",
                "--enable-widevine-cdm"
        );
        // 免下载版：natives 已由 NativesExtractor 在 PreLaunch 阶段解压到 installDir，
        // 跳过 jcefmaven 的 LOCATING/DOWNLOADING/EXTRACTING/INSTALL 流程，直接进入 INITIALIZING
        if (MCEFModern.NATIVES_READY) {
            MCEFModern.LOGGER.info("Bundled natives ready, skipping jcefmaven download");
            cefAppBuilder.setSkipInstallation(true);
            try {
                com.htmlui.client.config.DownloadMonitor.get().setSource(
                        com.htmlui.client.config.DownloadMonitor.Source.BUNDLED);
            } catch (Throwable ignored) {}
        } else {
            // 普通版：尝试国内镜像下载（如果配置开启）
            boolean mirrorOk = false;
            try {
                if (com.htmlui.client.config.HtmlUiConfig.get().useChinaMirror) {
                    MCEFModern.LOGGER.info("Trying China mirror download first");
                    mirrorOk = com.htmlui.client.config.ChinaMirrorDownloader.tryDownloadAndInstall();
                }
            } catch (Throwable t) {
                MCEFModern.LOGGER.warn("China mirror download failed: {}", t.getMessage());
            }
            if (mirrorOk) {
                MCEFModern.LOGGER.info("China mirror download succeeded, skipping jcefmaven download");
                cefAppBuilder.setSkipInstallation(true);
            } else {
                // 镜像失败或未启用，回退到 jcefmaven 官方 Maven Central 下载
                MCEFModern.LOGGER.info("Falling back to official maven central download");
                try {
                    com.htmlui.client.config.DownloadMonitor.get().resetProgress();
                    com.htmlui.client.config.DownloadMonitor.get().setSource(
                            com.htmlui.client.config.DownloadMonitor.Source.OFFICIAL);
                } catch (Throwable ignored) {}
            }
        }
        cefApp = cefAppBuilder.build();

        CefSettings cefSettings = new CefSettings();
        cefSettings.user_agent_product = "MCEF-Modern/0";
        cefSettings.root_cache_path = MCEFModern.CACHE_PATH.toAbsolutePath().toString();
        cefApp.setSettings(cefSettings);

        client = cefApp.createClient();
    }

    public static Initialization initialize() {
        if (initialization == null) {
            synchronized (MCEFApiImpl.class) {
                if (initialization == null) {
                    initialization = new InitializationImpl();
                }
            }
        }
        return initialization;
    }

    @Nullable
    public static Initialization getInitialization() {
        return initialization;
    }

    @Override
    public MCEFBrowser createBrowser(String url, boolean transparent) {
        MCEFBrowserImpl browser = new MCEFBrowserImpl(
                client,
                url,
                transparent,
                CefRequestContext.getGlobalContext(),
                null
        );
        browser.setCloseAllowed();
        browser.createImmediately();
        return browser;
    }

    @Override
    public org.cef.CefClient getClient() {
        return client;
    }

    public void close() {
        cefApp.dispose();
    }

    private static class InitializationImpl implements Initialization, IProgressHandler {

        private final CompletableFuture<MCEFApi> future;
        private volatile Stage stage = Stage.NOT_STARTED;
        private volatile float percentage = -1.0F;

        private InitializationImpl() {
            future = CompletableFuture.supplyAsync(() -> {
                try {
                    return new MCEFApiImpl(this);
                } catch (Throwable e) {
                    MCEFModern.LOGGER.error("Failed to initialize MCEF Modern", e);
                    stage = Stage.DONE;
                    percentage = -1;
                    throw new RuntimeException("Failed to initialize MCEF Modern", e);
                }
            });
        }

        @Override
        public Stage getStage() {
            return stage;
        }

        @Override
        public float getPercentage() {
            return percentage;
        }

        @Override
        public CompletableFuture<MCEFApi> getFuture() {
            return future;
        }

        @Override
        public void handleProgress(EnumProgress state, float percent) {
            Stage newStage = switch (state) {
                case LOCATING -> Stage.NOT_STARTED;
                case DOWNLOADING -> Stage.DOWNLOADING;
                case EXTRACTING -> Stage.EXTRACTING;
                case INSTALL -> Stage.INSTALL;
                case INITIALIZING -> Stage.INITIALIZING;
                case INITIALIZED -> Stage.DONE;
            };
            stage = newStage;
            percentage = percent;
            // 转发到下载监控器（供 HUD 显示进度/检测超时/慢速）
            try {
                com.htmlui.client.config.DownloadMonitor.get().onProgress(newStage, percent);
            } catch (Throwable ignored) {
                // 监控器异常不能影响 jcef 初始化流程
            }
        }

    }

}
