package com.plantshelf.app.data.repository;

import android.app.Application;

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
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlantRepository {

    private final CategoryDao categoryDao;
    private final PlantDao plantDao;
    private final CareLogDao careLogDao;
    private final PhotoDao photoDao;
    private final ExecutorService executor;

    public PlantRepository(Application application) {
        PlantshelfDatabase db = PlantshelfDatabase.getInstance(application);
        this.categoryDao = db.categoryDao();
        this.plantDao = db.plantDao();
        this.careLogDao = db.careLogDao();
        this.photoDao = db.photoDao();
        this.executor = Executors.newFixedThreadPool(4);
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
        executor.execute(() -> plantDao.insert(plant));
    }

    public void updatePlant(PlantEntity plant) {
        executor.execute(() -> plantDao.update(plant));
    }

    public void deletePlant(PlantEntity plant) {
        executor.execute(() -> {
            careLogDao.deleteLogsForPlant(plant.getId());
            photoDao.deletePhotosForPlant(plant.getId());
            plantDao.delete(plant);
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
        });
    }

    public void recordFertilizing(String plantId) {
        executor.execute(() -> {
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            plantDao.updateFertilized(plantId, today);

            String logId = UUID.randomUUID().toString().substring(0, 8);
            CareLogEntity log = new CareLogEntity(logId, plantId, "fert", today, System.currentTimeMillis());
            careLogDao.insert(log);
        });
    }

    public void recordMisting(String plantId) {
        executor.execute(() -> {
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            plantDao.updateMisted(plantId, today);

            String logId = UUID.randomUUID().toString().substring(0, 8);
            CareLogEntity log = new CareLogEntity(logId, plantId, "mist", today, System.currentTimeMillis());
            careLogDao.insert(log);
        });
    }

    // Logs & Photos
    public LiveData<List<CareLogEntity>> getLogsForPlant(String plantId) {
        return careLogDao.getLogsForPlant(plantId);
    }

    public LiveData<List<PhotoEntity>> getPhotosForPlant(String plantId) {
        return photoDao.getPhotosForPlant(plantId);
    }
}
