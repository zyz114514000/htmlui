package com.htmlui.client.config;

import net.dimaskama.mcef.impl.MCEFModern;
import net.dimaskama.mcef.impl.NativesExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * JCEF 国内镜像下载器 - 优先从国内镜像下载 jcef-natives，失败回退到官方源。
 *
 * 流程：
 * 1. 检查 installDir 是否已安装（NativesExtractor.isInstalled）→ 直接返回 true
 * 2. 遍历镜像 URL 列表（阿里云、腾讯云）
 * 3. 对每个镜像：HEAD 确认可用 → GET 下载到临时文件（边下载边报进度）→ 提取 .tar.gz → installFromTarGz
 * 4. 某个镜像成功 → 返回 true
 * 5. 所有镜像失败 → 返回 false（由调用方回退到 jcefmaven 官方下载）
 *
 * 进度通过 {@link DownloadMonitor#onMirrorProgress} 报告，HUD 实时显示。
 */
public final class ChinaMirrorDownloader {

    private static final Logger LOGGER = LoggerFactory.getLogger("HTMLUI-Mirror");

    /** JCEF natives artifact 路径（Maven 坐标转 URL 路径） */
    private static final String ARTIFACT_PATH = "me/friwi/jcef-natives-windows-amd64/"
            + MCEFModern.JCEF_VERSION_TAG
            + "/jcef-natives-windows-amd64-" + MCEFModern.JCEF_VERSION_TAG + ".jar";

    /** 国内镜像列表（按优先级排序） */
    private static final Mirror[] MIRRORS = {
        new Mirror(DownloadMonitor.Source.MIRROR_ALIYUN,
                "https://maven.aliyun.com/repository/public/"),
        new Mirror(DownloadMonitor.Source.MIRROR_TENCENT,
                "https://mirrors.cloud.tencent.com/nexus/repository/maven-public/"),
    };

    /** 下载超时（毫秒）：单镜像连接超时 15s，读取超时 60s */
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 60_000;
    /** 进度更新最小间隔（毫秒） */
    private static final long PROGRESS_UPDATE_INTERVAL_MS = 500L;

    private ChinaMirrorDownloader() {}

    /**
     * 尝试从国内镜像下载并安装 JCEF natives。
     * @return true 表示已安装成功（含之前已安装的情况）；false 表示所有镜像都失败
     */
    public static boolean tryDownloadAndInstall() {
        // 1. 已安装
        if (NativesExtractor.isInstalled()) {
            LOGGER.info("JCEF natives already installed, skip mirror download");
            DownloadMonitor.get().setSource(DownloadMonitor.Source.BUNDLED);
            return true;
        }

        // 2. 遍历镜像尝试下载
        for (Mirror mirror : MIRRORS) {
            String url = mirror.baseRepoUrl + ARTIFACT_PATH;
            try {
                LOGGER.info("Trying mirror: {}", mirror.source.label);
                if (downloadAndInstallFrom(mirror, url)) {
                    LOGGER.info("Mirror {} download succeeded", mirror.source.label);
                    return true;
                }
            } catch (Throwable e) {
                LOGGER.warn("Mirror {} failed: {}", mirror.source.label, e.getMessage());
            }
        }
        LOGGER.warn("All mirrors failed, will fall back to official maven central");
        return false;
    }

    /** 从单个镜像下载并安装 */
    private static boolean downloadAndInstallFrom(Mirror mirror, String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setRequestProperty("User-Agent", "HTMLUI-Mod/1.2.3");
        conn.setInstanceFollowRedirects(true);

        int code = conn.getResponseCode();
        if (code != 200) {
            LOGGER.warn("Mirror {} returned HTTP {}", mirror.source.label, code);
            conn.disconnect();
            return false;
        }

        long totalBytes = conn.getContentLengthLong();
        if (totalBytes <= 0) {
            LOGGER.warn("Mirror {} returned unknown content length", mirror.source.label);
            conn.disconnect();
            return false;
        }

        LOGGER.info("Downloading {} bytes from {}", totalBytes, mirror.source.label);

        // 下载到临时文件
        Path tempJar = Files.createTempFile("jcef-natives-mirror", ".jar");
        try {
            long[] windowStartMs = { System.currentTimeMillis() };
            long[] windowStartBytes = { 0L };
            long[] downloaded = { 0L };
            long lastUpdate = 0L;

            try (InputStream in = conn.getInputStream();
                 java.io.OutputStream out = Files.newOutputStream(tempJar)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    downloaded[0] += n;

                    // 节流进度更新（每 500ms）
                    long now = System.currentTimeMillis();
                    if (now - lastUpdate > PROGRESS_UPDATE_INTERVAL_MS) {
                        float pct = downloaded[0] * 100f / totalBytes;
                        long windowMs = now - windowStartMs[0];
                        long bytesInWindow = downloaded[0] - windowStartBytes[0];
                        long speedBps = windowMs > 0 ? bytesInWindow * 1000L / windowMs : 0;

                        DownloadMonitor.get().onMirrorProgress(mirror.source, pct, speedBps);

                        // 滑动窗口重置（每 5 秒重开一次窗口）
                        if (windowMs > 5000) {
                            windowStartMs[0] = now;
                            windowStartBytes[0] = downloaded[0];
                        }
                        lastUpdate = now;
                    }
                }
            }

            // 验证下载完整性
            if (downloaded[0] != totalBytes) {
                LOGGER.warn("Mirror {} download incomplete: {}/{} bytes",
                        mirror.source.label, downloaded[0], totalBytes);
                return false;
            }

            // 下载完成，更新进度到 100%
            DownloadMonitor.get().onMirrorProgress(mirror.source, 100f, 0);

            // 从下载的 jar 提取 .tar.gz 并安装
            DownloadMonitor.get().setStage(net.dimaskama.mcef.api.MCEFApi.Initialization.Stage.EXTRACTING);
            boolean ok = extractAndInstall(tempJar);
            if (ok) {
                DownloadMonitor.get().setSource(mirror.source);
                return true;
            } else {
                return false;
            }
        } finally {
            conn.disconnect();
            try { Files.deleteIfExists(tempJar); } catch (IOException ignored) {}
        }
    }

    /** 从 jcef-natives jar 提取 .tar.gz entry 并安装 */
    private static boolean extractAndInstall(Path jarFile) {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(jarFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(".tar.gz")) {
                    LOGGER.info("Extracting {} from jar", entry.getName());
                    // 复制到临时 .tar.gz 文件（因为 installFromTarGz 会关闭流）
                    Path tempGz = Files.createTempFile("jcef-natives", ".tar.gz");
                    try {
                        Files.copy(zis, tempGz, StandardCopyOption.REPLACE_EXISTING);
                        try (InputStream gzIn = Files.newInputStream(tempGz)) {
                            return NativesExtractor.installFromTarGz(gzIn);
                        }
                    } finally {
                        try { Files.deleteIfExists(tempGz); } catch (IOException ignored) {}
                    }
                }
            }
            LOGGER.warn("No .tar.gz entry found in jcef-natives jar");
            return false;
        } catch (Throwable e) {
            LOGGER.error("Failed to extract and install from jar", e);
            return false;
        }
    }

    /** 镜像定义 */
    private static final class Mirror {
        final DownloadMonitor.Source source;
        final String baseRepoUrl;

        Mirror(DownloadMonitor.Source source, String baseRepoUrl) {
            this.source = source;
            this.baseRepoUrl = baseRepoUrl;
        }
    }
}
