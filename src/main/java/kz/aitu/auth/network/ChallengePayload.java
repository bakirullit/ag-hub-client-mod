package kz.aitu.auth.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server-to-client configuration task trigger payload.
 * When received during the Configuration network phase, client automatically responds
 * by sending AuthTokenPayload with the stored session_token.
 * Payload ID: aitu_auth:challenge_payload
 */
public record ChallengePayload(String challenge) implements CustomPacketPayload {

    public static final Type<ChallengePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("aitu_auth", "challenge_payload")
    );

    /**
     * StreamCodec resilient to both empty (0-byte) trigger payloads and payloads containing a challenge string.
     */
    public static final StreamCodec<FriendlyByteBuf, ChallengePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                if (payload.challenge() != null && !payload.challenge().isEmpty()) {
                    buffer.writeUtf(payload.challenge());
                }
            },
            buffer -> {
                if (buffer.isReadable()) {
                    return new ChallengePayload(buffer.readUtf());
                }
                return new ChallengePayload("");
            }
    );

    public ChallengePayload() {
        this("");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
