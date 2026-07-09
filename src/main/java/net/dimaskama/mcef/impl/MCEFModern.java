package net.dimaskama.mcef.impl;

import net.dimaskama.mcef.api.MCEFApi;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class MCEFModern implements ClientModInitializer {

    public static final String MOD_ID = "mcef-modern";
    public static final Logger LOGGER = LoggerFactory.getLogger("MCEF Modern");
    public static final Path MOD_DIR = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
    public static final Path JCEF_PATH = MOD_DIR.resolve("jcef");
    public static final Path CACHE_PATH = MOD_DIR.resolve("cache");
    /** JCEF natives 版本标记，与 jcefmaven 期望一致；写入 .jcef-installed-version 用于校验 */
    public static final String JCEF_VERSION_TAG = "jcef-d3de827+cef-146.0.10+g8219561+chromium-146.0.7680.179";
    /** bundled natives 是否已就绪（由 NativesExtractor 在 PreLaunch 阶段设置） */
    public static volatile boolean NATIVES_READY = false;

    @Override
    public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STOPPING.register(MCEFModern::onClientStopping);
    }

    private static void onClientStopping(Minecraft mc) {
        MCEFApi.Initialization initialization = MCEFApiImpl.getInitialization();
        if (initialization != null && initialization.getStage() != MCEFApi.Initialization.Stage.DOWNLOADING) {
            ((MCEFApiImpl) initialization.getFuture().join()).close();
        }
    }

}