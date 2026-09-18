package com.plantshelf.app.data.importer;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.plantshelf.app.data.database.PlantshelfDatabase;
import com.plantshelf.app.data.entity.CareLogEntity;
import com.plantshelf.app.data.entity.CategoryEntity;
import com.plantshelf.app.data.entity.PhotoEntity;
import com.plantshelf.app.data.entity.PlantEntity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Parses and imports Brunq backup JSON files into Plantshelf Room database.
 * Uses streaming JsonReader to prevent OutOfMemoryErrors on large backup files.
 * Protects against path traversal in photo filenames and cleans up orphaned photos.
 */
public class BrunqBackupImporter {

    private static final String TAG = "BrunqBackupImporter";

    public interface ImportCallback {
        void onSuccess(int plantCount, int categoryCount);
        void onError(Exception e);
    }

    public static class ParsedData {
        public final List<CategoryEntity> categories;
        public final List<PlantEntity> plants;
        public final List<CareLogEntity> careLogs;
        public final List<PhotoEntity> photos;

        public ParsedData(List<CategoryEntity> categories, List<PlantEntity> plants,
                          List<CareLogEntity> careLogs, List<PhotoEntity> photos) {
            this.categories = categories;
            this.plants = plants;
            this.careLogs = careLogs;
            this.photos = photos;
        }
    }

    private static class RawPhoto {
        String id;
        String date;
        String dataUrl;
    }

    private static class RawCareLog {
        String id;
        String kind;
        String date;
        long ts;
    }

    public static void importFromStream(
            Context context,
            InputStream inputStream,
            ImportCallback callback
    ) {
        new Thread(() -> {
            File tempPhotosDir = new File(context.getFilesDir(), "photos_temp_" + UUID.randomUUID().toString());
            File finalPhotosDir = new File(context.getFilesDir(), "photos");

            try {
                if (!tempPhotosDir.exists()) {
                    tempPhotosDir.mkdirs();
                }
                if (!finalPhotosDir.exists()) {
                    finalPhotosDir.mkdirs();
                }

                ParsedData data;
                try (JsonReader reader = new JsonReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                    data = parseStream(reader, tempPhotosDir, finalPhotosDir);
                }

                PlantshelfDatabase db = PlantshelfDatabase.getInstance(context);

                // Transaction: delete old data and batch-insert imported entities
                db.runInTransaction(() -> {
                    db.categoryDao().deleteAll();
                    db.plantDao().deleteAll();
                    db.careLogDao().deleteAll();
                    db.photoDao().deleteAll();

                    db.categoryDao().insertAll(data.categories);
                    db.plantDao().insertAll(data.plants);
                    db.careLogDao().insertAll(data.careLogs);
                    db.photoDao().insertAll(data.photos);
                });

                // Commit photo files: delete old photos, move temp photos to final
                File[] oldFiles = finalPhotosDir.listFiles();
                if (oldFiles != null) {
                    for (File f : oldFiles) {
                        if (f.isFile()) {
                            f.delete();
                        }
                    }
                }

                File[] newFiles = tempPhotosDir.listFiles();
                if (newFiles != null) {
                    for (File nf : newFiles) {
                        File dest = new File(finalPhotosDir, nf.getName());
                        if (!nf.renameTo(dest)) {
                            copyFile(nf, dest);
                            nf.delete();
                        }
                    }
                }
                deleteDirectory(tempPhotosDir);

                callback.onSuccess(data.plants.size(), data.categories.size());

            } catch (Exception e) {
                Log.e(TAG, "Error importing Brunq backup", e);
                deleteDirectory(tempPhotosDir);
                callback.onError(e);
            }
        }).start();
    }

    public static ParsedData parseStream(
            JsonReader reader,
            File tempPhotosDir,
            File finalPhotosDir
    ) throws IOException {
        List<CategoryEntity> categories = new ArrayList<>();
        List<PlantEntity> plants = new ArrayList<>();
        List<CareLogEntity> allLogs = new ArrayList<>();
        List<PhotoEntity> allPhotos = new ArrayList<>();

        reader.beginObject();
        while (reader.hasNext()) {
            String rootKey = reader.nextName();
            if ("categories".equals(rootKey)) {
                parseCategoriesStream(reader, categories);
            } else if ("plants".equals(rootKey)) {
                parsePlantsStream(reader, plants, allLogs, allPhotos, tempPhotosDir, finalPhotosDir);
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();

        return new ParsedData(categories, plants, allLogs, allPhotos);
    }

    private static void parseCategoriesStream(JsonReader reader, List<CategoryEntity> categories) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return;
        }
        reader.beginArray();
        while (reader.hasNext()) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                reader.skipValue();
                continue;
            }
            reader.beginObject();
            String id = null;
            String name = "Кімната";
            String color = "#4E8D7C";
            String icon = "leaf";
            boolean smart = false;

