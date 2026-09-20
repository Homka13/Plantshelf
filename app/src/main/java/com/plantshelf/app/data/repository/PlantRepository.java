package com.plantshelf.app.data.repository;

import android.app.Application;
import android.content.Context;

import androidx.lifecycle.LiveData;

import com.plantshelf.app.data.dao.CareLogDao;
import com.plantshelf.app.data.dao.CategoryDao;
import com.plantshelf.app.data.dao.PhotoDao;
import com.plantshelf.app.data.dao.PlantDao;
import com.plantshelf.app.data.database.PlantshelfDatabase;
import com.plantshelf.app.data.entity.CareLogEntity;
import com.plantshelf.app.data.entity.CategoryEntity;
import com.plantshelf.app.data.entity.PhotoEntity;
import com.plantshelf.app.data.entity.PlantEntity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlantRepository {

    private final Context context;
    private final CategoryDao categoryDao;
    private final PlantDao plantDao;
    private final CareLogDao careLogDao;
    private final PhotoDao photoDao;
    private final ExecutorService executor;

    public PlantRepository(Application application) {
        this.context = application.getApplicationContext();
        PlantshelfDatabase db = PlantshelfDatabase.getInstance(application);
        this.categoryDao = db.categoryDao();
        this.plantDao = db.plantDao();
        this.careLogDao = db.careLogDao();
        this.photoDao = db.photoDao();
        this.executor = Executors.newFixedThreadPool(4);
    }

    public PlantRepository(Context context, CategoryDao categoryDao, PlantDao plantDao,
                           CareLogDao careLogDao, PhotoDao photoDao, ExecutorService executor) {
        this.context = context;
        this.categoryDao = categoryDao;
        this.plantDao = plantDao;
        this.careLogDao = careLogDao;
        this.photoDao = photoDao;
        this.executor = executor != null ? executor : Executors.newSingleThreadExecutor();
    }

    // Categories
    public LiveData<List<CategoryEntity>> getAllCategories() {
        return categoryDao.getAllCategories();
    }

    public void insertCategory(CategoryEntity category) {
        executor.execute(() -> categoryDao.insert(category));
    }

    // Plants
    public LiveData<List<PlantEntity>> getAllPlants() {
        return plantDao.getAllPlants();
    }

    public LiveData<List<PlantEntity>> getPlantsByCategory(String categoryId) {
        return plantDao.getPlantsByCategory(categoryId);
    }

    public LiveData<PlantEntity> getPlantById(String id) {
        return plantDao.getPlantById(id);
    }

    public void insertPlant(PlantEntity plant) {
        executor.execute(() -> {
            plantDao.insert(plant);
            com.plantshelf.app.widget.PlantCareWidgetProvider.sendUpdateBroadcast(context);
        });
    }

    public void updatePlant(PlantEntity plant) {
        executor.execute(() -> {
            plantDao.update(plant);
            if (context != null) {
                com.plantshelf.app.data.calendar.CalendarSyncManager.getInstance(context).updateEventForPlant(plant);
            }
            com.plantshelf.app.widget.PlantCareWidgetProvider.sendUpdateBroadcast(context);
        });
    }

    public void deletePlant(PlantEntity plant) {
        executor.execute(() -> {
            if (context != null) {
                com.plantshelf.app.data.calendar.CalendarSyncManager.getInstance(context).deleteEventForPlantSync(plant);
            }
            careLogDao.deleteLogsForPlant(plant.getId());
            photoDao.deletePhotosForPlant(plant.getId());
            plantDao.delete(plant);
            com.plantshelf.app.widget.PlantCareWidgetProvider.sendUpdateBroadcast(context);
        });
    }

    // Care actions
    public void recordWatering(String plantId) {
        executor.execute(() -> {
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            plantDao.updateWatered(plantId, today);

            String logId = UUID.randomUUID().toString().substring(0, 8);
            CareLogEntity log = new CareLogEntity(logId, plantId, "water", today, System.currentTimeMillis());
            careLogDao.insert(log);
            if (context != null) {
                PlantEntity updatedPlant = plantDao.getPlantByIdSync(plantId);
                if (updatedPlant != null) {
                    com.plantshelf.app.data.calendar.CalendarSyncManager.getInstance(context).updateEventForPlant(updatedPlant);
                }
            }
            com.plantshelf.app.widget.PlantCareWidgetProvider.sendUpdateBroadcast(context);
        });
    }

    public void recordFertilizing(String plantId) {
        executor.execute(() -> {
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            plantDao.updateFertilized(plantId, today);

            String logId = UUID.randomUUID().toString().substring(0, 8);
            CareLogEntity log = new CareLogEntity(logId, plantId, "fert", today, System.currentTimeMillis());
            careLogDao.insert(log);
            com.plantshelf.app.widget.PlantCareWidgetProvider.sendUpdateBroadcast(context);
        });
    }

    public void recordMisting(String plantId) {
        executor.execute(() -> {
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            plantDao.updateMisted(plantId, today);

            String logId = UUID.randomUUID().toString().substring(0, 8);
            CareLogEntity log = new CareLogEntity(logId, plantId, "mist", today, System.currentTimeMillis());
            careLogDao.insert(log);
            com.plantshelf.app.widget.PlantCareWidgetProvider.sendUpdateBroadcast(context);
        });
    }

    public void recordTreatment(String plantId, String drug, String notes, boolean setQuarantine, int quarantineDays, String quarantineReason) {
        executor.execute(() -> {
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            String logId = UUID.randomUUID().toString().substring(0, 8);
            CareLogEntity log = new CareLogEntity(logId, plantId, "treatment", today, System.currentTimeMillis());
            log.setTreatmentDrug(drug);
            log.setNotes(notes);
            careLogDao.insert(log);

            if (setQuarantine) {
                Calendar c = Calendar.getInstance();
                c.add(Calendar.DAY_OF_YEAR, quarantineDays > 0 ? quarantineDays : 14);
                String until = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(c.getTime());
                plantDao.updateQuarantine(plantId, today, until, quarantineReason != null ? quarantineReason : drug);
            }
            com.plantshelf.app.widget.PlantCareWidgetProvider.sendUpdateBroadcast(context);
        });
    }

    public void updateQuarantine(String plantId, String until, String reason) {
        executor.execute(() -> {
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            plantDao.updateQuarantine(plantId, today, until, reason);
            com.plantshelf.app.widget.PlantCareWidgetProvider.sendUpdateBroadcast(context);
        });
    }

    public LiveData<List<PlantEntity>> getQuarantinedPlants() {
        return plantDao.getQuarantinedPlants();
    }

    // Logs & Photos
    public LiveData<List<CareLogEntity>> getLogsForPlant(String plantId) {
        return careLogDao.getLogsForPlant(plantId);
    }

    public void deleteCareLog(CareLogEntity log) {
        executor.execute(() -> {
            careLogDao.delete(log);
            if (log != null && log.getPlantId() != null) {
                if ("water".equals(log.getKind())) {
                    List<CareLogEntity> remainingWaterLogs = careLogDao.getLogsByKindSync(log.getPlantId(), "water");
                    String latestDate = (remainingWaterLogs != null && !remainingWaterLogs.isEmpty())
                            ? remainingWaterLogs.get(0).getDate()
                            : "";
                    plantDao.updateWatered(log.getPlantId(), latestDate);
                } else if ("fert".equals(log.getKind())) {
                    List<CareLogEntity> remainingFertLogs = careLogDao.getLogsByKindSync(log.getPlantId(), "fert");
                    String latestDate = (remainingFertLogs != null && !remainingFertLogs.isEmpty())
                            ? remainingFertLogs.get(0).getDate()
                            : "";
                    plantDao.updateFertilized(log.getPlantId(), latestDate);
                }
                if (context != null) {
                    PlantEntity updatedPlant = plantDao.getPlantByIdSync(log.getPlantId());
                    if (updatedPlant != null) {
                        com.plantshelf.app.data.calendar.CalendarSyncManager.getInstance(context).updateEventForPlant(updatedPlant);
                    }
                    com.plantshelf.app.widget.PlantCareWidgetProvider.sendUpdateBroadcast(context);
                }
            }
        });
    }

    public void insertCareLog(CareLogEntity log) {
        executor.execute(() -> careLogDao.insert(log));
    }

    public LiveData<List<PhotoEntity>> getPhotosForPlant(String plantId) {
        return photoDao.getPhotosForPlant(plantId);
    }
}
