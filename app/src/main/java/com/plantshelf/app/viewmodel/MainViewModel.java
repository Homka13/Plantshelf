package com.plantshelf.app.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.plantshelf.app.data.entity.CategoryEntity;
import com.plantshelf.app.data.entity.PlantEntity;
import com.plantshelf.app.data.repository.PlantRepository;

import java.util.ArrayList;
import java.util.List;

public class MainViewModel extends AndroidViewModel {

    private final PlantRepository repository;
    private final LiveData<List<CategoryEntity>> categories;
    private final MutableLiveData<String> selectedCategoryId = new MutableLiveData<>(null);
    private final LiveData<List<PlantEntity>> plants;

    public MainViewModel(@NonNull Application application) {
        super(application);
        repository = new PlantRepository(application);
        categories = repository.getAllCategories();

        plants = Transformations.switchMap(selectedCategoryId, categoryId -> {
            if (categoryId == null || categoryId.isEmpty()) {
                return repository.getAllPlants();
            } else {
                return repository.getPlantsByCategory(categoryId);
            }
        });
    }

    public LiveData<List<CategoryEntity>> getCategories() {
        return categories;
    }

    public LiveData<List<PlantEntity>> getPlants() {
        return plants;
    }

    public void selectCategory(String categoryId) {
        selectedCategoryId.setValue(categoryId);
    }

    public String getSelectedCategoryId() {
        return selectedCategoryId.getValue();
    }

    public void quickWater(String plantId) {
        repository.recordWatering(plantId);
    }

    public void quickMist(String plantId) {
        repository.recordMisting(plantId);
    }

    public void deletePlant(PlantEntity plant) {
        repository.deletePlant(plant);
    }
}
