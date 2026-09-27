package kz.aitu.auth.server;

import kz.aitu.auth.api.AituApiClient;
import net.minecraft.client.multiplayer.ServerData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Manages the pinned AITU official server at Index 0 in the multiplayer server list.
 * Dynamically updates server IP and Name from AituApiClient.fetchServerInfo().
 */
public class AituServerManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(AituServerManager.class);

    public static final String DEFAULT_IP = "play.aitu-gaming.com:25565";
    public static final String DEFAULT_NAME = "AITU Official Server";

    private static volatile String serverIp = DEFAULT_IP;
    private static volatile String serverName = DEFAULT_NAME;
    private static volatile int onlinePlayers = 0;
    private static volatile boolean dynamicInfoLoaded = false;

    public static String getServerIp() {
        return serverIp;
    }

    public static String getServerName() {
        return serverName;
    }

    public static int getOnlinePlayers() {
        return onlinePlayers;
    }

    public static boolean isDynamicInfoLoaded() {
        return dynamicInfoLoaded;
    }

    /**
     * Checks if a given ServerData instance represents the pinned AITU server.
     */
    public static boolean isAituServer(ServerData serverData) {
        if (serverData == null) return false;
        return isAituServer(serverData.name, serverData.ip);
    }

    /**
     * Checks if given server name and IP correspond to the official AITU server.
     */
    public static boolean isAituServer(String name, String ip) {
        if (ip != null) {
            String cleanIp = ip.trim().toLowerCase();
            if (cleanIp.equals(serverIp.toLowerCase()) || cleanIp.equals(DEFAULT_IP.toLowerCase()) || cleanIp.contains("aitu-gaming.com")) {
                return true;
            }
        }
        if (name != null) {
            String cleanName = name.trim();
            if (cleanName.equalsIgnoreCase(serverName) || cleanName.equalsIgnoreCase(DEFAULT_NAME) || cleanName.contains("AITU")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ensures that the AITU official server is pinned at Index 0 of the given server list.
     * If an existing AITU server entry is present, updates its IP/name and moves it to index 0.
     * If not present, creates a new entry and inserts it at index 0.
     */
    public static void ensurePinnedServer(List<ServerData> list) {
        if (list == null) return;

        ServerData aituServer = null;
        for (int i = 0; i < list.size(); i++) {
            ServerData data = list.get(i);
            if (isAituServer(data)) {
                aituServer = data;
                list.remove(i);
                break;
            }
        }

        if (aituServer == null) {
            aituServer = new ServerData(serverName, serverIp, ServerData.Type.OTHER);
        } else {
            aituServer.name = serverName;
            aituServer.ip = serverIp;
        }

        // Always pin at index 0
        list.add(0, aituServer);
    }

    /**
     * Asynchronously queries the backend for the latest server IP and name,
     * updating the cached values.
     */
    public static void updateServerInfoAsync(Runnable onComplete) {
        AituApiClient.getInstance().fetchServerInfo().thenAccept(info -> {
            if (info != null) {
                if (info.ip() != null && !info.ip().isBlank()) {
                    serverIp = info.ip();
                }
                if (info.name() != null && !info.name().isBlank()) {
                    serverName = info.name();
                }
                onlinePlayers = info.online();
                dynamicInfoLoaded = info.success();
                LOGGER.info("[AITU Hub] Dynamic server info: {} ({}) - Online: {}", serverName, serverIp, onlinePlayers);
            }
            if (onComplete != null) {
                onComplete.run();
            }
        }).exceptionally(ex -> {
            LOGGER.warn("[AITU Hub] Could not refresh server info: {}", ex.getMessage());
            if (onComplete != null) {
                onComplete.run();
            }
            return null;
        });
    }
}
