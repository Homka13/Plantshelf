package com.plantshelf.app.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Represents a room / shelf category for organizing plants.
 * Directly compatible with Brunq backup schema.
 */
@Entity(tableName = "categories")
public class CategoryEntity {

    @PrimaryKey
    @NonNull
    private String id;

    private String name;
    private String color;
    private String icon;
    private boolean smart;

    public CategoryEntity(@NonNull String id, String name, String color, String icon, boolean smart) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.icon = icon;
        this.smart = smart;
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

    public String getColor() {
        return color != null && !color.isEmpty() ? color : "#4E8D7C";
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getIcon() {
        return icon != null ? icon : "leaf";
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public boolean isSmart() {
        return smart;
    }

    public void setSmart(boolean smart) {
        this.smart = smart;
    }
}
