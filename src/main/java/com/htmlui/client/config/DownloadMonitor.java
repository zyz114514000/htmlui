package com.htmlui.client.config;

import net.dimaskama.mcef.api.MCEFApi;

/**
 * JCEF natives 下载状态监控器（单例）。
 *
 * 数据来源：jcefmaven 的 IProgressHandler 回调（在 JCEF IO 线程触发），
 * 通过 {@link #onProgress(MCEFApi.Initialization.Stage, float)} 更新。
 *
 * HUD 主线程读取 {@link #getStage()} / {@link #getPercent()} 等字段渲染进度条，
 * 并根据进度变化频率估算下载速度、检测超时/慢速。
 *
 * 触发警告后，HUD 会弹出 ConfirmScreen 引导用户下载离线包。
 */
public final class DownloadMonitor {

    private static final DownloadMonitor INSTANCE = new DownloadMonitor();

    /** JCEF natives 包总大小（约 162MB，用于估算下载速度） */
    public static final long TOTAL_BYTES = 162_000_000L;
    /** 多久没进度更新视为"卡住"（毫秒） */
    public static final long STALL_THRESHOLD_MS = 20_000L;
    /** 速度低于此值视为"慢速"（字节/秒，300KB/s） */
    public static final long SLOW_THRESHOLD_BPS = 300L * 1024L;
    /** 两次警告之间的最小间隔（毫秒），避免反复弹窗 */
    public static final long WARN_COOLDOWN_MS = 60_000L;
    /** 速度估算窗口大小（毫秒），用最近 N 秒的进度变化算平均速度 */
    public static final long SPEED_WINDOW_MS = 5_000L;

    private volatile MCEFApi.Initialization.Stage stage = MCEFApi.Initialization.Stage.NOT_STARTED;
    private volatile float percent = -1f;
    /** 当前下载源 */
    private volatile Source source = Source.NONE;

    /** 当前阶段开始时间 */
    private volatile long stageStartMs = 0L;
    /** 最后一次进度更新时间 */
    private volatile long lastProgressMs = 0L;
    /** 速度窗口起点（毫秒） */
    private volatile long windowStartMs = 0L;
    /** 速度窗口起点对应的百分比 */
    private volatile float windowStartPercent = -1f;
    /** 当前估算速度（字节/秒） */
    private volatile long currentSpeedBps = 0L;

    /** 上次警告时间（避免重复弹窗） */
    private volatile long lastWarnMs = 0L;
    /** 当前警告类型（null 表示无） */
    private volatile WarnType activeWarn = null;

    /** 下载源 */
    public enum Source {
        NONE("等待"),
        MIRROR_ALIYUN("阿里云镜像"),
        MIRROR_TENCENT("腾讯云镜像"),
        OFFICIAL("官方 Maven"),
        BUNDLED("内置离线包");

        public final String label;
        Source(String l) { label = l; }
    }

    public enum WarnType {
        STALLED("网络不稳定，下载已停滞"),
        SLOW("下载速度过慢（<300KB/s）"),
        FAILED("下载失败");

        public final String label;
        WarnType(String l) { label = l; }
    }

    private DownloadMonitor() {}

    public static DownloadMonitor get() { return INSTANCE; }

    /**
     * 由 {@code MCEFApiImpl.handleProgress} 在 JCEF IO 线程调用。
     * 更新阶段/百分比/速度估算。线程安全（字段都是 volatile）。
     */
    public void onProgress(MCEFApi.Initialization.Stage newStage, float newPercent) {
        long now = System.currentTimeMillis();

        // 阶段切换
        if (newStage != this.stage) {
            this.stage = newStage;
            this.stageStartMs = now;
            this.lastProgressMs = now;
            this.windowStartMs = now;
            this.windowStartPercent = newPercent;
            this.currentSpeedBps = 0L;
            // 进入新阶段时清除"卡住"警告（可能新阶段本来就没进度回调）
            if (newStage != MCEFApi.Initialization.Stage.DOWNLOADING) {
                this.activeWarn = null;
            }
        }

        // 百分比变化
        if (newPercent != this.percent) {
            this.percent = newPercent;
            this.lastProgressMs = now;

            // 只在 DOWNLOADING 阶段算速度
            if (this.stage == MCEFApi.Initialization.Stage.DOWNLOADING && newPercent >= 0) {
                // 窗口过期 → 重新开窗
                if (now - windowStartMs > SPEED_WINDOW_MS) {
                    windowStartMs = now;
                    windowStartPercent = newPercent;
                } else if (windowStartPercent >= 0 && newPercent > windowStartPercent) {
                    long windowMs = now - windowStartMs;
                    if (windowMs > 500) { // 至少 0.5s 才算
                        float pctDelta = newPercent - windowStartPercent;
                        long bytesDelta = (long) (pctDelta / 100f * TOTAL_BYTES);
                        currentSpeedBps = bytesDelta * 1000L / windowMs;
                        // 重新开窗，平滑速度
                        windowStartMs = now;
                        windowStartPercent = newPercent;
                    }
                }
            }
        }
    }

