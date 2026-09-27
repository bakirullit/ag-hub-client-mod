package kz.aitu.auth.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class SessionManagerTest {

    private SessionManager sessionManager;
    private Path configPath;

    @BeforeEach
    public void setUp() {
        sessionManager = SessionManager.getInstance();
        configPath = sessionManager.getConfigPath();
        sessionManager.clearSession();
    }

    @AfterEach
    public void tearDown() {
        sessionManager.clearSession();
    }

    @Test
    public void testMissingFileHandling() {
        assertFalse(Files.exists(configPath));
        assertFalse(sessionManager.hasValidSession());
        assertNull(sessionManager.getSessionToken());
        assertTrue(sessionManager.getSession().isEmpty());
    }

    @Test
    public void testSaveAndLoadSessionWithTelegramTag() throws IOException {
        sessionManager.saveSession("test_token_abc_123", "Steve", 123456789L, "@steve_tg");

        assertTrue(Files.exists(configPath));
        assertTrue(sessionManager.hasValidSession());
        assertEquals("test_token_abc_123", sessionManager.getSessionToken());

        SessionData data = sessionManager.getSession().orElseThrow();
        assertEquals("test_token_abc_123", data.getSessionToken());
        assertEquals("Steve", data.getCachedNickname());
        assertEquals(123456789L, data.getTelegramId());
        assertEquals("@steve_tg", data.getTelegramTag());

        String jsonContent = Files.readString(configPath);
        assertTrue(jsonContent.contains("\"session_token\": \"test_token_abc_123\""));
        assertTrue(jsonContent.contains("\"cached_nickname\": \"Steve\""));
        assertTrue(jsonContent.contains("\"telegram_id\": 123456789"));
        assertTrue(jsonContent.contains("\"telegram_tag\": \"@steve_tg\""));
    }

    @Test
    public void testTagFormattingWithoutAtSymbol() throws IOException {
        // Tag without leading @ should automatically get formatted with @
        sessionManager.saveSession("token_xyz", "Alex", 987654L, "alex_tg");
        SessionData data = sessionManager.getSession().orElseThrow();
        assertEquals("@alex_tg", data.getTelegramTag());
    }

    @Test
    public void testMalformedJsonHandling() throws IOException {
        Path parent = configPath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        Files.writeString(configPath, "{ this is not valid json! ");

        assertFalse(sessionManager.loadSession());
        assertFalse(sessionManager.hasValidSession());
        assertNull(sessionManager.getSessionToken());
    }

    @Test
    public void testEmptyJsonFileHandling() throws IOException {
        Path parent = configPath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        Files.writeString(configPath, "   \n  ");

        assertFalse(sessionManager.loadSession());
        assertFalse(sessionManager.hasValidSession());
    }

    @Test
    public void testIncompleteJsonHandling() throws IOException {
        Path parent = configPath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        // Missing token and telegram id
        Files.writeString(configPath, "{\"cached_nickname\": \"Alex\"}");

        assertFalse(sessionManager.loadSession());
        assertFalse(sessionManager.hasValidSession());
    }

    @Test
    public void testClearSession() throws IOException {
        sessionManager.saveSession("to_be_deleted", "Alex", 999999L, "@alex");
        assertTrue(Files.exists(configPath));

        sessionManager.clearSession();
        assertFalse(Files.exists(configPath));
        assertFalse(sessionManager.hasValidSession());
    }

    @Test
    public void testParseInputTokenWithTag() {
        // 1. JSON format with telegram_tag
        String json = "{\"session_token\": \"jwt_token_999\", \"cached_nickname\": \"Gamer\", \"telegram_id\": 777, \"telegram_tag\": \"@pro_gamer\"}";
        SessionData parsedJson = sessionManager.parseInputToken(json, "Fallback", 0, "@fallback");
        assertNotNull(parsedJson);
        assertEquals("jwt_token_999", parsedJson.getSessionToken());
        assertEquals("Gamer", parsedJson.getCachedNickname());
        assertEquals(777, parsedJson.getTelegramId());
        assertEquals("@pro_gamer", parsedJson.getTelegramTag());
        assertTrue(parsedJson.isValid());

        // 2. Delimited format <telegram_id>:<token>
        String delimited = "555123:bot_token_xyz";
        SessionData parsedDelimited = sessionManager.parseInputToken(delimited, "Player1", 0, "@player1");
        assertNotNull(parsedDelimited);
        assertEquals("bot_token_xyz", parsedDelimited.getSessionToken());
        assertEquals("Player1", parsedDelimited.getCachedNickname());
        assertEquals(555123, parsedDelimited.getTelegramId());
        assertEquals("@player1", parsedDelimited.getTelegramTag());
        assertTrue(parsedDelimited.isValid());

        // 3. Plain token format with fallback telegram id and tag
        String plain = "plain_token_abc";
        SessionData parsedPlain = sessionManager.parseInputToken(plain, "Player2", 888123, "player2_tag");
        assertNotNull(parsedPlain);
        assertEquals("plain_token_abc", parsedPlain.getSessionToken());
        assertEquals("Player2", parsedPlain.getCachedNickname());
        assertEquals(888123, parsedPlain.getTelegramId());
        assertEquals("@player2_tag", parsedPlain.getTelegramTag());
        assertTrue(parsedPlain.isValid());

        // 4. Invalid / empty inputs
        assertNull(sessionManager.parseInputToken("", "Player", 0));
        assertNull(sessionManager.parseInputToken("   ", "Player", 0));
        assertNull(sessionManager.parseInputToken(null, "Player", 0));
    }

    @Test
    public void testSessionDataValidation() {
        SessionData valid = new SessionData("token", "nick", 12345, "@user");
        assertTrue(valid.isValid());
        assertEquals("@user", valid.getTelegramTag());

        SessionData nullToken = new SessionData(null, "nick", 12345, "@user");
        assertFalse(nullToken.isValid());

        SessionData emptyToken = new SessionData("  ", "nick", 12345, "@user");
        assertFalse(emptyToken.isValid());

        SessionData nullNick = new SessionData("token", null, 12345, "@user");
        assertFalse(nullNick.isValid());

        SessionData emptyNick = new SessionData("token", " ", 12345, "@user");
        assertFalse(emptyNick.isValid());

        SessionData zeroTgId = new SessionData("token", "nick", 0, "@user");
        assertFalse(zeroTgId.isValid());
    }
}
