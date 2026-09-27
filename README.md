# AITU Auth Client (`aitu_auth_client`)

Client-only NeoForge mod for Minecraft **1.21.1** providing authentication and account linking with the **AITU Gaming Hub** Telegram bot.

---

## 📌 Architecture & Features

### 1. Persistence & Session Management
- **File Location:** `.minecraft/config/aitu_session.json`
- **JSON Schema:**
  ```json
  {
    "session_token": "string",
    "cached_nickname": "string",
    "telegram_id": 123456789
  }
  ```
- **Singleton `SessionManager` (`kz.aitu.auth.config.SessionManager`):**
  - Thread-safe singleton providing `hasValidSession()`, `getSession()`, `getSessionToken()`, `saveSession(...)`, and `clearSession()`.
  - Atomic file writing via temporary files to avoid corrupted states.
  - Robust error handling: gracefully catches `JsonSyntaxException`, `IOException`, empty files, and partial JSON structures without crashing the game.
  - Smart input parsing: supports raw token strings, `<telegram_id>:<token>` delimited strings, and direct JSON payloads.

---

### 2. UI Injection & Account Linking Screen
- **Mixin on `TitleScreen` (`kz.aitu.auth.mixin.TitleScreenMixin`):**
  - Injects at the end of `TitleScreen#init()`.
  - If `aitu_session.json` is missing or invalid:
    - Renders a warning button in the top-right corner: `⚠ Link AITU Account`.
    - Clicking it opens `AituAuthScreen`.
  - If a valid session is present:
    - Renders a subtle `✔ AITU Linked` badge button allowing players to inspect their linked Telegram ID or unlink/switch accounts.
- **`AituAuthScreen` (`kz.aitu.auth.gui.AituAuthScreen`):**
  - **Copy Telegram Bot Link:** Copies `https://t.me/aitu_gaming_bot?start=link` to the system clipboard and provides instant visual feedback.
  - **Open in Browser:** Directly opens the Telegram bot link in the user's default browser.
  - **Token Input Box:** Accepts one-time linking tokens or session JSON payloads.
  - **Telegram ID Input:** Optional field for manual Telegram ID entry if entering raw tokens.
  - **Submit & Refresh:** Validates format, writes to `.minecraft/config/aitu_session.json`, and returns to the parent `TitleScreen`, automatically refreshing the UI state.

---

### 3. Network Configuration Handshake
- Registered during `RegisterPayloadHandlersEvent` on the `MOD` event bus using `IPayloadRegistrar` / `PayloadRegistrar`:
  - **Client-to-Server Payload (`aitu_auth:token_payload`):**
    - `AuthTokenPayload(String sessionToken)` implementing `CustomPacketPayload` using `StreamCodec` (`ByteBufCodecs.STRING_UTF8`).
    - Registered via `registrar.configurationToServer(...)`.
  - **Server-to-Client Configuration Task Trigger Payload (`aitu_auth:challenge_payload`):**
    - `ChallengePayload(String challenge)` implementing `CustomPacketPayload`.
    - Resilient `StreamCodec` supporting both empty (0-byte trigger) packets and challenge string packets.
    - Registered via `registrar.configurationToClient(..., ClientPayloadHandler::handleChallenge)`.
- **Handshake Flow:**
  1. During the server configuration phase, server triggers authentication with `aitu_auth:challenge_payload`.
  2. Client receives `ChallengePayload` and queries `SessionManager.getInstance().hasValidSession()`.
  3. If a valid token exists, client automatically responds with `AuthTokenPayload(sessionToken)` via `context.reply(...)`.
  4. If no token exists or the session is invalid, the client gracefully disconnects with an informative message instructing the user to link their account in the title screen.

---

## 🛠️ Building & Running

### Prerequisites
- JDK 21 (Java 21)

### Build the Mod JAR
```bash
./gradlew build
```
The output mod JAR will be located at:
```
build/libs/aitu_auth_client-1.0.0.jar
```

### Run Client in Development Environment
```bash
./gradlew runClient
```

### Run Unit Tests
```bash
./gradlew test
```
