package com.plantshelf.app.data.importer;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.plantshelf.app.data.database.PlantshelfDatabase;
import com.plantshelf.app.data.entity.CareLogEntity;
import com.plantshelf.app.data.entity.CategoryEntity;
import com.plantshelf.app.data.entity.PhotoEntity;
import com.plantshelf.app.data.entity.PlantEntity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Exports the Plantshelf database into a standard JSON backup file
 * compatible with Brunq and Plantshelf import.
 */
public class BackupExporter {

    private static final String TAG = "BackupExporter";

    public interface ExportCallback {
        void onSuccess(File exportedFile);
        void onError(Exception e);
    }

    public static void exportToFile(Context context, File targetFile, ExportCallback callback) {
        new Thread(() -> {
            try {
                PlantshelfDatabase db = PlantshelfDatabase.getInstance(context);

                List<CategoryEntity> categories = db.categoryDao().getAllCategoriesSync();
                List<PlantEntity> plants = db.plantDao().getAllPlantsSync();

                JsonObject root = new JsonObject();
                root.addProperty("schemaVersion", 2);
                root.addProperty("lastBackupAt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date()));

                // Categories
                JsonArray catArr = new JsonArray();
                for (CategoryEntity c : categories) {
                    JsonObject co = new JsonObject();
                    co.addProperty("id", c.getId());
                    co.addProperty("name", c.getName());
                    co.addProperty("color", c.getColor());
                    co.addProperty("icon", c.getIcon());
                    co.addProperty("smart", c.isSmart());
                    catArr.add(co);
                }
                root.add("categories", catArr);

                // Plants
                JsonArray plantArr = new JsonArray();
                for (PlantEntity p : plants) {
                    JsonObject po = new JsonObject();
                    po.addProperty("id", p.getId());
                    po.addProperty("name", p.getName());
                    po.addProperty("nickname", p.getNickname());
                    po.addProperty("variety", p.getVariety());
                    po.addProperty("latin", p.getLatin());
                    po.addProperty("categoryId", p.getCategoryId());
                    po.addProperty("type", p.getType());
                    po.addProperty("difficulty", p.getDifficulty());
                    po.addProperty("light", p.getLight());
                    po.addProperty("windowSide", p.getWindowSide());
                    po.addProperty("humidity", p.getHumidity());
                    po.addProperty("lux", p.getLux());
                    po.addProperty("intervalDays", p.getIntervalDays());
                    po.addProperty("intervalDaysWinter", p.getIntervalDaysWinter());
                    po.addProperty("lastWatered", p.getLastWatered());
                    po.addProperty("fertilizer", p.getFertilizer());
                    po.addProperty("fertIntervalDays", p.getFertIntervalDays());
                    po.addProperty("lastFert", p.getLastFert());
                    po.addProperty("mistIntervalDays", p.getMistIntervalDays());
                    po.addProperty("lastMisted", p.getLastMisted());
                    po.addProperty("potSize", p.getPotSize());
                    po.addProperty("soil", p.getSoil());
                    po.addProperty("warning", p.getWarning());
                    po.addProperty("comments", p.getComments());
                    po.addProperty("note", p.getNote());
                    po.addProperty("favorite", p.isFavorite());

                    // Care logs for this plant
                    List<CareLogEntity> logs = db.careLogDao().getLogsForPlantSync(p.getId());
                    JsonArray logArr = new JsonArray();
                    for (CareLogEntity l : logs) {
                        JsonObject lo = new JsonObject();
                        lo.addProperty("id", l.getId());
                        lo.addProperty("kind", l.getKind());
                        lo.addProperty("date", l.getDate());
                        lo.addProperty("ts", l.getTimestamp());
                        logArr.add(lo);
                    }
                    po.add("careLog", logArr);

                    // Photos
                    List<PhotoEntity> photos = db.photoDao().getPhotosForPlantSync(p.getId());
                    JsonArray phArr = new JsonArray();
                    for (PhotoEntity ph : photos) {
                        JsonObject pho = new JsonObject();
                        pho.addProperty("id", ph.getId());
                        pho.addProperty("date", ph.getDate());
                        String base64 = encodeFileToBase64(ph.getFilePath());
                        if (base64 != null) {
                            pho.addProperty("dataUrl", "data:image/jpeg;base64," + base64);
                        }
                        phArr.add(pho);
                    }
                    po.add("photos", phArr);

                    plantArr.add(po);
                }
                root.add("plants", plantArr);

                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(targetFile), StandardCharsets.UTF_8)) {
                    gson.toJson(root, writer);
                }

                callback.onSuccess(targetFile);
            } catch (Exception e) {
                Log.e(TAG, "Error exporting backup", e);
                callback.onError(e);
            }
        }).start();
    }

    private static String encodeFileToBase64(String path) {
        if (path == null) return null;
        File file = new File(path);
        if (!file.exists()) return null;
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            fis.read(bytes);
            return Base64.encodeToString(bytes, Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }
}
