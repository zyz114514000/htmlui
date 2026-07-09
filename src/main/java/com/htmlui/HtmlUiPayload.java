package com.htmlui;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * HTML UI 自定义数据包 - 在 htmlui:main 通道上传输 UTF-8 JSON 字符串
 */
public record HtmlUiPayload(String json) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("htmlui", "main");

    public static final CustomPacketPayload.Type<HtmlUiPayload> TYPE =
            new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, HtmlUiPayload> STREAM_CODEC =
            StreamCodec.ofMember(HtmlUiPayload::write, HtmlUiPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeBytes(json.getBytes(StandardCharsets.UTF_8));
    }

    private static HtmlUiPayload read(FriendlyByteBuf buf) {
        int length = buf.readableBytes();
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return new HtmlUiPayload(new String(bytes, StandardCharsets.UTF_8));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
