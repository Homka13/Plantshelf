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
        super(application);
        repository = new PlantRepository(application);

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
}
