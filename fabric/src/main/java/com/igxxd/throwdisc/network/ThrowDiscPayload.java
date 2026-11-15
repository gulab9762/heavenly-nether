package com.igxxd.throwdisc.network;

import com.igxxd.throwdisc.Constants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ThrowDiscPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ThrowDiscPayload> TYPE = 
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "throw_disc"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ThrowDiscPayload> STREAM_CODEC = 
        StreamCodec.unit(new ThrowDiscPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
