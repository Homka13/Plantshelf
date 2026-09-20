package com.plantshelf.app.data.ai;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
        assertTrue(AiErrorLogger.hasLogs(context));
    }

    @Test
    public void testClearLogs() {
        AiErrorLogger.log(context, "TestAction", new RuntimeException("Something failed"), "param=123");
        assertTrue(AiErrorLogger.hasLogs(context));

        AiErrorLogger.clearLogs(context);
        assertFalse(AiErrorLogger.hasLogs(context));
        assertEquals("", AiErrorLogger.getRecentLogs(context));
    }

    @Test
    public void testLogHttpError() {
        AiErrorLogger.logHttpError(context, "GeminiRequest", "gemini-3.8-flash", 429,
                "{\"error\": {\"code\": 429, \"message\": \"RESOURCE_EXHAUSTED\"}}",
                new RuntimeException("HTTP 429"));

        String logs = AiErrorLogger.getRecentLogs(context);
        assertTrue(logs.contains("GeminiRequest"));
        assertTrue(logs.contains("model=gemini-3.8-flash"));
        assertTrue(logs.contains("httpCode=429"));
        assertTrue(logs.contains("RESOURCE_EXHAUSTED"));
    }

    @Test
    public void testUserFriendlyMessages_Network() {
        String msg1 = AiErrorLogger.getUserFriendlyMessage(new UnknownHostException("generativelanguage.googleapis.com"));
        assertTrue("Message should mention connection in Ukrainian: " + msg1, msg1.contains("зв'язок") || msg1.contains("інтернет"));

        String msg2 = AiErrorLogger.getUserFriendlyMessage(new SocketTimeoutException("Read timed out"));
        assertTrue("Message should mention timeout or connection: " + msg2, msg2.contains("зв'язок") || msg2.contains("очікування"));

        String msg3 = AiErrorLogger.getUserFriendlyMessage(new ConnectException("Connection refused"));
        assertTrue(msg3.contains("зв'язок") || msg3.contains("інтернет"));
    }

    @Test
    public void testUserFriendlyMessages_ApiKeys() {
        String msg1 = AiErrorLogger.getUserFriendlyMessage(new RuntimeException("API-ключ Gemini не встановлено"));
        assertTrue(msg1.contains("API-ключ") && (msg1.contains("налаштуваннях") || msg1.contains("налаштовано")));

        String msg2 = AiErrorLogger.getUserFriendlyMessage(new RuntimeException("HTTP 403: API key not valid"));
        assertTrue(msg2.contains("API-ключ") && msg2.contains("Недійсний"));
    }

    @Test
    public void testUserFriendlyMessages_ModelNotFound() {
        String msg = AiErrorLogger.getUserFriendlyMessage(new RuntimeException("Модель 'gemini-old' недоступна (HTTP 404)"));
        assertTrue(msg.contains("модель") && (msg.contains("недоступна") || msg.contains("застаріла")));
    }

    @Test
    public void testUserFriendlyMessages_RateLimit() {
        String msg = AiErrorLogger.getUserFriendlyMessage(new RuntimeException("RESOURCE_EXHAUSTED: HTTP 429"));
        assertTrue(msg.contains("квот") || msg.contains("ліміт"));
    }

    @Test
    public void testUserFriendlyMessages_Safety() {
        String msg = AiErrorLogger.getUserFriendlyMessage(new IllegalStateException("Запит або фото заблоковано фільтрами безпеки Gemini (SAFETY)"));
        assertTrue(msg.contains("безпеки") || msg.contains("заблоковано"));
    }

    @Test
    public void testCleanJsonContent() {
        String markdownJson = "```json\n{\"name\": \"Монстера\", \"lux\": 12000}\n```";
        String cleaned = GeminiPlantAiService.cleanJsonContent(markdownJson);
        assertEquals("{\"name\": \"Монстера\", \"lux\": 12000}", cleaned);

        String plainJson = "{\"name\": \"Фікус\"}";
        assertEquals(plainJson, GeminiPlantAiService.cleanJsonContent(plainJson));
    }
}
