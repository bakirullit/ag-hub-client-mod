package kz.aitu.auth.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AituServerManagerTest {

    @Test
    public void testDefaults() {
        assertEquals("play.aitu-gaming.com:25565", AituServerManager.DEFAULT_IP);
        assertEquals("AITU Official Server", AituServerManager.DEFAULT_NAME);
        assertEquals("play.aitu-gaming.com:25565", AituServerManager.getServerIp());
        assertEquals("AITU Official Server", AituServerManager.getServerName());
    }

    @Test
    public void testIsAituServerDetection() {
        assertTrue(AituServerManager.isAituServer("AITU Official Server", "play.aitu-gaming.com:25565"));
        assertTrue(AituServerManager.isAituServer("My AITU SMP", "play.aitu-gaming.com:25565"));
        assertTrue(AituServerManager.isAituServer("SMP", "smp.aitu-gaming.com:25565"));
        assertTrue(AituServerManager.isAituServer("AITU Gaming Hub", "127.0.0.1:25565"));

        assertFalse(AituServerManager.isAituServer("Hypixel", "mc.hypixel.net"));
        assertFalse(AituServerManager.isAituServer(null, null));
    }
}
