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
    private final MediatorLiveData<List<PlantEntity>> filteredPlants = new MediatorLiveData<>();

    public MainViewModel(@NonNull Application application) {
        super(application);
        repository = new PlantRepository(application);
        categories = repository.getAllCategories();
        rawPlants = repository.getAllPlants();

        filteredPlants.addSource(rawPlants, plants -> applyFilter());
        filteredPlants.addSource(selectedCategoryId, cat -> applyFilter());
        filteredPlants.addSource(searchQuery, query -> applyFilter());
    }

    private void applyFilter() {
        List<PlantEntity> all = rawPlants.getValue();
        if (all == null) {
            filteredPlants.setValue(new ArrayList<>());
            return;
        }

        String catId = selectedCategoryId.getValue();
        String query = searchQuery.getValue();
        String trimmedQuery = query != null ? query.trim().toLowerCase() : "";

        List<PlantEntity> result = new ArrayList<>();
        for (PlantEntity plant : all) {
            // Category filter
            if (catId != null && !catId.isEmpty() && !catId.equals(plant.getCategoryId())) {
                continue;
            }

            // Search query filter
            if (!trimmedQuery.isEmpty()) {
                boolean matchesName = plant.getName() != null && plant.getName().toLowerCase().contains(trimmedQuery);
                boolean matchesVariety = plant.getVariety() != null && plant.getVariety().toLowerCase().contains(trimmedQuery);
                boolean matchesLatin = plant.getLatin() != null && plant.getLatin().toLowerCase().contains(trimmedQuery);
                boolean matchesNickname = plant.getNickname() != null && plant.getNickname().toLowerCase().contains(trimmedQuery);

                if (!matchesName && !matchesVariety && !matchesLatin && !matchesNickname) {
                    continue;
                }
            }

            result.add(plant);
        }

        filteredPlants.setValue(result);
    }

    public LiveData<List<CategoryEntity>> getCategories() {
        return categories;
    }

    public LiveData<List<PlantEntity>> getPlants() {
        return filteredPlants;
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

    public void quickMist(String plantId) {
        repository.recordMisting(plantId);
    }

    public void deletePlant(PlantEntity plant) {
        repository.deletePlant(plant);
    }
}
