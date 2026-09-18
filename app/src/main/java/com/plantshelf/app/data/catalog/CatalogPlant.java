package com.plantshelf.app.data.catalog;

public class CatalogPlant {

    private final String id;
    private final String name;
    private final String latin;
    private final String category; // e.g. "Ароїдні", "Сукуленти", "Фікуси", "Невибагливі"
    private final String difficulty;
    private final String light;
    private final int lux;
    private final int intervalSummer;
    private final int intervalWinter;
    private final int fertInterval;
    private final String humidity;
    private final String soil;
    private final String warning;
    private final String description;

    public CatalogPlant(
            String id,
            String name,
            String latin,
            String category,
            String difficulty,
            String light,
            int lux,
            int intervalSummer,
            int intervalWinter,
            int fertInterval,
            String humidity,
            String soil,
            String warning,
            String description
    ) {
        this.id = id;
        this.name = name;
        this.latin = latin;
        this.category = category;
        this.difficulty = difficulty;
        this.light = light;
        this.lux = lux;
        this.intervalSummer = intervalSummer;
        this.intervalWinter = intervalWinter;
        this.fertInterval = fertInterval;
        this.humidity = humidity;
        this.soil = soil;
        this.warning = warning;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLatin() {
        return latin;
    }

    public String getCategory() {
        return category;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public String getLight() {
        return light;
    }

    public int getLux() {
        return lux;
    }

    public int getIntervalSummer() {
        return intervalSummer;
    }

    public int getIntervalWinter() {
        return intervalWinter;
    }

    public int getFertInterval() {
        return fertInterval;
    }

    public String getHumidity() {
        return humidity;
    }

    public String getSoil() {
        return soil;
    }

    public String getWarning() {
        return warning;
    }

    public String getDescription() {
        return description;
    }
}
