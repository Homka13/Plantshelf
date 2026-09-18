package com.plantshelf.app.data.importer;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.plantshelf.app.data.database.PlantshelfDatabase;
import com.plantshelf.app.data.entity.CareLogEntity;
import com.plantshelf.app.data.entity.CategoryEntity;
import com.plantshelf.app.data.entity.PhotoEntity;
import com.plantshelf.app.data.entity.PlantEntity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses and imports Brunq backup JSON files into Plantshelf Room database.
 * Also decodes embedded Base64 photos into local storage to avoid memory issues.
 */
public class BrunqBackupImporter {

    private static final String TAG = "BrunqBackupImporter";

    public interface ImportCallback {
        void onSuccess(int plantCount, int categoryCount);
        void onError(Exception e);
    }

    public static void importFromStream(
            Context context,
            InputStream inputStream,
            ImportCallback callback
    ) {
        new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                JsonObject root = JsonParser.parseString(sb.toString()).getAsJsonObject();
                PlantshelfDatabase db = PlantshelfDatabase.getInstance(context);

                List<CategoryEntity> categories = parseCategories(root);
                List<PlantEntity> plants = new ArrayList<>();
                List<CareLogEntity> allLogs = new ArrayList<>();
                List<PhotoEntity> allPhotos = new ArrayList<>();

                File photosDir = new File(context.getFilesDir(), "photos");
                if (!photosDir.exists()) {
                    photosDir.mkdirs();
                }

                if (root.has("plants") && root.get("plants").isJsonArray()) {
                    JsonArray plantsArray = root.getAsJsonArray("plants");
                    for (JsonElement element : plantsArray) {
                        if (!element.isJsonObject()) continue;
                        JsonObject pObj = element.getAsJsonObject();

                        String plantId = optString(pObj, "id", "p_" + System.currentTimeMillis());
                        PlantEntity plant = new PlantEntity(plantId);

                        plant.setName(optString(pObj, "name", "Рослина"));
                        plant.setNickname(optString(pObj, "nickname", ""));
                        plant.setVariety(optString(pObj, "variety", ""));
                        plant.setLatin(optString(pObj, "latin", ""));
                        plant.setCategoryId(optString(pObj, "categoryId", null));
                        plant.setType(optString(pObj, "type", ""));
                        plant.setDifficulty(optString(pObj, "difficulty", ""));

                        plant.setLight(optString(pObj, "light", ""));
                        plant.setWindowSide(optString(pObj, "windowSide", ""));
                        plant.setHumidity(optString(pObj, "humidity", ""));
                        plant.setLux(optInt(pObj, "lux", 5000));

                        plant.setIntervalDays(optInt(pObj, "intervalDays", 7));
                        plant.setIntervalDaysWinter(optInt(pObj, "intervalDaysWinter", plant.getIntervalDays()));
                        plant.setLastWatered(optString(pObj, "lastWatered", null));

                        plant.setFertilizer(optString(pObj, "fertilizer", ""));
                        plant.setFertFreq(optString(pObj, "fertFreq", ""));
                        plant.setFertIntervalDays(optInt(pObj, "fertIntervalDays", 14));
                        plant.setLastFert(optString(pObj, "lastFert", null));

                        plant.setMistIntervalDays(optInt(pObj, "mistIntervalDays", 3));
                        plant.setLastMisted(optString(pObj, "lastMisted", null));

                        plant.setPotSize(optInt(pObj, "potSize", 0));
                        plant.setPotDepth(optString(pObj, "potDepth", ""));
                        plant.setPotMaterial(optString(pObj, "potMaterial", ""));
                        plant.setSoil(optString(pObj, "soil", ""));
                        plant.setSubstrate(optString(pObj, "substrate", ""));

                        plant.setWarning(optString(pObj, "warning", ""));
                        plant.setComments(optString(pObj, "comments", ""));
                        plant.setNote(optString(pObj, "note", ""));
                        plant.setFavorite(optBoolean(pObj, "favorite", false));

                        plant.setQuarantineUntil(optString(pObj, "quarantineUntil", null));
                        plant.setQuarantineFrom(optString(pObj, "quarantineFrom", null));
                        plant.setCreatedAt(optString(pObj, "createdAt", null));
                        plant.setUpdatedAt(optString(pObj, "updatedAt", null));

                        // Parse photos
                        if (pObj.has("photos") && pObj.get("photos").isJsonArray()) {
                            JsonArray photosArray = pObj.getAsJsonArray("photos");
                            for (int i = 0; i < photosArray.size(); i++) {
                                JsonObject phObj = photosArray.get(i).getAsJsonObject();
                                String photoId = optString(phObj, "id", "ph_" + System.currentTimeMillis() + "_" + i);
                                String photoDate = optString(phObj, "date", "");
                                String dataUrl = optString(phObj, "dataUrl", "");

                                String savedPath = saveBase64Image(photosDir, photoId, dataUrl);
                                if (savedPath != null) {
                                    PhotoEntity photoEntity = new PhotoEntity(photoId, plantId, photoDate, savedPath);
                                    allPhotos.add(photoEntity);

                                    if (plant.getPrimaryPhotoPath() == null) {
                                        plant.setPrimaryPhotoPath(savedPath);
                                    }
                                }
                            }
                        }

                        // Parse care logs
                        if (pObj.has("careLog") && pObj.get("careLog").isJsonArray()) {
                            JsonArray logsArray = pObj.getAsJsonArray("careLog");
                            for (JsonElement logEl : logsArray) {
                                if (!logEl.isJsonObject()) continue;
                                JsonObject logObj = logEl.getAsJsonObject();
                                String logId = optString(logObj, "id", "log_" + System.currentTimeMillis());
                                String kind = optString(logObj, "kind", "water");
                                String date = optString(logObj, "date", "");
                                long ts = optLong(logObj, "ts", System.currentTimeMillis());

                                allLogs.add(new CareLogEntity(logId, plantId, kind, date, ts));
                            }
                        }

                        plants.add(plant);
                    }
                }

                // Batch insert in transaction
                db.runInTransaction(() -> {
                    db.categoryDao().deleteAll();
                    db.plantDao().deleteAll();
                    db.careLogDao().deleteAll();
                    db.photoDao().deleteAll();

                    db.categoryDao().insertAll(categories);
                    db.plantDao().insertAll(plants);
                    db.careLogDao().insertAll(allLogs);
                    db.photoDao().insertAll(allPhotos);
                });

                callback.onSuccess(plants.size(), categories.size());

            } catch (Exception e) {
                Log.e(TAG, "Error importing Brunq backup", e);
                callback.onError(e);
            }
        }).start();
    }

    private static List<CategoryEntity> parseCategories(JsonObject root) {
        List<CategoryEntity> categories = new ArrayList<>();
        if (root.has("categories") && root.get("categories").isJsonArray()) {
            JsonArray arr = root.getAsJsonArray("categories");
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject c = el.getAsJsonObject();
                String id = optString(c, "id", "c_" + System.currentTimeMillis());
                String name = optString(c, "name", "Кімната");
                String color = optString(c, "color", "#4E8D7C");
                String icon = optString(c, "icon", "leaf");
                boolean smart = optBoolean(c, "smart", false);

                categories.add(new CategoryEntity(id, name, color, icon, smart));
            }
        }
        return categories;
    }

    private static String saveBase64Image(File dir, String photoId, String dataUrl) {
        if (dataUrl == null || dataUrl.isEmpty()) return null;
        try {
            String base64Data = dataUrl;
            if (base64Data.contains(",")) {
                base64Data = base64Data.substring(base64Data.indexOf(",") + 1);
            }
            byte[] imageBytes = Base64.decode(base64Data, Base64.DEFAULT);
            File file = new File(dir, photoId + ".jpg");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(imageBytes);
                fos.flush();
            }
            return file.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Failed to decode/save photo " + photoId, e);
            return null;
        }
    }

    private static String optString(JsonObject obj, String key, String def) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return def;
    }

    private static int optInt(JsonObject obj, String key, int def) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            try {
                return obj.get(key).getAsInt();
            } catch (Exception ignored) {}
        }
        return def;
    }

    private static long optLong(JsonObject obj, String key, long def) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            try {
                return obj.get(key).getAsLong();
            } catch (Exception ignored) {}
        }
        return def;
    }

    private static boolean optBoolean(JsonObject obj, String key, boolean def) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            try {
                return obj.get(key).getAsBoolean();
            } catch (Exception ignored) {}
        }
        return def;
    }
}
