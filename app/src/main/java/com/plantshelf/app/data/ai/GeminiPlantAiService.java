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
import com.plantshelf.app.BuildConfig;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
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
    public static final String KEY_GEMINI_API_KEY = "gemini_api_key";
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
        String savedKey = com.plantshelf.app.domain.security.SecurePreferencesManager.getSecret(context, KEY_GEMINI_API_KEY, "");
        if (savedKey != null && !savedKey.trim().isEmpty()) {
            return savedKey.trim();
        }
        // Only fallback to BuildConfig in debug builds to prevent leaking developer keys in release APKs
        if (BuildConfig.DEBUG) {
            try {
                if (BuildConfig.GEMINI_API_KEY != null && !BuildConfig.GEMINI_API_KEY.trim().isEmpty()
                        && !"your_api_key_here".equalsIgnoreCase(BuildConfig.GEMINI_API_KEY.trim())) {
                    return BuildConfig.GEMINI_API_KEY.trim();
                }
            } catch (Throwable ignored) {
            }
        }
        return "";
    }

    public static String getSavedModel(Context context) {
        return com.plantshelf.app.domain.security.SecurePreferencesManager.getSecurePreferences(context)
                .getString(KEY_GEMINI_MODEL, DEFAULT_MODEL);
    }

    public static void saveApiKey(Context context, String apiKey) {
        saveAiSettings(context, apiKey, getSavedModel(context));
    }

    public static void saveAiSettings(Context context, String apiKey, String model) {
        com.plantshelf.app.domain.security.SecurePreferencesManager.putSecret(context, KEY_GEMINI_API_KEY, apiKey != null ? apiKey.trim() : "");
        com.plantshelf.app.domain.security.SecurePreferencesManager.getSecurePreferences(context)
                .edit()
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
            Exception ex = new IllegalStateException("API-ключ Gemini не встановлено. Будь ласка, вкажіть ваш ключ у налаштуваннях.");
            AiErrorLogger.log(context, "IdentifyPlantByPhoto", ex, "missing_key");
            callback.onError(ex);
            return;
        }

        new Thread(() -> {
            try {
                String base64Image = prepareBase64Image(imageFile);
                if (base64Image == null) {
                    throw new IllegalArgumentException("Не вдалося завантажити або обробити фото для аналізу");
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
                AiErrorLogger.log(context, "IdentifyPlantByPhoto", e, "image=" + (imageFile != null ? imageFile.getName() : "null"));
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
            Exception ex = new IllegalStateException("API-ключ Gemini не встановлено. Будь ласка, вкажіть ваш ключ у налаштуваннях.");
            AiErrorLogger.log(context, "GeneratePlantByName", ex, "plantName=" + plantName + ", missing_key");
            callback.onError(ex);
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
                AiErrorLogger.log(context, "GeneratePlantByName", e, "plantName=" + plantName);
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
                + "  \"recommendedFertilizers\": \"Рекомендований тип і склад добрива (наприклад, комплексне NPK 10-10-10 або для сукулентів)\",\n"
                + "  \"fertilizeIntervalSummerDays\": 14,\n"
                + "  \"fertilizeIntervalWinterDays\": 0,\n"
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

            if (code != 200) {
                String errorBody = response.toString();
                String friendlyMsg;
                if (code == 404) {
                    friendlyMsg = "Модель '" + model + "' недоступна (HTTP 404). Оберіть актуальну модель (наприклад, 'gemini-3.8-flash' або 'gemini-flash-latest') у налаштуваннях AI.";
                } else if (code == 429) {
                    friendlyMsg = "Перевищено ліміти запитів Gemini API (HTTP 429). Зачекайте 1-2 хвилини перед повторним запитом.";
                } else if (code == 400 || code == 403 || code == 401) {
                    friendlyMsg = "Недійсний або неактивний API-ключ Gemini (HTTP " + code + "). Перевірте ключ у налаштуваннях додатка.";
                } else if (code >= 500) {
                    friendlyMsg = "Сервери Google Gemini тимчасово недоступні (HTTP " + code + "). Спробуйте через кілька хвилин.";
                } else {
                    friendlyMsg = "Помилка сервера Gemini (" + code + "): " + errorBody;
                }
                RuntimeException ex = new RuntimeException(friendlyMsg);
                AiErrorLogger.logHttpError(context, "GeminiRequest", model, code, errorBody, ex);
                throw ex;
            }

            JsonObject geminiResp = JsonParser.parseString(response.toString()).getAsJsonObject();
            String rawText = extractTextFromGeminiResponse(geminiResp);
            String cleanJson = cleanJsonContent(rawText);
            AiPlantAnalysisResult result = new Gson().fromJson(cleanJson, AiPlantAnalysisResult.class);
            if (result == null) {
                throw new IllegalStateException("Не вдалося сформувати дані рослини з відповіді AI");
            }

            callback.onSuccess(result);
        } catch (Exception e) {
            Log.e(TAG, "HTTP Request error in GeminiPlantAiService", e);
            Exception friendlyException = e;
            if (e instanceof UnknownHostException || e instanceof ConnectException || e instanceof SocketTimeoutException) {
                friendlyException = new RuntimeException("Відсутній зв'язок з інтернетом або перевищено час очікування відповіді Gemini. Перевірте з'єднання з мережею.", e);
                AiErrorLogger.log(context, "GeminiRequest", friendlyException, "model=" + getSavedModel(context) + ", network_error");
            }
            callback.onError(friendlyException);
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
                if (cand.has("finishReason")) {
                    String finishReason = cand.get("finishReason").getAsString();
                    if ("SAFETY".equalsIgnoreCase(finishReason)) {
                        throw new IllegalStateException("Запит або фото заблоковано фільтрами безпеки вмісту Gemini (SAFETY). Спробуйте інше фото рослини.");
                    } else if ("RECITATION".equalsIgnoreCase(finishReason)) {
                        throw new IllegalStateException("Відповідь заблоковано через політику цитування (RECITATION). Спробуйте ще раз.");
                    }
                }
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
        if (root.has("promptFeedback")) {
            JsonObject pf = root.getAsJsonObject("promptFeedback");
            if (pf.has("blockReason")) {
                String blockReason = pf.get("blockReason").getAsString();
                throw new IllegalStateException("Запит відхилено Gemini (" + blockReason + "). Будь ласка, оберіть інше фото рослини.");
            }
        }
        throw new IllegalStateException("Gemini не повернув результату розпізнавання");
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
