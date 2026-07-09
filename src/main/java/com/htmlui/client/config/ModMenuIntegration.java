package com.htmlui.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.screens.Screen;

/**
 * ModMenu 集成入口 - 让 htmlui 在 ModMenu 列表里显示「设置」按钮
 *
 * fabric.mod.json 的 entrypoints.modmenu 指向此类。
 * ModMenu 不是硬依赖（fabric.mod.json 未声明），玩家没装 ModMenu 时此类不会被加载。
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new ConfigScreen(parent);
    }
}
