package kz.aitu.auth.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * Singleton manager responsible for reading, writing, and validating
 * the local AITU session token stored in .minecraft/config/aitu_session.json.
 */
public class SessionManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionManager.class);
    private static final String FILE_NAME = "aitu_session.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final SessionManager INSTANCE = new SessionManager();

    private SessionData cachedSession = null;
    private long lastModifiedTime = -1;

    private SessionManager() {
        loadSession();
    }

    public static SessionManager getInstance() {
        return INSTANCE;
    }

    /**
     * Resolves the config file path, safely handling cases where FMLPaths is not yet initialized.
     */
    public Path getConfigPath() {
        try {
            Path configDir = FMLPaths.CONFIGDIR.get();
            if (configDir != null) {
                return configDir.resolve(FILE_NAME);
            }
        } catch (Throwable ignored) {
            // Fallback for standalone/test environments
        }
        return Path.of("config", FILE_NAME);
    }

    /**
     * Checks if a valid session exists on disk and in memory.
     */
    public synchronized boolean hasValidSession() {
        checkAndReloadIfModified();
        return cachedSession != null && cachedSession.isValid();
    }

    /**
     * Gets the currently loaded session if valid.
     */
    public synchronized Optional<SessionData> getSession() {
        checkAndReloadIfModified();
        if (cachedSession != null && cachedSession.isValid()) {
            return Optional.of(cachedSession);
        }
        return Optional.empty();
    }

    /**
     * Retrieves the active session token, or null if no valid session is present.
     */
    public synchronized String getSessionToken() {
        return getSession().map(SessionData::getSessionToken).orElse(null);
    }

    /**
     * Reads and parses .minecraft/config/aitu_session.json.
     * Clean, robust error handling for missing/malformed JSON files.
     *
     * @return true if successfully loaded a valid session, false otherwise.
     */
    public synchronized boolean loadSession() {
        Path path = getConfigPath();
        if (!Files.exists(path)) {
            LOGGER.debug("[AITU Auth] Session config file not found at: {}", path.toAbsolutePath());
            cachedSession = null;
            lastModifiedTime = -1;
            return false;
        }

        try {
            long currentModified = Files.getLastModifiedTime(path).toMillis();
            this.lastModifiedTime = currentModified;

            String content = Files.readString(path).trim();
            if (content.isEmpty()) {
                LOGGER.warn("[AITU Auth] Session file at {} is empty.", path.toAbsolutePath());
                cachedSession = null;
                return false;
            }

            SessionData session = GSON.fromJson(content, SessionData.class);
            if (session == null || !session.isValid()) {
                LOGGER.warn("[AITU Auth] Session file at {} contains invalid or incomplete session data.", path.toAbsolutePath());
                cachedSession = null;
                return false;
            }

            this.cachedSession = session;
            LOGGER.info("[AITU Auth] Loaded valid AITU session for player '{}' (Telegram ID: {}).",
                    session.getCachedNickname(), session.getTelegramId());
            return true;
        } catch (JsonSyntaxException e) {
            LOGGER.error("[AITU Auth] Malformed JSON in session file {}: {}", path.toAbsolutePath(), e.getMessage());
            cachedSession = null;
            return false;
        } catch (IOException e) {
            LOGGER.error("[AITU Auth] Failed to read session file {}: {}", path.toAbsolutePath(), e.getMessage());
            cachedSession = null;
            return false;
        } catch (Exception e) {
            LOGGER.error("[AITU Auth] Unexpected error while parsing session file {}: {}", path.toAbsolutePath(), e.getMessage());
            cachedSession = null;
            return false;
        }
    }

    /**
     * Saves session token, nickname, and telegram id to .minecraft/config/aitu_session.json.
     */
    public synchronized void saveSession(String sessionToken, String cachedNickname, long telegramId) throws IOException {
        if (sessionToken == null || sessionToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Session token must not be null or empty.");
        }
        if (cachedNickname == null || cachedNickname.trim().isEmpty()) {
            throw new IllegalArgumentException("Cached nickname must not be null or empty.");
        }

        Path path = getConfigPath();
        Path parent = path.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        SessionData newSession = new SessionData(sessionToken.trim(), cachedNickname.trim(), telegramId);
        String json = GSON.toJson(newSession);

        // Atomic write via temp file
        Path tempFile = path.resolveSibling(FILE_NAME + ".tmp");
        Files.writeString(tempFile, json);
        Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        this.cachedSession = newSession;
        this.lastModifiedTime = Files.getLastModifiedTime(path).toMillis();
        LOGGER.info("[AITU Auth] Successfully saved AITU session for '{}' to {}", cachedNickname, path.toAbsolutePath());
    }

    /**
     * Deletes the session configuration file and clears memory cache.
     */
    public synchronized void clearSession() {
        Path path = getConfigPath();
        try {
            if (Files.exists(path)) {
                Files.delete(path);
                LOGGER.info("[AITU Auth] Removed session file at {}", path.toAbsolutePath());
            }
        } catch (IOException e) {
            LOGGER.error("[AITU Auth] Failed to delete session file: {}", e.getMessage());
        } finally {
            this.cachedSession = null;
            this.lastModifiedTime = -1;
        }
    }

    /**
     * Parses user input from the auth screen.
     * Supports:
     * 1. Full JSON format {"session_token": "...", "cached_nickname": "...", "telegram_id": ...}
     * 2. Delimited format: <telegram_id>:<token> or <telegram_id>_<token>
     * 3. Raw token string (defaults nickname to current user, telegram_id to 0 or parsed if provided)
     */
    public SessionData parseInputToken(String rawInput, String fallbackNickname, long fallbackTelegramId) {
        if (rawInput == null) return null;
        String trimmed = rawInput.trim();
        if (trimmed.isEmpty()) return null;

        // Try JSON parsing
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                JsonObject json = JsonParser.parseString(trimmed).getAsJsonObject();
                String token = json.has("session_token") ? json.get("session_token").getAsString() : null;
                String nick = json.has("cached_nickname") ? json.get("cached_nickname").getAsString() : fallbackNickname;
                long tgId = json.has("telegram_id") ? json.get("telegram_id").getAsLong() : fallbackTelegramId;

                if (token != null && !token.trim().isEmpty()) {
                    return new SessionData(token, nick, tgId);
                }
            } catch (Exception ignored) {
            }
        }

        // Try delimited format: <telegram_id>:<token>
        if (trimmed.contains(":")) {
            int colonIdx = trimmed.indexOf(':');
            String left = trimmed.substring(0, colonIdx).trim();
            String right = trimmed.substring(colonIdx + 1).trim();
            try {
                long tgId = Long.parseLong(left);
                if (!right.isEmpty()) {
                    return new SessionData(right, fallbackNickname, tgId);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        // Plain token format
        return new SessionData(trimmed, fallbackNickname, fallbackTelegramId);
    }

    private void checkAndReloadIfModified() {
        Path path = getConfigPath();
        if (Files.exists(path)) {
            try {
                long mtime = Files.getLastModifiedTime(path).toMillis();
                if (mtime != this.lastModifiedTime || cachedSession == null) {
                    loadSession();
                }
            } catch (IOException ignored) {
            }
        } else if (cachedSession != null) {
            cachedSession = null;
            lastModifiedTime = -1;
        }
    }
}
