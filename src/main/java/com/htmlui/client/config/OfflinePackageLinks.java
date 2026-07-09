package com.htmlui.client.config;

import com.htmlui.client.NotificationToast;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.net.URI;

/**
 * 离线包下载渠道 - 当在线下载 JCEF natives 失败/过慢时引导用户使用
 *
 * 三个渠道（按推荐顺序）：
 * - 123云盘（不限速，推荐）
 * - 百度网盘（提取码 mmgf）
 * - QQ群文件（群 1081837109，进群后从群文件下载）
 */
public final class OfflinePackageLinks {

    private static final Logger LOGGER = LoggerFactory.getLogger("HTMLUI-Offline");

    public static final Entry PAN_123 = new Entry(
            "123云盘 (不限速 推荐)",
            "https://1814385081.share.123pan.cn/123pan/2GVbVv-0X20h?pwd=RgGx#",
            "提取码 RgGx"
    );

    public static final Entry BAIDU = new Entry(
            "百度网盘",
            "https://pan.baidu.com/s/1xdeQEsFA_kRQHTGQHil9iw?pwd=mmgf",
            "提取码 mmgf"
    );

    public static final Entry QQ_GROUP = new Entry(
            "QQ群文件 (群 1081837109)",
            "https://qm.qq.com/q/vVd4wpgKgo",
            "进群后从群文件下载"
    );

    /** 按推荐顺序列出全部渠道 */
    public static final Entry[] ALL = { PAN_123, BAIDU, QQ_GROUP };

    private OfflinePackageLinks() {}

    /** 用系统默认浏览器打开指定 URL，失败时提示用户手动复制 */
    public static boolean openInBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                LOGGER.info("Opened browser for {}", url);
                return true;
            }
        } catch (Throwable t) {
            LOGGER.warn("Desktop.browse failed: {}", t.getMessage());
        }
        // 回退：Windows 用 rundll32
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            try {
                Runtime.getRuntime().exec(new String[]{ "rundll32", "url.dll,FileProtocolHandler", url });
                return true;
            } catch (Throwable t) {
                LOGGER.warn("rundll32 fallback failed: {}", t.getMessage());
            }
        }
        NotificationToast.warning("无法打开浏览器，请手动复制链接");
        return false;
    }

    /** 渠道条目 */
    public static final class Entry {
        public final String label;
        public final String url;
        public final String hint;

        public Entry(String label, String url, String hint) {
            this.label = label;
            this.url = url;
            this.hint = hint;
        }
    }
}
