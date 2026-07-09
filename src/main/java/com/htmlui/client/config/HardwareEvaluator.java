package com.htmlui.client.config;

/**
 * 硬件性能评估器 - 根据本机硬件给出推荐分辨率等级
 *
 * 评估维度：
 * - 可用处理器数（逻辑核心）
 * - 最大堆内存（JVM 能用的内存）
 * - 操作系统（Windows 比其他系统更稳）
 *
 * 推荐等级（1-4，最高 1.0x=窗口分辨率）：
 *   1 极低：≤2 核 或 ≤2GB 堆 —— 仅能跑最小分辨率
 *   2 低：  3-4 核 或 ≤4GB 堆 —— 入门级
 *   3 中：  4-6 核 且 4-8GB 堆 —— 主流配置（默认）
 *   4 高：  ≥6 核 且 ≥8GB 堆 —— 高配（1:1 窗口分辨率）
 *
 * 注意：无法直接读到 GPU 显存，按 CPU/内存保守估计。
 */
public final class HardwareEvaluator {

    private HardwareEvaluator() {}

    /** 返回推荐等级 1-4 */
    public static int recommendedLevel() {
        int cores = Runtime.getRuntime().availableProcessors();
        long maxHeapMb = Runtime.getRuntime().maxMemory() / (1024 * 1024);

        // 取 CPU 和内存两个维度的较低值，保守推荐
        int cpuLevel = cpuLevel(cores);
        int memLevel = memLevel(maxHeapMb);
        return Math.min(cpuLevel, memLevel);
    }

    private static int cpuLevel(int cores) {
        if (cores <= 2) return 1;
        if (cores <= 4) return 2;
        if (cores <= 6) return 3;
        return 4;
    }

    private static int memLevel(long heapMb) {
        if (heapMb <= 2048) return 1;
        if (heapMemUnder4G(heapMb)) return 2;
        if (heapMb <= 8192) return 3;
        return 4;
    }

    private static boolean heapMemUnder4G(long heapMb) {
        return heapMb > 2048 && heapMb <= 4096;
    }

    /** 人类可读的硬件描述（用于 tooltip） */
    public static String describe() {
        int cores = Runtime.getRuntime().availableProcessors();
        long maxHeapMb = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        long heapGb = maxHeapMb / 1024;
        int rec = recommendedLevel();
        return String.format(
                "CPU 核心数: %d\n最大堆内存: %dMB (%dGB)\n推荐等级: %d\n" +
                "超过推荐等级可能导致卡顿或崩溃",
                cores, maxHeapMb, heapGb, rec);
    }
}
