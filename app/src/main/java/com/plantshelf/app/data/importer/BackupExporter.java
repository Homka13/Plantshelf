package com.plantshelf.app.data.importer;

import android.content.Context;
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
import java.util.Base64;
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

    @FunctionalInterface
    public interface CareLogLookup {
        List<CareLogEntity> getLogsForPlant(String plantId);
    }

    @FunctionalInterface
    public interface PhotoLookup {
        List<PhotoEntity> getPhotosForPlant(String plantId);
    }

    public static void exportToFile(Context context, File targetFile, ExportCallback callback) {
        new Thread(() -> {
            try {
                PlantshelfDatabase db = PlantshelfDatabase.getInstance(context);

                List<CategoryEntity> categories = db.categoryDao().getAllCategoriesSync();
                List<PlantEntity> plants = db.plantDao().getAllPlantsSync();

                JsonObject root = buildBackupJson(
                        categories,
                        plants,
                        plantId -> db.careLogDao().getLogsForPlantSync(plantId),
                        plantId -> db.photoDao().getPhotosForPlantSync(plantId)
                );

                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(targetFile), StandardCharsets.UTF_8)) {
                    gson.toJson(root, writer);
                }

                callback.onSuccess(targetFile);
            } catch (Exception e) {
                logError(TAG, "Error exporting backup", e);
                callback.onError(e);
            }
        }).start();
    }

    public static JsonObject buildBackupJson(
            List<CategoryEntity> categories,
            List<PlantEntity> plants,
            CareLogLookup careLogLookup,
            PhotoLookup photoLookup
    ) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 2);
        root.addProperty("lastBackupAt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date()));

        // Categories
        JsonArray catArr = new JsonArray();
        if (categories != null) {
            for (CategoryEntity c : categories) {
                JsonObject co = new JsonObject();
                co.addProperty("id", c.getId());
                co.addProperty("name", c.getName());
                co.addProperty("color", c.getColor());
                co.addProperty("icon", c.getIcon());
                co.addProperty("smart", c.isSmart());
                catArr.add(co);
            }
        }
        root.add("categories", catArr);

        // Plants
        JsonArray plantArr = new JsonArray();
        if (plants != null) {
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
                po.addProperty("fertFreq", p.getFertFreq());
                po.addProperty("fertIntervalDays", p.getFertIntervalDays());
                po.addProperty("lastFert", p.getLastFert());
                po.addProperty("mistIntervalDays", p.getMistIntervalDays());
                po.addProperty("lastMisted", p.getLastMisted());
                po.addProperty("potSize", p.getPotSize());
                po.addProperty("potDepth", p.getPotDepth());
                po.addProperty("potMaterial", p.getPotMaterial());
                po.addProperty("soil", p.getSoil());
                po.addProperty("substrate", p.getSubstrate());
                po.addProperty("warning", p.getWarning());
                po.addProperty("comments", p.getComments());
                po.addProperty("note", p.getNote());
                po.addProperty("favorite", p.isFavorite());
                po.addProperty("quarantineUntil", p.getQuarantineUntil());
                po.addProperty("quarantineFrom", p.getQuarantineFrom());
                po.addProperty("quarantineReason", p.getQuarantineReason());
                po.addProperty("recommendedFertilizers", p.getRecommendedFertilizers());
                po.addProperty("fertilizeIntervalSummerDays", p.getFertilizeIntervalSummerDays());
                po.addProperty("fertilizeIntervalWinterDays", p.getFertilizeIntervalWinterDays());
                po.addProperty("calendarEventId", p.getCalendarEventId());
                po.addProperty("createdAt", p.getCreatedAt());
                po.addProperty("updatedAt", p.getUpdatedAt());

                // Care logs for this plant
                JsonArray logArr = new JsonArray();
                if (careLogLookup != null) {
                    List<CareLogEntity> logs = careLogLookup.getLogsForPlant(p.getId());
                    if (logs != null) {
                        for (CareLogEntity l : logs) {
                            JsonObject lo = new JsonObject();
                            lo.addProperty("id", l.getId());
                            lo.addProperty("kind", l.getKind());
                            lo.addProperty("date", l.getDate());
                            lo.addProperty("ts", l.getTimestamp());
                            lo.addProperty("treatmentDrug", l.getTreatmentDrug());
                            lo.addProperty("notes", l.getNotes());
                            logArr.add(lo);
                        }
                    }
                }
                po.add("careLog", logArr);

                // Photos
                JsonArray phArr = new JsonArray();
                if (photoLookup != null) {
                    List<PhotoEntity> photos = photoLookup.getPhotosForPlant(p.getId());
                    if (photos != null) {
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
                    }
                }
                po.add("photos", phArr);

                plantArr.add(po);
            }
        }
        root.add("plants", plantArr);

        return root;
    }

    public static String encodeFileToBase64(String path) {
        if (path == null) return null;
        File file = new File(path);
        if (!file.exists() || !file.isFile()) return null;
        try {
            long length = file.length();
            if (length > Integer.MAX_VALUE) {
                return null;
            }
            byte[] bytes = new byte[(int) length];
            try (FileInputStream fis = new FileInputStream(file)) {
                int offset = 0;
                int numRead;
                while (offset < bytes.length && (numRead = fis.read(bytes, offset, bytes.length - offset)) >= 0) {
                    offset += numRead;
                }
                if (offset < bytes.length) {
                    byte[] exact = new byte[offset];
                    System.arraycopy(bytes, 0, exact, 0, offset);
                    bytes = exact;
                }
            }
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            return null;
        }
    }

    private static void logError(String tag, String msg, Throwable tr) {
        try {
            Log.e(tag, msg, tr);
        } catch (Throwable t) {
            System.err.println(tag + " [ERROR]: " + msg);
            if (tr != null) tr.printStackTrace();
        }
    }
}
