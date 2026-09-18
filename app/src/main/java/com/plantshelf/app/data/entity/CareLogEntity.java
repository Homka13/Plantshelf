package com.plantshelf.app.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Historical record of a care event (watering, misting, fertilizing).
 */
@Entity(
        tableName = "care_logs",
        indices = {@Index("plantId")}
)
public class CareLogEntity {

    @PrimaryKey
    @NonNull
    private String id;

    @NonNull
    private String plantId;

    private String kind; // "water", "fert", "mist", "treatment"
    private String date; // "yyyy-MM-dd"
    private long timestamp;
    private String treatmentDrug;
    private String notes;

    public CareLogEntity(@NonNull String id, @NonNull String plantId, String kind, String date, long timestamp) {
        this.id = id;
        this.plantId = plantId;
        this.kind = kind;
        this.date = date;
        this.timestamp = timestamp;
    }

    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    @NonNull
    public String getPlantId() {
        return plantId;
    }

    public void setPlantId(@NonNull String plantId) {
        this.plantId = plantId;
    }

    public String getKind() {
        return kind != null ? kind : "water";
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getTreatmentDrug() {
        return treatmentDrug;
    }

    public void setTreatmentDrug(String treatmentDrug) {
        this.treatmentDrug = treatmentDrug;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
