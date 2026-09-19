package com.plantshelf.app.viewmodel;

import android.app.Application;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.test.core.app.ApplicationProvider;

import com.plantshelf.app.data.entity.CategoryEntity;
import com.plantshelf.app.data.entity.PlantEntity;
import com.plantshelf.app.data.repository.PlantRepository;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
public class MainViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private FakePlantRepository fakeRepository;
    private MainViewModel viewModel;

    static class FakePlantRepository extends PlantRepository {
        final MutableLiveData<List<PlantEntity>> plants = new MutableLiveData<>(new ArrayList<>());
        final MutableLiveData<List<CategoryEntity>> categories = new MutableLiveData<>(new ArrayList<>());

        FakePlantRepository(Application app) {
            super(app, null, null, null, null, null);
        }

        @Override
        public LiveData<List<PlantEntity>> getAllPlants() {
            return plants;
        }

        @Override
        public LiveData<List<CategoryEntity>> getAllCategories() {
            return categories;
        }
    }

    @Before
    public void setUp() {
        Application app = ApplicationProvider.getApplicationContext();
        fakeRepository = new FakePlantRepository(app);
        viewModel = new MainViewModel(app, fakeRepository);
    }

    @Test
    public void testQuarantineCountAndFiltering() {
        List<PlantEntity> plants = new ArrayList<>();

        PlantEntity p1 = new PlantEntity("p1");
        p1.setName("Монстера");
        p1.setQuarantineUntil("2026-10-01"); // Quarantined

        PlantEntity p2 = new PlantEntity("p2");
        p2.setName("Фікус Бенджаміна");
        // Not quarantined

        plants.add(p1);
        plants.add(p2);

        // Observe filtered plants and count to activate MediatorLiveData
        viewModel.getPlants().observeForever(p -> {});
        viewModel.getQuarantinedCount().observeForever(c -> {});

        fakeRepository.plants.setValue(plants);

        assertEquals(Integer.valueOf(1), viewModel.getQuarantinedCount().getValue());
        assertEquals(2, viewModel.getPlants().getValue().size());

        // Enable quarantine filter
        viewModel.setQuarantineFilter(true);
        List<PlantEntity> filtered = viewModel.getPlants().getValue();
        assertNotNull(filtered);
        assertEquals(1, filtered.size());
        assertEquals("p1", filtered.get(0).getId());

        // Disable quarantine filter
        viewModel.setQuarantineFilter(false);
        assertEquals(2, viewModel.getPlants().getValue().size());
    }

    @Test
    public void testSearchQueryFiltering() {
        List<PlantEntity> plants = new ArrayList<>();

        PlantEntity p1 = new PlantEntity("p1");
        p1.setName("Монстера Деліціоза");
        p1.setLatin("Monstera deliciosa");

        PlantEntity p2 = new PlantEntity("p2");
        p2.setName("Заміокулькас");
        p2.setLatin("Zamioculcas zamiifolia");

        plants.add(p1);
        plants.add(p2);

        viewModel.getPlants().observeForever(p -> {});
        fakeRepository.plants.setValue(plants);

        viewModel.setSearchQuery("монстера");
        List<PlantEntity> result = viewModel.getPlants().getValue();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("p1", result.get(0).getId());

        // Search by latin name
        viewModel.setSearchQuery("zamioculcas");
        result = viewModel.getPlants().getValue();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("p2", result.get(0).getId());

        // Clear search
        viewModel.setSearchQuery("");
        assertEquals(2, viewModel.getPlants().getValue().size());
    }
}
