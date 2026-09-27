package kz.aitu.auth.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Asynchronous HTTP client communicating with AITU gaming backend services.
 * Base URL: https://aitu-gaming.y-not-devs.com
 */
public class AituApiClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(AituApiClient.class);
    public static final String BASE_URL = "https://aitu-gaming.y-not-devs.com";

    private static final AituApiClient INSTANCE = new AituApiClient();
    private static final Gson GSON = new Gson();

    private final HttpClient httpClient;

    private AituApiClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public static AituApiClient getInstance() {
        return INSTANCE;
    }

    public record VerifyResult(boolean success, String sessionToken, long telegramId, String telegramTag, String error) {
        public static VerifyResult ok(String sessionToken, long telegramId, String telegramTag) {
            return new VerifyResult(true, sessionToken, telegramId, telegramTag, null);
        }

        public static VerifyResult fail(String error) {
            return new VerifyResult(false, null, 0, null, error);
        }
    }

    public record ServerInfoResult(boolean success, String ip, String name, int online, String error) {
        public static ServerInfoResult ok(String ip, String name, int online) {
            return new ServerInfoResult(true, ip, name, online, null);
        }

        public static ServerInfoResult fallback(String ip, String name, int online, String error) {
            return new ServerInfoResult(false, ip, name, online, error);
        }
    }

    public record FriendEntry(String nickname, String status, boolean isOnline) {}

    /**
     * Exchanges a 6-digit PIN and Telegram handle for a session token via POST /api/auth/verify.
     */
    public CompletableFuture<VerifyResult> verifyPin(String tag, String pin, String mcNick) {
        String formattedTag = (tag != null && tag.startsWith("@")) ? tag : "@" + (tag == null ? "" : tag.trim());
        String cleanPin = pin == null ? "" : pin.trim();
        String cleanNick = mcNick == null ? "Player" : mcNick.trim();

        JsonObject payload = new JsonObject();
        payload.addProperty("tag", formattedTag);
        payload.addProperty("pin", cleanPin);
        payload.addProperty("mc_nick", cleanNick);

        String jsonBody = GSON.toJson(payload);

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/auth/verify"))
                    .timeout(Duration.ofSeconds(6))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
        } catch (Exception e) {
            LOGGER.error("[AITU API] Invalid request URI: {}", e.getMessage());
            return CompletableFuture.completedFuture(VerifyResult.fail("Invalid request: " + e.getMessage()));
        }

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    int statusCode = response.statusCode();
                    String body = response.body();

                    if (statusCode >= 200 && statusCode < 300) {
                        try {
                            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                            String token = json.has("session_token") ? json.get("session_token").getAsString()
                                    : json.has("token") ? json.get("token").getAsString() : null;

                            long tgId = json.has("telegram_id") ? json.get("telegram_id").getAsLong()
                                    : json.has("id") ? json.get("id").getAsLong() : Math.abs(formattedTag.hashCode());

                            String retTag = json.has("telegram_tag") ? json.get("telegram_tag").getAsString()
                                    : json.has("tag") ? json.get("tag").getAsString() : formattedTag;

                            if (token != null && !token.isBlank()) {
                                return VerifyResult.ok(token, tgId, retTag);
                            } else {
                                return VerifyResult.fail("Server returned empty session token.");
                            }
                        } catch (Exception e) {
                            return VerifyResult.fail("Malformed JSON response from auth server.");
                        }
                    } else {
                        // Extract error detail if available
                        String errorMsg = "Verification failed (HTTP " + statusCode + ")";
                        try {
                            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                            if (json.has("detail")) {
                                errorMsg = json.get("detail").getAsString();
                            } else if (json.has("message")) {
                                errorMsg = json.get("message").getAsString();
                            } else if (json.has("error")) {
                                errorMsg = json.get("error").getAsString();
                            }
                        } catch (Exception ignored) {
                        }
                        return VerifyResult.fail(errorMsg);
                    }
                })
                .exceptionally(ex -> {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    LOGGER.warn("[AITU API] Verification network error: {}", msg);
                    return VerifyResult.fail("Network error: " + (msg != null ? msg : "Connection failed"));
                });
    }

    /**
     * Retrieves server information via GET /api/server/info.
     * Returns: { "ip": "play.aitu-gaming.com:25565", "name": "AITU Official Server", "online": 42 }
     */
    public CompletableFuture<ServerInfoResult> fetchServerInfo() {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/server/info"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                    ServerInfoResult.fallback("play.aitu-gaming.com:25565", "AITU Official Server", 0, e.getMessage())
            );
        }

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                            String ip = json.has("ip") ? json.get("ip").getAsString() : "play.aitu-gaming.com:25565";
                            String name = json.has("name") ? json.get("name").getAsString() : "AITU Official Server";
                            int online = json.has("online") ? json.get("online").getAsInt() : 0;
                            return ServerInfoResult.ok(ip, name, online);
                        } catch (Exception e) {
                            LOGGER.warn("[AITU API] Failed to parse server info JSON: {}", e.getMessage());
                        }
                    }
                    return ServerInfoResult.fallback("play.aitu-gaming.com:25565", "AITU Official Server", 0, "HTTP " + response.statusCode());
                })
                .exceptionally(ex -> {
                    LOGGER.warn("[AITU API] Server info network error: {}", ex.getMessage());
                    return ServerInfoResult.fallback("play.aitu-gaming.com:25565", "AITU Official Server", 0, ex.getMessage());
                });
    }

    /**
     * Retrieves online friends list via GET /api/friends/online.
     */
    public CompletableFuture<List<FriendEntry>> fetchFriends() {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/friends/online"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
        } catch (Exception e) {
            return CompletableFuture.completedFuture(getDefaultDemoFriends());
        }

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    List<FriendEntry> friends = new ArrayList<>();
                    if (response.statusCode() == 200) {
                        try {
                            JsonElement root = JsonParser.parseString(response.body());
                            JsonArray array = null;
                            if (root.isJsonArray()) {
                                array = root.getAsJsonArray();
                            } else if (root.isJsonObject() && root.getAsJsonObject().has("friends")) {
                                array = root.getAsJsonObject().getAsJsonArray("friends");
                            }

                            if (array != null) {
                                for (JsonElement elem : array) {
                                    if (elem.isJsonObject()) {
                                        JsonObject obj = elem.getAsJsonObject();
                                        String nick = obj.has("nickname") ? obj.get("nickname").getAsString()
                                                : obj.has("name") ? obj.get("name").getAsString() : "Student";
                                        String status = obj.has("status") ? obj.get("status").getAsString() : "Idle";
                                        boolean online = !obj.has("online") || obj.get("online").getAsBoolean();
                                        friends.add(new FriendEntry(nick, status, online));
                                    }
                                }
                                return friends;
                            }
                        } catch (Exception e) {
                            LOGGER.warn("[AITU API] Failed to parse friends JSON: {}", e.getMessage());
                        }
                    }
                    LOGGER.info("[AITU API] /api/friends/online returned HTTP {}. Using sample demo friends.", response.statusCode());
                    return getDefaultDemoFriends();
                })
                .exceptionally(ex -> {
                    LOGGER.warn("[AITU API] Friends fetch network error: {}. Using sample demo friends.", ex.getMessage());
                    return getDefaultDemoFriends();
                });
    }

    /**
     * Provides standard sample/cached online friends when the remote backend is unreachable or returns 404.
     */
    public static List<FriendEntry> getDefaultDemoFriends() {
        List<FriendEntry> list = new ArrayList<>();
        list.add(new FriendEntry("Alikhan", "Playing on AITU SMP", true));
        list.add(new FriendEntry("Dias_Kz", "Idle", true));
        list.add(new FriendEntry("Aruzhan_IT", "Playing on AITU SMP", true));
        list.add(new FriendEntry("Nursultan", "Idle", true));
        list.add(new FriendEntry("Temirlan", "Playing on AITU SMP", true));
        return list;
    }
}
