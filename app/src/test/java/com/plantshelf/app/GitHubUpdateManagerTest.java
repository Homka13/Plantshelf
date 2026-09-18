package com.plantshelf.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.plantshelf.app.updater.GitHubUpdateManager;

import org.junit.Test;

public class GitHubUpdateManagerTest {

    @Test
    public void testVersionComparison() {
        // Newer versions
        assertTrue(GitHubUpdateManager.isNewerVersion("v1.0.1", "1.0.0"));
        assertTrue(GitHubUpdateManager.isNewerVersion("v1.1.0", "1.0.5"));
        assertTrue(GitHubUpdateManager.isNewerVersion("v2.0.0", "1.9.9"));
        assertTrue(GitHubUpdateManager.isNewerVersion("1.0.1", "1.0.0"));
        assertTrue(GitHubUpdateManager.isNewerVersion("v1.0.10", "1.0.9"));

        // Same version
        assertFalse(GitHubUpdateManager.isNewerVersion("v1.0.0", "1.0.0"));
        assertFalse(GitHubUpdateManager.isNewerVersion("1.0.0", "1.0.0"));
        assertFalse(GitHubUpdateManager.isNewerVersion("v1.0.0", "v1.0.0"));

        // Older version
        assertFalse(GitHubUpdateManager.isNewerVersion("v0.9.9", "1.0.0"));
        assertFalse(GitHubUpdateManager.isNewerVersion("1.0.0", "1.0.1"));
        assertFalse(GitHubUpdateManager.isNewerVersion("v1.0.0", "1.1.0"));

        // Null/empty
        assertFalse(GitHubUpdateManager.isNewerVersion(null, "1.0.0"));
        assertFalse(GitHubUpdateManager.isNewerVersion("v1.0.1", null));
        assertFalse(GitHubUpdateManager.isNewerVersion("", ""));
    }
}
