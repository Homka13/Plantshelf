package com.plantshelf.app.data.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Pure Java service communicating directly with Google Gemini Flash / Pro API
 * to identify houseplants by photo or name, and extract structured care requirements.
 */
public class GeminiPlantAiService {

    public static class GeminiModelInfo {
        private final String modelId;
        private final String displayName;

        public GeminiModelInfo(String modelId, String displayName) {
            this.modelId = modelId;
            this.displayName = displayName;
        }

        public String getModelId() {
            return modelId;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName + " (" + modelId + ")";
        }
    }

    private static final String TAG = "GeminiPlantAiService";
    private static final String PREFS_NAME = "plantshelf_ai_prefs";
    private static final String KEY_GEMINI_API_KEY = "gemini_api_key";
    public static final String KEY_GEMINI_MODEL = "gemini_model";
    public static final String DEFAULT_MODEL = "gemini-3.8-flash";

    public static final List<GeminiModelInfo> AVAILABLE_MODELS = Arrays.asList(
            new GeminiModelInfo("gemini-3.8-flash", "Gemini 3.8 Flash (Нова стабільна)"),
            new GeminiModelInfo("gemini-3.7-flash", "Gemini 3.7 Flash"),
            new GeminiModelInfo("gemini-3.6-flash", "Gemini 3.6 Flash"),
            new GeminiModelInfo("gemini-3.5-flash", "Gemini 3.5 Flash"),
            new GeminiModelInfo("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite"),
            new GeminiModelInfo("gemini-3.1-flash-lite", "Gemini 3.1 Flash-Lite"),
            new GeminiModelInfo("gemini-3.1-pro-preview", "Gemini 3.1 Pro (Preview)"),
            new GeminiModelInfo("gemini-3-flash-preview", "Gemini 3 Flash (Preview)"),
            new GeminiModelInfo("gemini-2.5-flash", "Gemini 2.5 Flash"),
            new GeminiModelInfo("gemini-2.5-flash-lite", "Gemini 2.5 Flash-Lite"),
            new GeminiModelInfo("gemini-2.5-pro", "Gemini 2.5 Pro"),
            new GeminiModelInfo("gemini-flash-latest", "Gemini Flash Latest (Автооновлення)")
    );

    public interface AiAnalysisCallback {
        void onSuccess(AiPlantAnalysisResult result);
        void onError(Exception e);
    }

