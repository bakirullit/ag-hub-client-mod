package kz.aitu.auth.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AituApiClientTest {

    @Test
    public void testBaseUrlAndInstance() {
        assertNotNull(AituApiClient.getInstance());
        assertEquals("https://aitu-gaming.y-not-devs.com", AituApiClient.BASE_URL);
    }

    @Test
    public void testDefaultDemoFriends() {
        List<AituApiClient.FriendEntry> friends = AituApiClient.getDefaultDemoFriends();
        assertNotNull(friends);
        assertFalse(friends.isEmpty());

        boolean hasSmp = friends.stream().anyMatch(f -> f.status().contains("AITU SMP"));
        boolean hasIdle = friends.stream().anyMatch(f -> f.status().contains("Idle"));

        assertTrue(hasSmp, "Expected at least one friend with 'Playing on AITU SMP'");
        assertTrue(hasIdle, "Expected at least one friend with 'Idle'");
    }

    @Test
    public void testVerifyResultRecord() {
        AituApiClient.VerifyResult ok = AituApiClient.VerifyResult.ok("token_123", 999L, "@user");
        assertTrue(ok.success());
        assertEquals("token_123", ok.sessionToken());
        assertEquals(999L, ok.telegramId());
        assertEquals("@user", ok.telegramTag());
        assertNull(ok.error());

        AituApiClient.VerifyResult fail = AituApiClient.VerifyResult.fail("Invalid PIN");
        assertFalse(fail.success());
        assertNull(fail.sessionToken());
        assertEquals("Invalid PIN", fail.error());
    }

    @Test
    public void testServerInfoResultRecord() {
        AituApiClient.ServerInfoResult ok = AituApiClient.ServerInfoResult.ok("play.test.com", "Official", 25);
        assertTrue(ok.success());
        assertEquals("play.test.com", ok.ip());
        assertEquals(25, ok.online());

        AituApiClient.ServerInfoResult fallback = AituApiClient.ServerInfoResult.fallback("default.ip", "Default", 0, "offline");
        assertFalse(fallback.success());
        assertEquals("default.ip", fallback.ip());
    }
}
