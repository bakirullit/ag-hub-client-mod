package kz.aitu.auth.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Local client configuration for toggles such as auto-join and notifications.
 * Stored in .minecraft/config/aitu_client_config.json
 */
public class AituClientConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(AituClientConfig.class);
    private static final String FILE_NAME = "aitu_client_config.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final AituClientConfig INSTANCE = new AituClientConfig();

    private boolean autoJoinLaunch = false;
    private boolean showServerNotifications = true;

    private AituClientConfig() {
        load();
    }

    public static AituClientConfig getInstance() {
        return INSTANCE;
    }

    public Path getConfigPath() {
        try {
            Path configDir = FMLPaths.CONFIGDIR.get();
            if (configDir != null) {
                return configDir.resolve(FILE_NAME);
            }
        } catch (Throwable ignored) {
        }
        return Path.of("config", FILE_NAME);
    }

    public boolean isAutoJoinLaunch() {
        return autoJoinLaunch;
    }

    public void setAutoJoinLaunch(boolean autoJoinLaunch) {
        this.autoJoinLaunch = autoJoinLaunch;
        save();
    }

    public boolean isShowServerNotifications() {
        return showServerNotifications;
    }

    public void setShowServerNotifications(boolean showServerNotifications) {
        this.showServerNotifications = showServerNotifications;
        save();
    }

    public synchronized void load() {
        Path path = getConfigPath();
        if (!Files.exists(path)) {
            return;
        }

        try {
            String content = Files.readString(path).trim();
            if (!content.isEmpty()) {
                JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
                if (obj.has("auto_join_launch")) {
                    this.autoJoinLaunch = obj.get("auto_join_launch").getAsBoolean();
                }
                if (obj.has("show_server_notifications")) {
                    this.showServerNotifications = obj.get("show_server_notifications").getAsBoolean();
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[AITU Hub] Could not load client config: {}", e.getMessage());
        }
    }

    public synchronized void save() {
        Path path = getConfigPath();
        try {
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            JsonObject obj = new JsonObject();
            obj.addProperty("auto_join_launch", autoJoinLaunch);
            obj.addProperty("show_server_notifications", showServerNotifications);

            Files.writeString(path, GSON.toJson(obj));
        } catch (IOException e) {
            LOGGER.error("[AITU Hub] Could not save client config: {}", e.getMessage());
        }
    }
}
