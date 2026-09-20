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
    private String recommendedFertilizers;
    private int fertilizeIntervalSummerDays;
    private int fertilizeIntervalWinterDays;

    // Calendar sync tracking
    private String calendarEventId;

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

    public String getRecommendedFertilizers() {
        return recommendedFertilizers;
    }

    public void setRecommendedFertilizers(String recommendedFertilizers) {
        this.recommendedFertilizers = recommendedFertilizers;
    }

    /**
     * Effective recommended fertilizer helper for UI and AI suggestions with fallback to legacy fertilizer.
     */
    public String getEffectiveRecommendedFertilizers() {
        if (recommendedFertilizers != null && !recommendedFertilizers.trim().isEmpty()) {
            return recommendedFertilizers.trim();
        }
        return fertilizer != null ? fertilizer.trim() : "";
    }

    public int getFertilizeIntervalSummerDays() {
        return fertilizeIntervalSummerDays;
    }

    public void setFertilizeIntervalSummerDays(int fertilizeIntervalSummerDays) {
        this.fertilizeIntervalSummerDays = fertilizeIntervalSummerDays;
    }

    /**
     * Effective summer fertilization interval helper with fallback to legacy fertIntervalDays or 14 days.
     */
    public int getEffectiveFertilizeIntervalSummerDays() {
        if (fertilizeIntervalSummerDays > 0) {
            return fertilizeIntervalSummerDays;
        }
        if (fertIntervalDays > 0) {
            return fertIntervalDays;
        }
        return 14;
    }

    public int getFertilizeIntervalWinterDays() {
        return fertilizeIntervalWinterDays;
    }

    public void setFertilizeIntervalWinterDays(int fertilizeIntervalWinterDays) {
        this.fertilizeIntervalWinterDays = fertilizeIntervalWinterDays;
    }

    /**
     * Effective winter fertilization interval helper with fallback to summer interval.
     */
    public int getEffectiveFertilizeIntervalWinterDays() {
        if (fertilizeIntervalWinterDays > 0) {
            return fertilizeIntervalWinterDays;
        }
        return getEffectiveFertilizeIntervalSummerDays();
    }

    public String getCalendarEventId() {
        return calendarEventId;
    }

    public void setCalendarEventId(String calendarEventId) {
        this.calendarEventId = calendarEventId;
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
     * Delegates domain business logic to CalculateNextWateringUseCase.
     *
     * @return Positive number (days remaining), 0 (needs water today), negative (overdue)
     */
    public int getDaysUntilWatering() {
        com.plantshelf.app.domain.usecase.CalculateNextWateringUseCase useCase =
                new com.plantshelf.app.domain.usecase.CalculateNextWateringUseCase();
        return useCase.execute(lastWatered, intervalDays, intervalDaysWinter).getDaysRemaining();
    }

    public boolean isQuarantined() {
        return quarantineUntil != null && !quarantineUntil.isEmpty();
    }
}