    /** 下载失败时调用 */
    public void onFailed() {
        long now = System.currentTimeMillis();
        if (now - lastWarnMs > WARN_COOLDOWN_MS) {
            activeWarn = WarnType.FAILED;
            lastWarnMs = now;
        }
    }

    /**
     * 镜像下载进度更新（由 ChinaMirrorDownloader 在下载线程调用）。
     * 直接设置百分比和速度，不依赖阶段切换。
     */
    public void onMirrorProgress(Source src, float percentVal, long speedBps) {
        this.source = src;
        this.stage = MCEFApi.Initialization.Stage.DOWNLOADING;
        this.percent = percentVal;
        this.lastProgressMs = System.currentTimeMillis();
        if (speedBps >= 0) {
            this.currentSpeedBps = speedBps;
        }
    }

    /** 设置下载源（不改变进度） */
    public void setSource(Source src) {
        this.source = src;
    }

    /** 设置阶段（用于镜像下载完成后的状态切换） */
    public void setStage(MCEFApi.Initialization.Stage s) {
        this.stage = s;
        if (s == MCEFApi.Initialization.Stage.DONE) {
            this.percent = 100f;
        }
    }

    /** 重置（用于"重试"按钮或镜像失败回退官方时） */
    public void reset() {
        stage = MCEFApi.Initialization.Stage.NOT_STARTED;
        percent = -1f;
        source = Source.NONE;
        stageStartMs = 0L;
        lastProgressMs = 0L;
        windowStartMs = 0L;
        windowStartPercent = -1f;
        currentSpeedBps = 0L;
        activeWarn = null;
        lastWarnMs = 0L;
    }

    /** 仅重置进度数据（保留 source），用于镜像失败回退官方时 */
    public void resetProgress() {
        percent = -1f;
        lastProgressMs = 0L;
        windowStartMs = 0L;
        windowStartPercent = -1f;
        currentSpeedBps = 0L;
        activeWarn = null;
    }

    // ---- 读取（HUD 主线程调用） ----

    public MCEFApi.Initialization.Stage getStage() { return stage; }
    public float getPercent() { return percent; }
    public long getSpeedBps() { return currentSpeedBps; }
    public Source getSource() { return source; }

    /** 是否处于下载相关阶段（HUD 显示条件） */
    public boolean isDownloadRelatedStage() {
        return stage == MCEFApi.Initialization.Stage.DOWNLOADING
                || stage == MCEFApi.Initialization.Stage.EXTRACTING
                || stage == MCEFApi.Initialization.Stage.INSTALL
                || stage == MCEFApi.Initialization.Stage.INITIALIZING
                || stage == MCEFApi.Initialization.Stage.NOT_STARTED;
    }

    /** 是否处于"活跃下载"阶段（用于超时/慢速检测） */
    public boolean isActivelyDownloading() {
        return stage == MCEFApi.Initialization.Stage.DOWNLOADING;
    }

    /** 是否卡住（超过阈值没进度更新） */
    public boolean isStalled() {
        if (!isActivelyDownloading()) return false;
        if (lastProgressMs == 0L) return false;
        return System.currentTimeMillis() - lastProgressMs > STALL_THRESHOLD_MS;
    }

    /** 是否慢速（估算速度低于阈值） */
    public boolean isSlow() {
        if (!isActivelyDownloading()) return false;
        return currentSpeedBps > 0 && currentSpeedBps < SLOW_THRESHOLD_BPS;
    }

    /** 是否需要弹出警告（含冷却） */
    public WarnType pollWarning() {
        if (activeWarn != null) {
            WarnType w = activeWarn;
            activeWarn = null;
            lastWarnMs = System.currentTimeMillis();
            return w;
        }
        long now = System.currentTimeMillis();
        if (now - lastWarnMs < WARN_COOLDOWN_MS) return null;
        if (isStalled()) {
            lastWarnMs = now;
            return WarnType.STALLED;
        }
        if (isSlow()) {
            lastWarnMs = now;
            return WarnType.SLOW;
        }
        return null;
    }

    /** 格式化速度为可读字符串 */
    public static String formatSpeed(long bps) {
        if (bps <= 0) return "—";
        if (bps < 1024) return bps + " B/s";
        if (bps < 1024 * 1024) return String.format("%.1f KB/s", bps / 1024.0);
        return String.format("%.2f MB/s", bps / (1024.0 * 1024.0));
    }
}
