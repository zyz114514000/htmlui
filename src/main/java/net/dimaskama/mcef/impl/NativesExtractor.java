package net.dimaskama.mcef.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

/**
 * 把 mod jar 内打包的 JCEF natives 归档（natives/jcef-natives.tar.gz）解压到 {@link MCEFModern#JCEF_PATH}。
 * 用于"免下载版"：玩家无需联网下载 ~150MB 的 JCEF natives。
 *
 * 流程：
 * 1. 检查 installDir 是否已有 .installed 标记文件 → 已就绪则跳过
 * 2. 从 classpath 读取 natives/jcef-natives.tar.gz
 * 3. gzip 解压 → tar 解析 → 写入 installDir
 * 4. 写入 .installed 标记文件
 *
 * tar 格式参考 POSIX ustar：每个条目 = 512B header + 文件内容(向上对齐 512B)，全零 block 结束。
 */
public final class NativesExtractor {

    /** classpath 内的 natives 归档资源路径 */
    public static final String BUNDLED_RESOURCE = "/natives/jcef-natives.tar.gz";
    /** 解压完成后写入的标记文件名（含 jcefmaven 期望的版本信息） */
    public static final String MARKER_FILE = ".jcef-installed-version";

    private NativesExtractor() {}

    /**
     * 若 classpath 含 bundled natives 且 installDir 未就绪，则解压。
     * @return true 表示 installDir 已就绪（已解压或之前已存在）；false 表示未解压（非 full 版或解压失败）
     */
    public static boolean ensureInstalled() {
        // 1. 已有标记文件 → 已就绪
        Path marker = MCEFModern.JCEF_PATH.resolve(MARKER_FILE);
        if (Files.exists(marker)) {
            return true;
        }

        // 2. 检查 classpath 是否有 bundled natives
        if (!hasBundledNatives()) {
            return false; // 非 full 版，走 jcefmaven 在线下载
        }

        // 3. 解压
        try {
            MCEFModern.LOGGER.info("Detected bundled JCEF natives, extracting to {}", MCEFModern.JCEF_PATH);
            Files.createDirectories(MCEFModern.JCEF_PATH);
            long start = System.currentTimeMillis();
            try (InputStream in = NativesExtractor.class.getResourceAsStream(BUNDLED_RESOURCE)) {
                if (in == null) {
                    MCEFModern.LOGGER.warn("Bundled natives resource not found: {}", BUNDLED_RESOURCE);
                    return false;
                }
                try (GZIPInputStream gz = new GZIPInputStream(in)) {
                    extractTar(gz, MCEFModern.JCEF_PATH);
                }
            }
            // 4. 写标记文件
            Files.writeString(marker, MCEFModern.JCEF_VERSION_TAG);
            long elapsed = System.currentTimeMillis() - start;
            MCEFModern.LOGGER.info("JCEF natives extracted in {}ms", elapsed);
            return true;
        } catch (Throwable e) {
            MCEFModern.LOGGER.error("Failed to extract bundled JCEF natives, will fall back to online download", e);
            return false;
        }
    }

    /** 检查 classpath 是否含 bundled natives 资源 */
    public static boolean hasBundledNatives() {
        return NativesExtractor.class.getResource(BUNDLED_RESOURCE) != null;
    }

    /**
     * 从 .tar.gz 输入流安装 natives 到 {@link MCEFModern#JCEF_PATH}，并写标记文件。
     * 供 {@code ChinaMirrorDownloader} 复用 —— 镜像下载完成后调用此方法解压安装。
     * @param gzInputStream gzip 压缩的 tar 流（调用方负责关闭外层连接）
     * @return true 表示安装成功
     */
    public static boolean installFromTarGz(InputStream gzInputStream) {
        try {
            Files.createDirectories(MCEFModern.JCEF_PATH);
            try (GZIPInputStream gz = new GZIPInputStream(gzInputStream)) {
                extractTar(gz, MCEFModern.JCEF_PATH);
            }
            Files.writeString(MCEFModern.JCEF_PATH.resolve(MARKER_FILE), MCEFModern.JCEF_VERSION_TAG);
            return true;
        } catch (Throwable e) {
            MCEFModern.LOGGER.error("Failed to install natives from tar.gz stream", e);
            return false;
        }
    }

