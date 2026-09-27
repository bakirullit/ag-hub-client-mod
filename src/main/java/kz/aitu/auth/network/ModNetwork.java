package kz.aitu.auth.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles payload registration during RegisterPayloadHandlersEvent on the MOD bus.
 * Uses configuration-to-server and configuration-to-client payloads for the AITU auth handshake.
 */
public class ModNetwork {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModNetwork.class);

    public static void registerPayloads(final RegisterPayloadHandlersEvent event) {
        LOGGER.info("[AITU Auth] Registering network payloads under namespace 'aitu_auth'...");
        final PayloadRegistrar registrar = event.registrar("aitu_auth").optional();

        // 1. Client-to-server configuration payload
        // Payload ID: aitu_auth:token_payload
        registrar.configurationToServer(
                AuthTokenPayload.TYPE,
                AuthTokenPayload.STREAM_CODEC,
                (payload, context) -> {
                    // Server-side handler placeholder for client-only mod
                }
        );

        // 2. Server-to-client configuration task trigger payload
        // Payload ID: aitu_auth:challenge_payload
        registrar.configurationToClient(
                ChallengePayload.TYPE,
                ChallengePayload.STREAM_CODEC,
                ClientPayloadHandler::handleChallenge
        );

        LOGGER.info("[AITU Auth] Network configuration payloads successfully registered.");
    }
}
