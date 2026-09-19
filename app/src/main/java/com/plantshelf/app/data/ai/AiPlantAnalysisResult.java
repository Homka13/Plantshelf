package com.plantshelf.app.data.ai;

public class AiPlantAnalysisResult {

    private String name;
    private String latin;
    private String variety;
    private String difficulty;
    private String light;
    private int lux;
    private int intervalDaysSummer;
    private int intervalDaysWinter;
    private int fertIntervalDays;
    private String recommendedFertilizers;
    private int fertilizeIntervalSummerDays;
    private int fertilizeIntervalWinterDays;
    private String humidity;
    private String soil;
    private String warning;
    private String notes;

    public String getName() {
        return name != null ? name : "";
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLatin() {
        return latin != null ? latin : "";
    }

    public void setLatin(String latin) {
        this.latin = latin;
    }

    public String getVariety() {
        return variety != null ? variety : "";
    }

    public void setVariety(String variety) {
        this.variety = variety;
    }

    public String getDifficulty() {
        return difficulty != null ? difficulty : "середня";
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getLight() {
        return light != null ? light : "яскраве розсіяне";
    }

    public void setLight(String light) {
        this.light = light;
    }

    public int getLux() {
        return lux > 0 ? lux : 10000;
    }

    public void setLux(int lux) {
        this.lux = lux;
    }

    public int getIntervalDaysSummer() {
        return intervalDaysSummer > 0 ? intervalDaysSummer : 7;
    }

    public void setIntervalDaysSummer(int intervalDaysSummer) {
        this.intervalDaysSummer = intervalDaysSummer;
    }

    public int getIntervalDaysWinter() {
        return intervalDaysWinter > 0 ? intervalDaysWinter : 14;
    }

    public void setIntervalDaysWinter(int intervalDaysWinter) {
        this.intervalDaysWinter = intervalDaysWinter;
    }

    public int getFertIntervalDays() {
        return fertIntervalDays > 0 ? fertIntervalDays : (fertilizeIntervalSummerDays > 0 ? fertilizeIntervalSummerDays : 14);
    }

    public void setFertIntervalDays(int fertIntervalDays) {
        this.fertIntervalDays = fertIntervalDays;
    }

    public String getRecommendedFertilizers() {
        return recommendedFertilizers != null ? recommendedFertilizers : "";
    }

    public void setRecommendedFertilizers(String recommendedFertilizers) {
        this.recommendedFertilizers = recommendedFertilizers;
    }

    public int getFertilizeIntervalSummerDays() {
        return fertilizeIntervalSummerDays > 0 ? fertilizeIntervalSummerDays : getFertIntervalDays();
    }

    public void setFertilizeIntervalSummerDays(int fertilizeIntervalSummerDays) {
        this.fertilizeIntervalSummerDays = fertilizeIntervalSummerDays;
    }

    public int getFertilizeIntervalWinterDays() {
        return fertilizeIntervalWinterDays;
    }

    public void setFertilizeIntervalWinterDays(int fertilizeIntervalWinterDays) {
        this.fertilizeIntervalWinterDays = fertilizeIntervalWinterDays;
    }

    public String getHumidity() {
        return humidity != null ? humidity : "середня";
    }

    public void setHumidity(String humidity) {
        this.humidity = humidity;
    }

    public String getSoil() {
        return soil != null ? soil : "";
    }

    public void setSoil(String soil) {
        this.soil = soil;
    }

    public String getWarning() {
        return warning != null ? warning : "";
    }

    public void setWarning(String warning) {
        this.warning = warning;
    }

    public String getNotes() {
        return notes != null ? notes : "";
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
