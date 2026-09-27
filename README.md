# AITU Auth Client (`aitu_auth_client`)

Client-only NeoForge mod for Minecraft **1.21.1** providing authentication, profile management, and account linking with the **AITU Gaming Hub** Telegram bot.

---

## 📌 Architecture & Features

### 1. Persistence & Session Management
- **File Location:** `.minecraft/config/aitu_session.json`
- **JSON Schema:**
  ```json
  {
    "session_token": "string",
    "cached_nickname": "string",
    "telegram_id": 123456789,
    "telegram_tag": "@username"
  }
  ```
- **Singleton `SessionManager` (`kz.aitu.auth.config.SessionManager`):**
  - Thread-safe singleton providing `hasValidSession()`, `getSession()`, `getSessionToken()`, `saveSession(...)`, and `clearSession()`.
  - Atomic file writing via temporary files to avoid corrupted states.
  - Robust error handling: gracefully catches `JsonSyntaxException`, `IOException`, empty files, and partial JSON structures without crashing the game.
  - Smart input parsing: supports raw token strings, `<telegram_id>:<token>` delimited strings, and direct JSON payloads with automatic `@` tag formatting.

---

### 2. UI Architecture

#### Top-Right Profile Widget (`TitleScreenMixin` & `AituProfileWidget`)
- Replaces generic text buttons with a sleek, compact profile badge in the top-right corner of the Minecraft Title Screen:
  - **Unlinked State:** Displays a guest profile silhouette with a subtle amber warning dot, "Sign In" label, and informative tooltip.
  - **Linked State:** Displays the player's 3D skin face avatar via `PlayerFaceRenderer`, cached `@tag`, and a vibrant green active indicator dot.
- Clicking the widget opens **`AituHubScreen`**.

#### Tabbed Modular Container (`AituHubScreen`)
- Responsive modular container with a top Tab Navigation bar and sub-panel rendering:
  - **Tab 1: Profile & Authentication (Active by default)**
    - *When Unlinked:*
      - Direct "Get Code from @aitu_gaming_bot" button (copies link & opens browser).
      - Field 1: Telegram Tag / Username (`@username`).
      - Field 2: 6-digit Code (strictly numeric, max 6 characters).
      - "Verify & Link" button to finalize login and immediately update UI state.
    - *When Linked:*
      - Rich User Card displaying player face avatar, Nickname, Telegram `@tag`, Telegram ID, and active status pill.
      - "Log Out / Unlink" button that clears local session data and resets to the sign-in form.
  - **Tab 2: Friends (`FriendsTab`)**
    - Foundation stub announcing upcoming university social, party, and invite features.
  - **Tab 3: Settings (`SettingsTab`)**
    - Client diagnostics, version info, session disk reload button, and direct Telegram community links.
- Uses dynamic layout calculations (`width / 2`, relative Y offsets) to prevent widget overlapping across all GUI scales.

---

### 3. Network Configuration Handshake
- Registered during `RegisterPayloadHandlersEvent` on the `MOD` event bus using `PayloadRegistrar`:
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
  4. If no token exists or the session is invalid, the client gracefully disconnects with an informative message instructing the user to sign in on the title screen.

---

## 🛠️ Building & Testing

### Prerequisites
- JDK 21 (Java 21)

### Build the Mod JAR
```bash
./gradlew build
```
Output JAR: `build/libs/aitu_auth_client-1.1.0.jar`

### Run Unit Tests
```bash
./gradlew test
```
