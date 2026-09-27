package kz.aitu.auth.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AituApiClientTest {

    @Test
    public void testBaseUrlAndInstance() {
        assertNotNull(AituApiClient.getInstance());
        assertEquals("https://aitu-gaming.y-not-devs.com", AituApiClient.BASE_URL);
    }

    @Test
    public void testRequestCodeResultRecord() {
        AituApiClient.RequestCodeResult ok = AituApiClient.RequestCodeResult.ok("Code sent");
        assertTrue(ok.success());
        assertEquals("Code sent", ok.message());
        assertNull(ok.error());

        AituApiClient.RequestCodeResult fail = AituApiClient.RequestCodeResult.fail("User not found");
        assertFalse(fail.success());
        assertNull(fail.message());
        assertEquals("User not found", fail.error());
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
    public void testFriendItemRecordAndStatus() {
        AituApiClient.FriendItem smpFriend = new AituApiClient.FriendItem("Alikhan", "@alikhan", "Playing on AITU SMP", true);
        assertTrue(smpFriend.isOnline());
        assertTrue(smpFriend.isPlayingSmp());
        assertEquals("Alikhan", smpFriend.nickname());

        AituApiClient.FriendItem onlineFriend = new AituApiClient.FriendItem("Dias", "@dias", "Online", true);
        assertTrue(onlineFriend.isOnline());
        assertFalse(onlineFriend.isPlayingSmp());

        AituApiClient.FriendItem offlineFriend = new AituApiClient.FriendItem("Aruzhan", "@aruzhan", "Offline", false);
        assertFalse(offlineFriend.isOnline());
        assertFalse(offlineFriend.isPlayingSmp());
    }

    @Test
    public void testFriendRequestItemRecord() {
        AituApiClient.FriendRequestItem req = new AituApiClient.FriendRequestItem("req_1", "Temirlan", "@temirlan", "2 mins ago");
        assertEquals("req_1", req.id());
        assertEquals("Temirlan", req.fromNickname());
        assertEquals("@temirlan", req.fromTag());
        assertEquals("2 mins ago", req.timestamp());
    }

    @Test
    public void testActionResultRecord() {
        AituApiClient.ActionResult ok = AituApiClient.ActionResult.ok("Accepted");
        assertTrue(ok.success());
        assertEquals("Accepted", ok.message());
        assertNull(ok.error());

        AituApiClient.ActionResult fail = AituApiClient.ActionResult.fail("Declined");
        assertFalse(fail.success());
        assertEquals("Declined", fail.error());
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
