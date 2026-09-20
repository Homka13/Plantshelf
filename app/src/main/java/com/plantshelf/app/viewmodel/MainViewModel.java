package com.plantshelf.app.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.plantshelf.app.data.entity.CategoryEntity;
import com.plantshelf.app.data.entity.PlantEntity;
import com.plantshelf.app.data.repository.PlantRepository;

import java.util.ArrayList;
import java.util.List;

public class MainViewModel extends AndroidViewModel {

    private final PlantRepository repository;
    private final LiveData<List<CategoryEntity>> categories;
    private final LiveData<List<PlantEntity>> rawPlants;

    private final MutableLiveData<String> selectedCategoryId = new MutableLiveData<>(null);
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> quarantineFilterOnly = new MutableLiveData<>(false);
    private final MediatorLiveData<List<PlantEntity>> filteredPlants = new MediatorLiveData<>();
    private final MediatorLiveData<Integer> quarantinedCount = new MediatorLiveData<>();

    public MainViewModel(@NonNull Application application) {
        this(application, new PlantRepository(application));
    }

    public MainViewModel(@NonNull Application application, @NonNull PlantRepository repository) {
        super(application);
        this.repository = repository;
        categories = repository.getAllCategories();
        rawPlants = repository.getAllPlants();

        filteredPlants.addSource(rawPlants, plants -> applyFilter());
        filteredPlants.addSource(selectedCategoryId, cat -> applyFilter());
        filteredPlants.addSource(searchQuery, query -> applyFilter());
        filteredPlants.addSource(quarantineFilterOnly, q -> applyFilter());

        quarantinedCount.addSource(rawPlants, plants -> {
            if (plants == null) {
                quarantinedCount.setValue(0);
                return;
            }
            int count = 0;
            for (PlantEntity p : plants) {
                if (p.isQuarantined()) count++;
            }
            quarantinedCount.setValue(count);
        });
    }

    private final com.plantshelf.app.domain.usecase.FilterPlantsUseCase filterPlantsUseCase =
            new com.plantshelf.app.domain.usecase.FilterPlantsUseCase();

    private void applyFilter() {
        List<PlantEntity> all = rawPlants.getValue();
        if (all == null) {
            filteredPlants.setValue(new ArrayList<>());
            return;
        }

        String catId = selectedCategoryId.getValue();
        String query = searchQuery.getValue();
        boolean quarantineOnly = Boolean.TRUE.equals(quarantineFilterOnly.getValue());

        List<PlantEntity> result = filterPlantsUseCase.execute(all, catId, query, quarantineOnly);
        filteredPlants.setValue(result);
    }

    public LiveData<List<CategoryEntity>> getCategories() {
        return categories;
    }

    public LiveData<List<PlantEntity>> getPlants() {
        return filteredPlants;
    }

    public LiveData<Integer> getQuarantinedCount() {
        return quarantinedCount;
    }

    public LiveData<Boolean> getQuarantineFilterOnly() {
        return quarantineFilterOnly;
    }

    public void setQuarantineFilter(boolean onlyQuarantine) {
        quarantineFilterOnly.setValue(onlyQuarantine);
    }

    public boolean isQuarantineFilterActive() {
        return Boolean.TRUE.equals(quarantineFilterOnly.getValue());
    }

    public void selectCategory(String categoryId) {
        selectedCategoryId.setValue(categoryId);
    }

    public String getSelectedCategoryId() {
        return selectedCategoryId.getValue();
    }

    public void setSearchQuery(String query) {
        searchQuery.setValue(query);
    }

    public void quickWater(String plantId) {
        repository.recordWatering(plantId);
    }

    public void quickFertilize(String plantId) {
        repository.recordFertilizing(plantId);
    }

    public void quickMist(String plantId) {
        repository.recordMisting(plantId);
    }

    public void deletePlant(PlantEntity plant) {
        repository.deletePlant(plant);
    }
}
