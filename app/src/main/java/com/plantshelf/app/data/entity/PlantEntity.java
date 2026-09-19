package com.plantshelf.app.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Main plant entity containing care schedules, environmental requirements and metadata.
 * 100% compatible with Brunq backup schema.
 */
@Entity(
        tableName = "plants",
        indices = {@Index("categoryId")}
)
public class PlantEntity {

    @PrimaryKey
    @NonNull
    private String id;

    private String name;
    private String nickname;
    private String variety;
    private String latin;
    private String categoryId;
    private String type;
    private String difficulty;

    // Lighting & Environment
    private String light;
    private String windowSide;
    private String humidity;
    private int lux;

    // Watering schedule
    private int intervalDays;
    private int intervalDaysWinter;
    private String lastWatered; // Format: "yyyy-MM-dd"

    // Fertilizing schedule
    private String fertilizer;
    private String fertFreq;
    private int fertIntervalDays;
    private String lastFert; // Format: "yyyy-MM-dd"

    // Misting schedule
    private int mistIntervalDays;
    private String lastMisted; // Format: "yyyy-MM-dd"

    // Physical pot & substrate
    private int potSize;
    private String potDepth;
    private String potMaterial;
    private String soil;
    private String substrate;

    // Warnings & notes
    private String warning;
    private String comments;
    private String note;
    private boolean favorite;

    // Quarantine & Photos
    private String quarantineUntil;
    private String quarantineFrom;
    private String quarantineReason;
    private String primaryPhotoPath;

    private String createdAt;
    private String updatedAt;

    public PlantEntity(@NonNull String id) {
        this.id = id;
    }

    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    public String getName() {
        return name != null ? name : "";
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNickname() {
        return nickname != null ? nickname : "";
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getVariety() {
        return variety != null ? variety : "";
    }

    public void setVariety(String variety) {
        this.variety = variety;
    }

    public String getLatin() {
        return latin != null ? latin : "";
    }

    public void setLatin(String latin) {
        this.latin = latin;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getLight() {
        return light;
    }

    public void setLight(String light) {
        this.light = light;
    }

    public String getWindowSide() {
        return windowSide;
    }

    public void setWindowSide(String windowSide) {
        this.windowSide = windowSide;
    }

    public String getHumidity() {
        return humidity;
    }

    public void setHumidity(String humidity) {
        this.humidity = humidity;
    }

    public int getLux() {
        return lux;
    }

    public void setLux(int lux) {
        this.lux = lux;
    }

    public int getIntervalDays() {
        return intervalDays > 0 ? intervalDays : 7;
    }

    public void setIntervalDays(int intervalDays) {
        this.intervalDays = intervalDays;
    }

    public int getIntervalDaysWinter() {
        return intervalDaysWinter > 0 ? intervalDaysWinter : getIntervalDays();
    }

    public void setIntervalDaysWinter(int intervalDaysWinter) {
        this.intervalDaysWinter = intervalDaysWinter;
    }

    public String getLastWatered() {
        return lastWatered;
    }

    public void setLastWatered(String lastWatered) {
        this.lastWatered = lastWatered;
    }

    public String getFertilizer() {
        return fertilizer;
    }

    public void setFertilizer(String fertilizer) {
        this.fertilizer = fertilizer;
    }

    public String getFertFreq() {
        return fertFreq;
    }

    public void setFertFreq(String fertFreq) {
        this.fertFreq = fertFreq;
    }

    public int getFertIntervalDays() {
        return fertIntervalDays;
    }

    public void setFertIntervalDays(int fertIntervalDays) {
        this.fertIntervalDays = fertIntervalDays;
    }

    public String getLastFert() {
        return lastFert;
    }

    public void setLastFert(String lastFert) {
        this.lastFert = lastFert;
    }

    public int getMistIntervalDays() {
        return mistIntervalDays;
    }

    public void setMistIntervalDays(int mistIntervalDays) {
        this.mistIntervalDays = mistIntervalDays;
    }

    public String getLastMisted() {
        return lastMisted;
    }

    public void setLastMisted(String lastMisted) {
        this.lastMisted = lastMisted;
    }

    public int getPotSize() {
        return potSize;
    }

    public void setPotSize(int potSize) {
        this.potSize = potSize;
    }

    public String getPotDepth() {
        return potDepth;
    }

    public void setPotDepth(String potDepth) {
        this.potDepth = potDepth;
    }

    public String getPotMaterial() {
        return potMaterial;
    }

    public void setPotMaterial(String potMaterial) {
        this.potMaterial = potMaterial;
    }

    public String getSoil() {
        return soil;
    }

    public void setSoil(String soil) {
        this.soil = soil;
    }

    public String getSubstrate() {
        return substrate;
    }

    public void setSubstrate(String substrate) {
        this.substrate = substrate;
    }

    public String getWarning() {
        return warning;
    }

    public void setWarning(String warning) {
        this.warning = warning;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public String getQuarantineUntil() {
        return quarantineUntil;
    }

    public void setQuarantineUntil(String quarantineUntil) {
        this.quarantineUntil = quarantineUntil;
    }

    public String getQuarantineFrom() {
        return quarantineFrom;
    }

    public void setQuarantineFrom(String quarantineFrom) {
        this.quarantineFrom = quarantineFrom;
    }

    public String getQuarantineReason() {
        return quarantineReason;
    }

    public void setQuarantineReason(String quarantineReason) {
        this.quarantineReason = quarantineReason;
    }

    public String getPrimaryPhotoPath() {
        return primaryPhotoPath;
    }

    public void setPrimaryPhotoPath(String primaryPhotoPath) {
        this.primaryPhotoPath = primaryPhotoPath;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Calculates days until next watering based on current season.
     * Positive number: days remaining until watering.
     * 0: needs water today.
     * Negative number: days overdue.
     */
    public int getDaysUntilWatering() {
        if (lastWatered == null || lastWatered.trim().isEmpty()) {
            return 0; // Needs watering immediately
        }

        try {
            String dateStr = lastWatered.trim();
            if (dateStr.length() > 10) {
                dateStr = dateStr.substring(0, 10);
            }
            LocalDate lastDate = LocalDate.parse(dateStr);
            LocalDate today = LocalDate.now();

            int month = today.getMonthValue(); // 1 to 12
            boolean isWinter = (month == 12 || month == 1 || month == 2);

            int interval = isWinter ? getIntervalDaysWinter() : getIntervalDays();
            if (interval <= 0) {
                interval = 7;
            }

            LocalDate nextWater = lastDate.plusDays(interval);
            return (int) ChronoUnit.DAYS.between(today, nextWater);
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean isQuarantined() {
        return quarantineUntil != null && !quarantineUntil.isEmpty();
    }
}
