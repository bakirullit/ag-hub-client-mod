package kz.aitu.auth;

import kz.aitu.auth.config.SessionManager;
import kz.aitu.auth.network.ModNetwork;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main client mod entry point for aitu_auth_client.
 */
@Mod(value = AituAuthClient.MODID, dist = Dist.CLIENT)
public class AituAuthClient {

    public static final String MODID = "aitu_auth_client";
    public static final String TELEGRAM_BOT_URL = "https://t.me/aitu_gaming_bot?start=link";

    private static final Logger LOGGER = LoggerFactory.getLogger(AituAuthClient.class);

    public AituAuthClient(IEventBus modEventBus) {
        LOGGER.info("[AITU Auth] Initializing AITU Auth Client mod...");

        // Register payload handlers on the MOD bus
        modEventBus.addListener(ModNetwork::registerPayloads);

        // Check local session file on startup
        boolean valid = SessionManager.getInstance().hasValidSession();
        if (valid) {
            SessionManager.getInstance().getSession().ifPresent(session ->
                    LOGGER.info("[AITU Auth] Loaded active session for user: {} (Telegram ID: {})",
                            session.getCachedNickname(), session.getTelegramId())
            );
        } else {
            LOGGER.info("[AITU Auth] No valid session found in config/aitu_session.json. Link button will be displayed on TitleScreen.");
        }

        // Asynchronously fetch latest server info and initialize pinned server cache
        kz.aitu.auth.server.AituServerManager.updateServerInfoAsync(null);
    }
}
