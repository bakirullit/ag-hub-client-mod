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
    public void testSaveAndLoadSession() throws IOException {
        sessionManager.saveSession("test_token_abc_123", "Steve", 123456789L);

        assertTrue(Files.exists(configPath));
        assertTrue(sessionManager.hasValidSession());
        assertEquals("test_token_abc_123", sessionManager.getSessionToken());

        SessionData data = sessionManager.getSession().orElseThrow();
        assertEquals("test_token_abc_123", data.getSessionToken());
        assertEquals("Steve", data.getCachedNickname());
        assertEquals(123456789L, data.getTelegramId());

        String jsonContent = Files.readString(configPath);
        assertTrue(jsonContent.contains("\"session_token\": \"test_token_abc_123\""));
        assertTrue(jsonContent.contains("\"cached_nickname\": \"Steve\""));
        assertTrue(jsonContent.contains("\"telegram_id\": 123456789"));
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
        sessionManager.saveSession("to_be_deleted", "Alex", 999999L);
        assertTrue(Files.exists(configPath));

        sessionManager.clearSession();
        assertFalse(Files.exists(configPath));
        assertFalse(sessionManager.hasValidSession());
    }

    @Test
    public void testParseInputToken() {
        // 1. JSON format
        String json = "{\"session_token\": \"jwt_token_999\", \"cached_nickname\": \"Gamer\", \"telegram_id\": 777}";
        SessionData parsedJson = sessionManager.parseInputToken(json, "Fallback", 0);
        assertNotNull(parsedJson);
        assertEquals("jwt_token_999", parsedJson.getSessionToken());
        assertEquals("Gamer", parsedJson.getCachedNickname());
        assertEquals(777, parsedJson.getTelegramId());
        assertTrue(parsedJson.isValid());

        // 2. Delimited format <telegram_id>:<token>
        String delimited = "555123:bot_token_xyz";
        SessionData parsedDelimited = sessionManager.parseInputToken(delimited, "Player1", 0);
        assertNotNull(parsedDelimited);
        assertEquals("bot_token_xyz", parsedDelimited.getSessionToken());
        assertEquals("Player1", parsedDelimited.getCachedNickname());
        assertEquals(555123, parsedDelimited.getTelegramId());
        assertTrue(parsedDelimited.isValid());

        // 3. Plain token format with fallback telegram id
        String plain = "plain_token_abc";
        SessionData parsedPlain = sessionManager.parseInputToken(plain, "Player2", 888123);
        assertNotNull(parsedPlain);
        assertEquals("plain_token_abc", parsedPlain.getSessionToken());
        assertEquals("Player2", parsedPlain.getCachedNickname());
        assertEquals(888123, parsedPlain.getTelegramId());
        assertTrue(parsedPlain.isValid());

        // 4. Invalid / empty inputs
        assertNull(sessionManager.parseInputToken("", "Player", 0));
        assertNull(sessionManager.parseInputToken("   ", "Player", 0));
        assertNull(sessionManager.parseInputToken(null, "Player", 0));
    }

    @Test
    public void testSessionDataValidation() {
        SessionData valid = new SessionData("token", "nick", 12345);
        assertTrue(valid.isValid());

        SessionData nullToken = new SessionData(null, "nick", 12345);
        assertFalse(nullToken.isValid());

        SessionData emptyToken = new SessionData("  ", "nick", 12345);
        assertFalse(emptyToken.isValid());

        SessionData nullNick = new SessionData("token", null, 12345);
        assertFalse(nullNick.isValid());

        SessionData emptyNick = new SessionData("token", " ", 12345);
        assertFalse(emptyNick.isValid());

        SessionData zeroTgId = new SessionData("token", "nick", 0);
        assertFalse(zeroTgId.isValid());
    }
}