    public static String getSavedApiKey(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_GEMINI_API_KEY, "");
    }

    public static String getSavedModel(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_GEMINI_MODEL, DEFAULT_MODEL);
    }

    public static void saveApiKey(Context context, String apiKey) {
        saveAiSettings(context, apiKey, getSavedModel(context));
    }

    public static void saveAiSettings(Context context, String apiKey, String model) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_GEMINI_API_KEY, apiKey != null ? apiKey.trim() : "")
                .putString(KEY_GEMINI_MODEL, model != null && !model.trim().isEmpty() ? model.trim() : DEFAULT_MODEL)
                .apply();
    }

    /**
     * Identify plant using a camera/gallery photo.
     */
    public static void identifyPlantByPhoto(
            Context context,
            File imageFile,
            AiAnalysisCallback callback
    ) {
        String apiKey = getSavedApiKey(context);
        if (apiKey.isEmpty()) {
            callback.onError(new IllegalStateException("API ключ не встановлено"));
            return;
        }

        new Thread(() -> {
            try {
                String base64Image = prepareBase64Image(imageFile);
                if (base64Image == null) {
                    throw new IllegalArgumentException("Не вдалося завантажити фото для аналізу");
                }

                String promptText = "Ти експерт-ботанік з кімнатних рослин. "
                        + "Визнач кімнатну рослину на цьому фото. "
                        + "Поверни відповідь ВИКЛЮЧНО валідним JSON без зайвого тексту у такій схемі:\n"
                        + getJsonSchemaPrompt();

                JsonObject payload = new JsonObject();
                JsonArray contentsArray = new JsonArray();
                JsonObject contentObj = new JsonObject();
                JsonArray partsArray = new JsonArray();

                // Text part
                JsonObject textPart = new JsonObject();
                textPart.addProperty("text", promptText);
                partsArray.add(textPart);

                // Image part
                JsonObject imagePart = new JsonObject();
                JsonObject inlineData = new JsonObject();
                inlineData.addProperty("mime_type", "image/jpeg");
                inlineData.addProperty("data", base64Image);
                imagePart.add("inline_data", inlineData);
                partsArray.add(imagePart);

                contentObj.add("parts", partsArray);
                contentsArray.add(contentObj);
                payload.add("contents", contentsArray);

                sendGeminiRequest(context, apiKey, payload, callback);
            } catch (Exception e) {
                Log.e(TAG, "Error in identifyPlantByPhoto", e);
                callback.onError(e);
            }
        }).start();
    }

    /**
     * Generate plant care profile simply by plant name (e.g. "Філодендрон Біркін").
     */
    public static void generatePlantByName(
            Context context,
            String plantName,
            AiAnalysisCallback callback
    ) {
        String apiKey = getSavedApiKey(context);
        if (apiKey.isEmpty()) {
            callback.onError(new IllegalStateException("API ключ не встановлено"));
            return;
        }

        new Thread(() -> {
            try {
                String promptText = "Ти експерт-ботанік з кімнатних рослин. "
                        + "Склади точний та вичерпний паспорт догляду для кімнатної рослини: '" + plantName + "'. "
                        + "Поверни відповідь ВИКЛЮЧНО валідним JSON без зайвого тексту у такій схемі:\n"
                        + getJsonSchemaPrompt();

                JsonObject payload = new JsonObject();
                JsonArray contentsArray = new JsonArray();
                JsonObject contentObj = new JsonObject();
                JsonArray partsArray = new JsonArray();

                JsonObject textPart = new JsonObject();
                textPart.addProperty("text", promptText);
                partsArray.add(textPart);

                contentObj.add("parts", partsArray);
                contentsArray.add(contentObj);
                payload.add("contents", contentsArray);

                sendGeminiRequest(context, apiKey, payload, callback);
            } catch (Exception e) {
                Log.e(TAG, "Error in generatePlantByName", e);
                callback.onError(e);
            }
        }).start();
    }

    private static String getJsonSchemaPrompt() {
        return "{\n"
                + "  \"name\": \"Назва українською (наприклад, Фікус каучуконосний)\",\n"
                + "  \"latin\": \"Латинська ботанічна назва\",\n"
                + "  \"variety\": \"Сорт або різновид якщо відомо\",\n"
                + "  \"difficulty\": \"дуже легка / легка / середня / складна\",\n"
                + "  \"light\": \"яскраве розсіяне / півтінь / пряме сонце\",\n"
                + "  \"lux\": 12000,\n"
                + "  \"intervalDaysSummer\": 7,\n"
                + "  \"intervalDaysWinter\": 14,\n"
                + "  \"fertIntervalDays\": 14,\n"
                + "  \"humidity\": \"низька / середня / висока\",\n"
                + "  \"soil\": \"рекомендований склад субстрату\",\n"
                + "  \"warning\": \"попередження якщо отруйна для котів/собак\",\n"
                + "  \"notes\": \"практичні поради щодо поливу та догляду\"\n"
                + "}";
    }

    private static void sendGeminiRequest(Context context, String apiKey, JsonObject payload, AiAnalysisCallback callback) {
        HttpURLConnection conn = null;
        try {
            String model = getSavedModel(context);
            String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";
            URL url = new URL(endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("x-goog-api-key", apiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int code = conn.getResponseCode();
            InputStream stream = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            if (code == 404) {
                throw new RuntimeException("Модель '" + model + "' недоступна (HTTP 404). Оберіть іншу модель у налаштуваннях AI (наприклад, gemini-flash-latest або gemini-3.5-flash).");
            } else if (code == 400 || code == 403) {
                throw new RuntimeException("Помилка авторизації Gemini (HTTP " + code + "): перевірте свій API-ключ або вибрану модель.");
            } else if (code != 200) {
                throw new RuntimeException("Помилка сервера Gemini (" + code + "): " + response.toString());
            }

            JsonObject geminiResp = JsonParser.parseString(response.toString()).getAsJsonObject();
            String rawText = extractTextFromGeminiResponse(geminiResp);
            String cleanJson = cleanJsonContent(rawText);
            AiPlantAnalysisResult result = new Gson().fromJson(cleanJson, AiPlantAnalysisResult.class);

            callback.onSuccess(result);
        } catch (Exception e) {
            Log.e(TAG, "HTTP Request error in GeminiPlantAiService", e);
            callback.onError(e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String prepareBase64Image(File file) {
        if (file == null || !file.exists()) return null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);

            int width = options.outWidth;
            int height = options.outHeight;
            int maxDim = 1024;
            int inSampleSize = 1;

            while ((width / inSampleSize) > maxDim || (height / inSampleSize) > maxDim) {
                inSampleSize *= 2;
            }

            options.inJustDecodeBounds = false;
            options.inSampleSize = inSampleSize;

            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (bitmap == null) return null;

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos);
            byte[] bytes = baos.toByteArray();
            bitmap.recycle();

            return Base64.encodeToString(bytes, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "Failed to prepare image for AI", e);
            return null;
        }
    }

    private static String extractTextFromGeminiResponse(JsonObject root) {
        if (root.has("candidates")) {
            JsonArray candidates = root.getAsJsonArray("candidates");
            if (candidates.size() > 0) {
                JsonObject cand = candidates.get(0).getAsJsonObject();
                if (cand.has("content")) {
                    JsonObject content = cand.getAsJsonObject("content");
                    if (content.has("parts")) {
                        JsonArray parts = content.getAsJsonArray("parts");
                        if (parts.size() > 0) {
                            return parts.get(0).getAsJsonObject().get("text").getAsString();
                        }
                    }
                }
            }
        }
        throw new IllegalStateException("Gemini не повернув результату");
    }

    public static String cleanJsonContent(String raw) {
        if (raw == null) return "{}";
        String s = raw.trim();
        if (s.startsWith("```json")) {
            s = s.substring(7);
        } else if (s.startsWith("```")) {
            s = s.substring(3);
        }
        if (s.endsWith("```")) {
            s = s.substring(0, s.length() - 3);
        }
        s = s.trim();
        int firstBrace = s.indexOf("{");
        int lastBrace = s.lastIndexOf("}");
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            s = s.substring(firstBrace, lastBrace + 1);
        }
        return s;
    }
}
