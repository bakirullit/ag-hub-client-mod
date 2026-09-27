package kz.aitu.auth.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-server payload sent during the Configuration network phase
 * containing the long-lived session token.
 * Payload ID: aitu_auth:token_payload
 */
public record AuthTokenPayload(String sessionToken) implements CustomPacketPayload {

    public static final Type<AuthTokenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("aitu_auth", "token_payload")
    );

    public static final StreamCodec<ByteBuf, AuthTokenPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            AuthTokenPayload::sessionToken,
            AuthTokenPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
