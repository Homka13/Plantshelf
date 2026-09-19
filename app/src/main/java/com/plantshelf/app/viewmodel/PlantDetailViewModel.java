package com.plantshelf.app.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.plantshelf.app.data.entity.CareLogEntity;
import com.plantshelf.app.data.entity.PhotoEntity;
import com.plantshelf.app.data.entity.PlantEntity;
import com.plantshelf.app.data.repository.PlantRepository;

import java.util.List;

public class PlantDetailViewModel extends AndroidViewModel {

    private final PlantRepository repository;
    private final MutableLiveData<String> plantIdLiveData = new MutableLiveData<>();
    private final LiveData<PlantEntity> plant;
    private final LiveData<List<CareLogEntity>> careLogs;
    private final LiveData<List<PhotoEntity>> photos;

    public PlantDetailViewModel(@NonNull Application application) {
        this(application, new PlantRepository(application));
    }

    public PlantDetailViewModel(@NonNull Application application, @NonNull PlantRepository repository) {
        super(application);
        this.repository = repository;

        plant = Transformations.switchMap(plantIdLiveData, repository::getPlantById);
        careLogs = Transformations.switchMap(plantIdLiveData, repository::getLogsForPlant);
        photos = Transformations.switchMap(plantIdLiveData, repository::getPhotosForPlant);
    }

    public void setPlantId(String plantId) {
        plantIdLiveData.setValue(plantId);
    }

    public LiveData<PlantEntity> getPlant() {
        return plant;
    }

    public LiveData<List<CareLogEntity>> getCareLogs() {
        return careLogs;
    }

    public LiveData<List<PhotoEntity>> getPhotos() {
        return photos;
    }

    public void recordWatering() {
        String id = plantIdLiveData.getValue();
        if (id != null) {
            repository.recordWatering(id);
        }
    }

    public void recordFertilizing() {
        String id = plantIdLiveData.getValue();
        if (id != null) {
            repository.recordFertilizing(id);
        }
    }

    public void recordMisting() {
        String id = plantIdLiveData.getValue();
        if (id != null) {
            repository.recordMisting(id);
        }
    }

    public void recordTreatment(String drug, String notes, boolean setQuarantine, int quarantineDays, String quarantineReason) {
        String id = plantIdLiveData.getValue();
        if (id != null) {
            repository.recordTreatment(id, drug, notes, setQuarantine, quarantineDays, quarantineReason);
        }
    }

    public void updateQuarantine(String until, String reason) {
        String id = plantIdLiveData.getValue();
        if (id != null) {
            repository.updateQuarantine(id, until, reason);
        }
    }

    public void updatePlant(PlantEntity plant) {
        if (plant != null) {
            repository.updatePlant(plant);
        }
    }

    public void deletePlant(PlantEntity plant) {
        if (plant != null) {
            repository.deletePlant(plant);
        }
    }

    public void deleteCareLog(CareLogEntity log) {
        if (log != null) {
            repository.deleteCareLog(log);
        }
    }

    public void restoreCareLog(CareLogEntity log) {
        if (log != null) {
            repository.insertCareLog(log);
        }
    }
}
