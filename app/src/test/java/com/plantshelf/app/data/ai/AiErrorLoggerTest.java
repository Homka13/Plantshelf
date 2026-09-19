package com.plantshelf.app.data.ai;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class AiErrorLoggerTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        AiErrorLogger.clearLogs(context);
    }

    @After
    public void tearDown() {
        AiErrorLogger.clearLogs(context);
    }

    @Test
    public void testLogAndReadBack() {
        AiErrorLogger.log(context, "TestGeminiCall", new IllegalArgumentException("Invalid key"), "model=gemini-flash");
        String logs = AiErrorLogger.getRecentLogs(context);

        assertNotNull(logs);
        assertTrue(logs.contains("TestGeminiCall"));
        assertTrue(logs.contains("Invalid key"));
        assertTrue(logs.contains("model=gemini-flash"));
    }

    @Test
    public void testCleanJsonContent() {
        String markdownJson = "```json\n{\"name\": \"Монстера\", \"lux\": 12000}\n```";
        String cleaned = GeminiPlantAiService.cleanJsonContent(markdownJson);
        assertEquals("{\"name\": \"Монстера\", \"lux\": 12000}", cleaned);

        String plainJson = "{\"name\": \"Фікус\"}";
        assertEquals(plainJson, GeminiPlantAiService.cleanJsonContent(plainJson));
    }

    private void assertNotNull(Object obj) {
        assertTrue(obj != null);
    }
}
