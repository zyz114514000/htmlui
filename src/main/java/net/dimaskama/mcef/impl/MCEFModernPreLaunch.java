package net.dimaskama.mcef.impl;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import java.io.IOException;
import java.nio.file.Files;

public class MCEFModernPreLaunch implements PreLaunchEntrypoint {

    @Override
    public void onPreLaunch() {
        System.setProperty("java.awt.headless", "false");
        try {
            Files.createDirectories(MCEFModern.JCEF_PATH);
            Files.createDirectories(MCEFModern.CACHE_PATH);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create MCEF directories", e);
        }
        // 若是"免下载版"（jar 内打包了 natives），在此解压到 JCEF_PATH
        // 普通版无 bundled natives，会返回 false，后续走 jcefmaven 在线下载
        MCEFModern.NATIVES_READY = NativesExtractor.ensureInstalled();
    }

}