    /** 标记文件是否已存在（natives 已安装） */
    public static boolean isInstalled() {
        return Files.exists(MCEFModern.JCEF_PATH.resolve(MARKER_FILE));
    }

    // ---- tar 解压 ----

    private static final int BLOCK = 512;
    private static final Charset US_ASCII = StandardCharsets.US_ASCII;

    private static void extractTar(InputStream tarIn, Path targetDir) throws IOException {
        byte[] header = new byte[BLOCK];
        long entries = 0;
        while (true) {
            int read = readFully(tarIn, header, 0, BLOCK);
            if (read < BLOCK) break;

            // 全零 block = 结束
            if (isAllZero(header)) break;

            // 解析文件名（name + ustar prefix）
            String name = readString(header, 0, 100);
            if (name.isEmpty()) {
                // 跳过这个 block（可能是 padding）
                continue;
            }
            String prefix = readString(header, 345, 155);
            String fullName = prefix.isEmpty() ? name : prefix + "/" + name;
            // 规范化路径分隔符
            fullName = fullName.replace('\\', '/');

            // 解析大小（八进制）
            long size = parseOctal(header, 124, 12);

            // 类型标志
            byte typeFlag = header[156];

            // 解析出的目标路径（防目录穿越）
            Path target = safeResolve(targetDir, fullName);
            if (target == null) {
                // 路径逃逸，跳过内容
                skipPadded(tarIn, size);
                continue;
            }

            char tf = (char) typeFlag;
            if (tf == '5' || fullName.endsWith("/")) {
                // 目录
                Files.createDirectories(target);
            } else if (tf == '0' || tf == '\0' || tf == '7') {
                // 普通文件
                if (target.getParent() != null) Files.createDirectories(target.getParent());
                try (OutputStream out = Files.newOutputStream(target)) {
                    byte[] buf = new byte[8192];
                    long remaining = size;
                    while (remaining > 0) {
                        int toRead = (int) Math.min(buf.length, remaining);
                        int n = tarIn.read(buf, 0, toRead);
                        if (n < 0) break;
                        out.write(buf, 0, n);
                        remaining -= n;
                    }
                }
                // 设置可执行权限（jcef.dll/chrome.exe 等需要）
                try {
                    target.toFile().setExecutable(true, false);
                } catch (Exception ignored) {}
            } else {
                // 符号链接/硬链接/其他类型：跳过内容
                // （JCEF natives 不应该有链接，但稳妥处理）
            }

            // 跳过 padding（文件内容对齐到 512B）
            skipPadded(tarIn, size);
            entries++;
        }
        MCEFModern.LOGGER.info("Extracted {} tar entries to {}", entries, targetDir);
    }

    private static void skipPadded(InputStream in, long size) throws IOException {
        long padded = (size + BLOCK - 1) / BLOCK * BLOCK - size;
        if (padded > 0) {
            long skipped = 0;
            while (skipped < padded) {
                long n = in.skip(padded - skipped);
                if (n <= 0) break;
                skipped += n;
            }
        }
    }

    private static int readFully(InputStream in, byte[] buf, int off, int len) throws IOException {
        int total = 0;
        while (total < len) {
            int n = in.read(buf, off + total, len - total);
            if (n < 0) return total;
            total += n;
        }
        return total;
    }

    private static boolean isAllZero(byte[] buf) {
        for (byte b : buf) {
            if (b != 0) return false;
        }
        return true;
    }

    private static String readString(byte[] buf, int off, int len) {
        // 去掉末尾的 \0 和空白
        int end = off + len;
        for (int i = off; i < end; i++) {
            if (buf[i] == 0) { end = i; break; }
        }
        return new String(buf, off, end - off, US_ASCII).trim();
    }

    private static long parseOctal(byte[] buf, int off, int len) {
        String s = readString(buf, off, len);
        if (s.isEmpty()) return 0;
        try {
            return Long.parseLong(s, 8);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 解析路径并确保在 baseDir 内，防止目录穿越 */
    private static Path safeResolve(Path baseDir, String relative) {
        Path resolved = baseDir.resolve(relative).normalize();
        Path baseNormalized = baseDir.normalize();
        if (!resolved.startsWith(baseNormalized)) {
            MCEFModern.LOGGER.warn("Skipping tar entry outside target dir: {}", relative);
            return null;
        }
        return resolved;
    }
}
