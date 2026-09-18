package com.plantshelf.app.data.models;

public class CalendarTask {

    private final String plantId;
    private final String plantName;
    private final String plantVariety;
    private final String categoryName;
    private final String categoryColor;
    private final String taskType; // "water", "fert", "mist"
    private final String dueDate;
    private boolean completed;

    public CalendarTask(
            String plantId,
            String plantName,
            String plantVariety,
            String categoryName,
            String categoryColor,
            String taskType,
            String dueDate
    ) {
        this.plantId = plantId;
        this.plantName = plantName;
        this.plantVariety = plantVariety;
        this.categoryName = categoryName;
        this.categoryColor = categoryColor;
        this.taskType = taskType;
        this.dueDate = dueDate;
        this.completed = false;
    }

    public String getPlantId() {
        return plantId;
    }

    public String getPlantName() {
        return plantName;
    }

    public String getPlantVariety() {
        return plantVariety;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getCategoryColor() {
        return categoryColor;
    }

    public String getTaskType() {
        return taskType;
    }

    public String getDueDate() {
        return dueDate;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }
}
