package kz.aitu.auth.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import kz.aitu.auth.config.SessionManager;
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
 * Automatically attaches `Authorization: Bearer <session_token>` when SessionManager has a token.
 */
public class AituApiClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(AituApiClient.class);
    public static final String BASE_URL = "https://aitu-gaming.y-not-devs.com";

    private static final AituApiClient INSTANCE = new AituApiClient();
    private static final Gson GSON = new Gson();

    private final HttpClient httpClient;

    private AituApiClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public static AituApiClient getInstance() {
        return INSTANCE;
    }

    // --- Data Records ---

    public record RequestCodeResult(boolean success, String message, String error) {
        public static RequestCodeResult ok(String message) {
            return new RequestCodeResult(true, message, null);
        }

        public static RequestCodeResult fail(String error) {
            return new RequestCodeResult(false, null, error);
        }
    }

    public record VerifyResult(boolean success, String sessionToken, long telegramId, String telegramTag, String error) {
        public static VerifyResult ok(String sessionToken, long telegramId, String telegramTag) {
            return new VerifyResult(true, sessionToken, telegramId, telegramTag, null);
        }

        public static VerifyResult fail(String error) {
            return new VerifyResult(false, null, 0, null, error);
        }
    }

    public record FriendItem(String nickname, String telegramTag, String status, boolean isOnline) {
        public boolean isPlayingSmp() {
            return status != null && status.toLowerCase().contains("aitu smp");
        }
    }

    public record FriendRequestItem(String id, String fromNickname, String fromTag, String timestamp) {}

    public record ActionResult(boolean success, String message, String error) {
        public static ActionResult ok(String message) {
            return new ActionResult(true, message, null);
        }

        public static ActionResult fail(String error) {
            return new ActionResult(false, null, error);
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

    // --- Helper for Authenticated Requests ---

    private HttpRequest.Builder createRequestBuilder(String endpoint) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .timeout(Duration.ofSeconds(6))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");

        String token = SessionManager.getInstance().getSessionToken();
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token.trim());
        }

        return builder;
    }

    private String extractError(HttpResponse<String> response) {
        int statusCode = response.statusCode();
        String body = response.body();
        if (body != null && !body.isBlank()) {
            try {
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                if (json.has("detail")) {
                    return json.get("detail").getAsString();
                } else if (json.has("message")) {
                    return json.get("message").getAsString();
                } else if (json.has("error")) {
                    return json.get("error").getAsString();
                }
            } catch (Exception ignored) {
            }
        }
        return "Server returned HTTP " + statusCode;
    }

    // --- API Endpoints ---

    /**
     * Step 1: Sends POST /api/auth/request-code with {"telegram_tag": "@username", "minecraft_nickname": "<current_nick>"}
     */
    public CompletableFuture<RequestCodeResult> requestCode(String telegramTag, String mcNick) {
        String formattedTag = (telegramTag != null && telegramTag.startsWith("@")) ? telegramTag : "@" + (telegramTag == null ? "" : telegramTag.trim());
        String cleanNick = (mcNick == null || mcNick.isBlank()) ? "Player" : mcNick.trim();

        JsonObject payload = new JsonObject();
        payload.addProperty("telegram_tag", formattedTag);
        payload.addProperty("minecraft_nickname", cleanNick);

        HttpRequest request = createRequestBuilder("/api/auth/request-code")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return RequestCodeResult.ok("Code sent to " + formattedTag);
                    } else {
                        return RequestCodeResult.fail(extractError(response));
                    }
                })
                .exceptionally(ex -> {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    LOGGER.warn("[AITU API] Request code network error: {}", msg);
                    return RequestCodeResult.fail("Network error: " + (msg != null ? msg : "Connection failed"));
                });
    }

    /**
     * Step 2: Exchanges 6-digit code for session token via POST /api/auth/verify.
     */
    public CompletableFuture<VerifyResult> verifyPin(String tag, String pin, String mcNick) {
        String formattedTag = (tag != null && tag.startsWith("@")) ? tag : "@" + (tag == null ? "" : tag.trim());
        String cleanPin = pin == null ? "" : pin.trim();
        String cleanNick = (mcNick == null || mcNick.isBlank()) ? "Player" : mcNick.trim();

        JsonObject payload = new JsonObject();
        payload.addProperty("telegram_tag", formattedTag);
        payload.addProperty("tag", formattedTag);
        payload.addProperty("pin", cleanPin);
        payload.addProperty("minecraft_nickname", cleanNick);
        payload.addProperty("mc_nick", cleanNick);

        HttpRequest request = createRequestBuilder("/api/auth/verify")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        try {
                            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
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
                        return VerifyResult.fail(extractError(response));
                    }
                })
                .exceptionally(ex -> {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    LOGGER.warn("[AITU API] Verification network error: {}", msg);
                    return VerifyResult.fail("Network error: " + (msg != null ? msg : "Connection failed"));
                });
    }

    /**
     * Loads friends list from GET /api/friends/list using Authorization header.
     * Real backend integration with no hardcoded dummy data.
     */
    public CompletableFuture<List<FriendItem>> fetchFriendsList() {
        HttpRequest request = createRequestBuilder("/api/friends/list")
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    List<FriendItem> friends = new ArrayList<>();
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
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
                                        String tag = obj.has("telegram_tag") ? obj.get("telegram_tag").getAsString()
                                                : obj.has("tag") ? obj.get("tag").getAsString() : "@" + nick;
                                        String status = obj.has("status") ? obj.get("status").getAsString() : "Offline";
                                        boolean online = !obj.has("online") || obj.get("online").getAsBoolean();
                                        friends.add(new FriendItem(nick, tag, status, online));
                                    }
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.warn("[AITU API] Failed to parse friends list JSON: {}", e.getMessage());
                        }
                    } else {
                        LOGGER.debug("[AITU API] /api/friends/list returned HTTP {}", response.statusCode());
                    }
                    return friends;
                })
                .exceptionally(ex -> {
                    LOGGER.warn("[AITU API] Friends fetch network error: {}", ex.getMessage());
                    return new ArrayList<>();
                });
    }

    /**
     * Loads pending incoming friend requests from GET /api/friends/requests using Authorization header.
     */
    public CompletableFuture<List<FriendRequestItem>> fetchFriendRequests() {
        HttpRequest request = createRequestBuilder("/api/friends/requests")
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    List<FriendRequestItem> requests = new ArrayList<>();
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        try {
                            JsonElement root = JsonParser.parseString(response.body());
                            JsonArray array = null;
                            if (root.isJsonArray()) {
                                array = root.getAsJsonArray();
                            } else if (root.isJsonObject() && root.getAsJsonObject().has("requests")) {
                                array = root.getAsJsonObject().getAsJsonArray("requests");
                            }

                            if (array != null) {
                                for (JsonElement elem : array) {
                                    if (elem.isJsonObject()) {
                                        JsonObject obj = elem.getAsJsonObject();
                                        String id = obj.has("id") ? obj.get("id").getAsString() : String.valueOf(obj.hashCode());
                                        String fromNick = obj.has("from_nickname") ? obj.get("from_nickname").getAsString()
                                                : obj.has("nickname") ? obj.get("nickname").getAsString()
                                                : obj.has("sender") ? obj.get("sender").getAsString() : "Player";
                                        String fromTag = obj.has("from_tag") ? obj.get("from_tag").getAsString()
                                                : obj.has("telegram_tag") ? obj.get("telegram_tag").getAsString() : "@user";
                                        String time = obj.has("created_at") ? obj.get("created_at").getAsString() : "Just now";
                                        requests.add(new FriendRequestItem(id, fromNick, fromTag, time));
                                    }
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.warn("[AITU API] Failed to parse friend requests JSON: {}", e.getMessage());
                        }
                    }
                    return requests;
                })
                .exceptionally(ex -> {
                    LOGGER.warn("[AITU API] Friend requests network error: {}", ex.getMessage());
                    return new ArrayList<>();
                });
    }

    /**
     * Sends friend request via POST /api/friends/request with {"query": "<input_value>"}.
     */
    public CompletableFuture<ActionResult> sendFriendRequest(String query) {
        JsonObject payload = new JsonObject();
        payload.addProperty("query", query != null ? query.trim() : "");

        HttpRequest request = createRequestBuilder("/api/friends/request")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return ActionResult.ok("Friend request sent!");
                    } else {
                        return ActionResult.fail(extractError(response));
                    }
                })
                .exceptionally(ex -> ActionResult.fail("Network error: " + ex.getMessage()));
    }

    /**
     * Accepts friend request via POST /api/friends/accept with {"request_id": "<id>"}.
     */
    public CompletableFuture<ActionResult> acceptFriendRequest(String requestId) {
        JsonObject payload = new JsonObject();
        payload.addProperty("request_id", requestId);

        HttpRequest request = createRequestBuilder("/api/friends/accept")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return ActionResult.ok("Request accepted!");
                    } else {
                        return ActionResult.fail(extractError(response));
                    }
                })
                .exceptionally(ex -> ActionResult.fail("Network error: " + ex.getMessage()));
    }

    /**
     * Declines friend request via POST /api/friends/decline with {"request_id": "<id>"}.
     */
    public CompletableFuture<ActionResult> declineFriendRequest(String requestId) {
        JsonObject payload = new JsonObject();
        payload.addProperty("request_id", requestId);

        HttpRequest request = createRequestBuilder("/api/friends/decline")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return ActionResult.ok("Request declined.");
                    } else {
                        return ActionResult.fail(extractError(response));
                    }
                })
                .exceptionally(ex -> ActionResult.fail("Network error: " + ex.getMessage()));
    }

    /**
     * Retrieves server information via GET /api/server/info.
     */
    public CompletableFuture<ServerInfoResult> fetchServerInfo() {
        HttpRequest request = createRequestBuilder("/api/server/info")
                .GET()
                .build();

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
                .exceptionally(ex -> ServerInfoResult.fallback("play.aitu-gaming.com:25565", "AITU Official Server", 0, ex.getMessage()));
    }
}
