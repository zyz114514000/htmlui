package com.htmlui.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Toast 通知 - 屏幕右上角短暂提示
 */
public class NotificationToast {

    private static final List<ToastEntry> active = new ArrayList<>();
    private static final int MAX = 5;
    private static final long DURATION = 3000;

    public enum Type {
        SUCCESS(Theme.TEXT_GREEN, "成功"), ERROR(Theme.TEXT_RED, "失败"),
        INFO(Theme.SKY_BLUE, "信息"), WARNING(Theme.ORANGE, "警告");
        public final int color; public final String name;
        Type(int c, String n) { color = c; name = n; }
    }

    private static class ToastEntry {
        final Type type; final String msg; final long created;
        ToastEntry(Type t, String m) { type = t; msg = m; created = System.currentTimeMillis(); }
        boolean expired() { return System.currentTimeMillis() - created > DURATION; }
        float alpha() {
            long el = System.currentTimeMillis() - created, rem = DURATION - el;
            if (rem < 500) return rem / 500.0f;
            if (el < 200) return el / 200.0f;
            return 1.0f;
        }
    }

    public static void success(String m) { show(Type.SUCCESS, m); }
    public static void error(String m) { show(Type.ERROR, m); }
    public static void info(String m) { show(Type.INFO, m); }
    public static void warning(String m) { show(Type.WARNING, m); }

    public static void show(Type type, String msg) {
        active.removeIf(ToastEntry::expired);
        if (active.size() >= MAX) active.remove(0);
        active.add(new ToastEntry(type, msg));
    }

    public static void render(GuiGraphicsExtractor g) {
        active.removeIf(ToastEntry::expired);
        if (active.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Font font = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth();
        int tw = 200, th = 28, gap = 4;
        int sx = sw - tw - 8, sy = 8;
        for (int i = 0; i < active.size(); i++) {
            ToastEntry t = active.get(i);
            int a = (int) (t.alpha() * 255) << 24;
            int y = sy + i * (th + gap);
            g.fill(sx, y, sx + tw, y + th, (Theme.BG_PANEL & 0x00FFFFFF) | a);
            g.fill(sx, y, sx + 3, y + th, (t.type.color & 0x00FFFFFF) | a);
            g.text(font, Component.literal("[" + t.type.name + "]"), sx + 8, y + 4, (t.type.color & 0x00FFFFFF) | a, true);
            String msg = t.msg;
            if (font.width(msg) > tw - 16) {
                while (font.width(msg + "...") > tw - 16 && !msg.isEmpty()) msg = msg.substring(0, msg.length() - 1);
                msg += "...";
            }
            g.text(font, Component.literal(msg), sx + 8, y + 15, (Theme.TEXT_WHITE & 0x00FFFFFF) | a, true);
        }
    }

    public static void clear() { active.clear(); }
}
