package com.plantshelf.app.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Stores plant photos metadata and local file paths.
 */
@Entity(
        tableName = "photos",
        indices = {@Index("plantId")}
)
public class PhotoEntity {

    @PrimaryKey
    @NonNull
    private String id;

    @NonNull
    private String plantId;

    private String date;
    private String filePath;

    public PhotoEntity(@NonNull String id, @NonNull String plantId, String date, String filePath) {
        this.id = id;
        this.plantId = plantId;
        this.date = date;
        this.filePath = filePath;
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

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
}
