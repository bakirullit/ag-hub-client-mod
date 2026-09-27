package kz.aitu.auth.network;

import kz.aitu.auth.config.SessionManager;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles incoming server-to-client configuration payloads on the client side.
 */
public class ClientPayloadHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientPayloadHandler.class);

    /**
     * Responds to the server's authentication challenge during the Configuration network phase.
     */
    public static void handleChallenge(final ChallengePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            LOGGER.info("[AITU Auth] Received server authentication challenge (phase: Configuration)");

            SessionManager sessionManager = SessionManager.getInstance();
            if (!sessionManager.hasValidSession()) {
                LOGGER.warn("[AITU Auth] No valid session found in config/aitu_session.json. Disconnecting from server.");
                context.disconnect(Component.literal(
                        "§c§l[AITU Auth] Account Linking Required!\n\n" +
                        "§fThis server requires authentication with your AITU account.\n" +
                        "§ePlease open the Main Menu and click §6'Link AITU Account'§e to connect your Telegram account."
                ));
                return;
            }

            String token = sessionManager.getSessionToken();
            if (token == null || token.trim().isEmpty()) {
                LOGGER.warn("[AITU Auth] Stored session token is null or empty. Disconnecting.");
                context.disconnect(Component.literal(
                        "§c§l[AITU Auth] Invalid Session Token!\n\n" +
                        "§fYour session file is present but contains an empty token.\n" +
                        "§ePlease re-link your account in the Main Menu."
                ));
                return;
            }

            LOGGER.info("[AITU Auth] Sending AuthTokenPayload with session token to server...");
            context.reply(new AuthTokenPayload(token));
        }).exceptionally(throwable -> {
            LOGGER.error("[AITU Auth] Exception while handling challenge: {}", throwable.getMessage(), throwable);
            context.disconnect(Component.literal("§c[AITU Auth] Authentication failed: " + throwable.getMessage()));
            return null;
        });
    }
}