            while (reader.hasNext()) {
                String field = reader.nextName();
                if (reader.peek() == JsonToken.NULL) {
                    reader.nextNull();
                    continue;
                }
                switch (field) {
                    case "id":
                        id = sanitizeId(reader.nextString(), "c");
                        break;
                    case "name":
                        name = reader.nextString();
                        break;
                    case "color":
                        color = reader.nextString();
                        break;
                    case "icon":
                        icon = reader.nextString();
                        break;
                    case "smart":
                        smart = readSafeBoolean(reader, false);
                        break;
                    default:
                        reader.skipValue();
                        break;
                }
            }
            reader.endObject();

            if (id == null) {
                id = "c_" + UUID.randomUUID().toString();
            }
            categories.add(new CategoryEntity(id, name, color, icon, smart));
        }
        reader.endArray();
    }

    private static void parsePlantsStream(
            JsonReader reader,
            List<PlantEntity> plants,
            List<CareLogEntity> allLogs,
            List<PhotoEntity> allPhotos,
            File tempPhotosDir,
            File finalPhotosDir
    ) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return;
        }
        reader.beginArray();
        while (reader.hasNext()) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                reader.skipValue();
                continue;
            }
            parseSinglePlant(reader, plants, allLogs, allPhotos, tempPhotosDir, finalPhotosDir);
        }
        reader.endArray();
    }

    private static void parseSinglePlant(
            JsonReader reader,
            List<PlantEntity> plants,
            List<CareLogEntity> allLogs,
            List<PhotoEntity> allPhotos,
            File tempPhotosDir,
            File finalPhotosDir
    ) throws IOException {
        reader.beginObject();

        String rawId = null;
        String name = "Рослина";
        String nickname = "";
        String variety = "";
        String latin = "";
        String categoryId = null;
        String type = "";
        String difficulty = "";
        String light = "";
        String windowSide = "";
        String humidity = "";
        int lux = 5000;
        int intervalDays = 7;
        int intervalDaysWinter = 7;
        String lastWatered = null;
        String fertilizer = "";
        String fertFreq = "";
        int fertIntervalDays = 14;
        String lastFert = null;
        int mistIntervalDays = 3;
        String lastMisted = null;
        int potSize = 0;
        String potDepth = "";
        String potMaterial = "";
        String soil = "";
        String substrate = "";
        String warning = "";
        String comments = "";
        String note = "";
        boolean favorite = false;
        String quarantineUntil = null;
        String quarantineFrom = null;
        String createdAt = null;
        String updatedAt = null;

        List<RawPhoto> plantPhotos = new ArrayList<>();
        List<RawCareLog> plantLogs = new ArrayList<>();

        while (reader.hasNext()) {
            String field = reader.nextName();
            if (reader.peek() == JsonToken.NULL) {
                reader.nextNull();
                continue;
            }
            switch (field) {
                case "id":
                    rawId = reader.nextString();
                    break;
                case "name":
                    name = reader.nextString();
                    break;
                case "nickname":
                    nickname = reader.nextString();
                    break;
                case "variety":
                    variety = reader.nextString();
                    break;
                case "latin":
                    latin = reader.nextString();
                    break;
                case "categoryId":
                    categoryId = cleanOptionalId(reader.nextString());
                    break;
                case "type":
                    type = reader.nextString();
                    break;
                case "difficulty":
                    difficulty = reader.nextString();
                    break;
                case "light":
                    light = reader.nextString();
                    break;
                case "windowSide":
                    windowSide = reader.nextString();
                    break;
                case "humidity":
                    humidity = reader.nextString();
                    break;
                case "lux":
                    lux = readSafeInt(reader, 5000);
                    break;
                case "intervalDays":
                    intervalDays = readSafeInt(reader, 7);
                    break;
                case "intervalDaysWinter":
                    intervalDaysWinter = readSafeInt(reader, intervalDays);
                    break;
                case "lastWatered":
                    lastWatered = reader.nextString();
                    break;
                case "fertilizer":
                    fertilizer = reader.nextString();
                    break;
                case "fertFreq":
                    fertFreq = reader.nextString();
                    break;
                case "fertIntervalDays":
                    fertIntervalDays = readSafeInt(reader, 14);
                    break;
                case "lastFert":
                    lastFert = reader.nextString();
                    break;
                case "mistIntervalDays":
                    mistIntervalDays = readSafeInt(reader, 3);
                    break;
                case "lastMisted":
                    lastMisted = reader.nextString();
                    break;
                case "potSize":
                    potSize = readSafeInt(reader, 0);
                    break;
                case "potDepth":
                    potDepth = reader.nextString();
                    break;
                case "potMaterial":
                    potMaterial = reader.nextString();
                    break;
                case "soil":
                    soil = reader.nextString();
                    break;
                case "substrate":
                    substrate = reader.nextString();
                    break;
                case "warning":
                    warning = reader.nextString();
                    break;
                case "comments":
                    comments = reader.nextString();
                    break;
                case "note":
                    note = reader.nextString();
                    break;
                case "favorite":
                    favorite = readSafeBoolean(reader, false);
                    break;
                case "quarantineUntil":
                    quarantineUntil = reader.nextString();
                    break;
                case "quarantineFrom":
                    quarantineFrom = reader.nextString();
                    break;
                case "createdAt":
                    createdAt = reader.nextString();
                    break;
                case "updatedAt":
                    updatedAt = reader.nextString();
                    break;
                case "photos":
                    parseRawPhotos(reader, plantPhotos);
                    break;
                case "careLog":
                    parseRawCareLogs(reader, plantLogs);
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();

        String plantId = sanitizeId(rawId, "p");
        PlantEntity plant = new PlantEntity(plantId);
        plant.setName(name);
        plant.setNickname(nickname);
        plant.setVariety(variety);
        plant.setLatin(latin);
        plant.setCategoryId(categoryId);
        plant.setType(type);
        plant.setDifficulty(difficulty);
        plant.setLight(light);
        plant.setWindowSide(windowSide);
        plant.setHumidity(humidity);
        plant.setLux(lux);
        plant.setIntervalDays(intervalDays);
        plant.setIntervalDaysWinter(intervalDaysWinter);
        plant.setLastWatered(lastWatered);
        plant.setFertilizer(fertilizer);
        plant.setFertFreq(fertFreq);
        plant.setFertIntervalDays(fertIntervalDays);
        plant.setLastFert(lastFert);
        plant.setMistIntervalDays(mistIntervalDays);
        plant.setLastMisted(lastMisted);
        plant.setPotSize(potSize);
        plant.setPotDepth(potDepth);
        plant.setPotMaterial(potMaterial);
        plant.setSoil(soil);
        plant.setSubstrate(substrate);
        plant.setWarning(warning);
        plant.setComments(comments);
        plant.setNote(note);
        plant.setFavorite(favorite);
        plant.setQuarantineUntil(quarantineUntil);
        plant.setQuarantineFrom(quarantineFrom);
        plant.setCreatedAt(createdAt);
        plant.setUpdatedAt(updatedAt);

        // Process plant photos
        for (RawPhoto rp : plantPhotos) {
            String photoId = sanitizeId(rp.id, "ph");
            String savedPath = saveBase64Image(tempPhotosDir, finalPhotosDir, photoId, rp.dataUrl);
            if (savedPath != null) {
                PhotoEntity photoEntity = new PhotoEntity(photoId, plantId, rp.date != null ? rp.date : "", savedPath);
                allPhotos.add(photoEntity);
                if (plant.getPrimaryPhotoPath() == null) {
                    plant.setPrimaryPhotoPath(savedPath);
                }
            }
        }
        plantPhotos.clear();

        // Process plant care logs
        for (RawCareLog rl : plantLogs) {
            String logId = sanitizeId(rl.id, "log");
            allLogs.add(new CareLogEntity(logId, plantId, rl.kind != null ? rl.kind : "water", rl.date != null ? rl.date : "", rl.ts));
        }
        plantLogs.clear();

        plants.add(plant);
    }

    private static void parseRawPhotos(JsonReader reader, List<RawPhoto> photos) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return;
        }
        reader.beginArray();
        while (reader.hasNext()) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                reader.skipValue();
                continue;
            }
            reader.beginObject();
            RawPhoto photo = new RawPhoto();
            while (reader.hasNext()) {
                String f = reader.nextName();
                if (reader.peek() == JsonToken.NULL) {
                    reader.nextNull();
                    continue;
                }
                switch (f) {
                    case "id":
                        photo.id = reader.nextString();
                        break;
                    case "date":
                        photo.date = reader.nextString();
                        break;
                    case "dataUrl":
                        photo.dataUrl = reader.nextString();
                        break;
                    default:
                        reader.skipValue();
                        break;
                }
            }
            reader.endObject();
            photos.add(photo);
        }
        reader.endArray();
    }

    private static void parseRawCareLogs(JsonReader reader, List<RawCareLog> logs) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return;
        }
        reader.beginArray();
        while (reader.hasNext()) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                reader.skipValue();
                continue;
            }
            reader.beginObject();
            RawCareLog log = new RawCareLog();
            log.ts = System.currentTimeMillis();
            while (reader.hasNext()) {
                String f = reader.nextName();
                if (reader.peek() == JsonToken.NULL) {
                    reader.nextNull();
                    continue;
                }
                switch (f) {
                    case "id":
                        log.id = reader.nextString();
                        break;
                    case "kind":
                        log.kind = reader.nextString();
                        break;
                    case "date":
                        log.date = reader.nextString();
                        break;
                    case "ts":
                        log.ts = readSafeLong(reader, System.currentTimeMillis());
                        break;
                    default:
                        reader.skipValue();
                        break;
                }
            }
            reader.endObject();
            logs.add(log);
        }
        reader.endArray();
    }

    private static String saveBase64Image(File tempDir, File targetDir, String rawPhotoId, String dataUrl) {
        if (dataUrl == null || dataUrl.trim().isEmpty() || tempDir == null || targetDir == null) {
            return null;
        }
        try {
            String base64Data = dataUrl;
            int commaIndex = base64Data.indexOf(',');
            if (commaIndex != -1) {
                base64Data = base64Data.substring(commaIndex + 1);
            }

            String safePhotoId = sanitizeId(rawPhotoId, "ph");
            String fileName = safePhotoId + ".jpg";

            File tempFile = new File(tempDir, fileName);
            File finalFile = new File(targetDir, fileName);

            // Path Traversal Security check
            String tempCanonical = tempFile.getCanonicalPath();
            String tempDirCanonical = tempDir.getCanonicalPath() + File.separator;
            String finalCanonical = finalFile.getCanonicalPath();
            String targetDirCanonical = targetDir.getCanonicalPath() + File.separator;

            if (!tempCanonical.startsWith(tempDirCanonical) || !finalCanonical.startsWith(targetDirCanonical)) {
                Log.w(TAG, "Path traversal attempt rejected for photo: " + rawPhotoId);
                return null;
            }

            byte[] imageBytes = Base64.decode(base64Data, Base64.DEFAULT);
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(imageBytes);
                fos.flush();
            }

            return finalFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Failed to decode/save photo " + rawPhotoId, e);
            return null;
        }
    }

    public static String sanitizeId(String id, String prefix) {
        if (id == null || id.trim().isEmpty()) {
            return prefix != null ? prefix + "_" + UUID.randomUUID().toString() : null;
        }
        String cleaned = id.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (cleaned.isEmpty()) {
            return prefix != null ? prefix + "_" + UUID.randomUUID().toString() : null;
        }
        return cleaned;
    }

    public static String cleanOptionalId(String id) {
        return sanitizeId(id, null);
    }

    private static int readSafeInt(JsonReader reader, int def) {
        try {
            if (reader.peek() == JsonToken.NUMBER || reader.peek() == JsonToken.STRING) {
                return Integer.parseInt(reader.nextString());
            } else {
                reader.skipValue();
                return def;
            }
        } catch (Exception e) {
            return def;
        }
    }

    private static long readSafeLong(JsonReader reader, long def) {
        try {
            if (reader.peek() == JsonToken.NUMBER || reader.peek() == JsonToken.STRING) {
                return Long.parseLong(reader.nextString());
            } else {
                reader.skipValue();
                return def;
            }
        } catch (Exception e) {
            return def;
        }
    }

    private static boolean readSafeBoolean(JsonReader reader, boolean def) {
        try {
            if (reader.peek() == JsonToken.BOOLEAN) {
                return reader.nextBoolean();
            } else if (reader.peek() == JsonToken.STRING) {
                return Boolean.parseBoolean(reader.nextString());
            } else {
                reader.skipValue();
                return def;
            }
        } catch (Exception e) {
            return def;
        }
    }

    private static void copyFile(File src, File dst) throws IOException {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }

    private static void deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteDirectory(f);
                } else {
                    f.delete();
                }
            }
        }
        dir.delete();
    }
}
