package kz.aitu.auth.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class AituClientConfigTest {

    private AituClientConfig config;
    private Path configPath;

    @BeforeEach
    public void setUp() throws IOException {
        config = AituClientConfig.getInstance();
        configPath = config.getConfigPath();
        if (Files.exists(configPath)) {
            Files.delete(configPath);
        }
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (Files.exists(configPath)) {
            Files.delete(configPath);
        }
    }

    @Test
    public void testDefaultsAndToggles() {
        assertFalse(config.isAutoJoinLaunch());
        assertTrue(config.isShowServerNotifications());

        config.setAutoJoinLaunch(true);
        assertTrue(config.isAutoJoinLaunch());
        assertTrue(Files.exists(configPath));

        config.setShowServerNotifications(false);
        assertFalse(config.isShowServerNotifications());

        // Re-read from disk
        config.load();
        assertTrue(config.isAutoJoinLaunch());
        assertFalse(config.isShowServerNotifications());
    }
}
