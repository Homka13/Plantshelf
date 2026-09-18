package com.plantshelf.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.plantshelf.app.data.catalog.CatalogPlant;
import com.plantshelf.app.data.catalog.PlantCatalogRepository;

import org.junit.Test;

import java.util.List;

public class PlantCatalogTest {

    @Test
    public void testCatalogHasPopularPlants() {
        List<CatalogPlant> all = PlantCatalogRepository.getAllPlants();
        assertNotNull(all);
        assertTrue("Catalog should contain at least 15 plants", all.size() >= 15);

        // Verify Monstera
        List<CatalogPlant> monstera = PlantCatalogRepository.filter("монстера", "Всі");
        assertFalse(monstera.isEmpty());
        assertTrue(monstera.get(0).getName().contains("Монстера"));
        assertEquals("Monstera deliciosa", monstera.get(0).getLatin());
        assertTrue(monstera.get(0).getIntervalSummer() > 0);
    }

    @Test
    public void testCatalogCategoryFilter() {
        List<CatalogPlant> succulents = PlantCatalogRepository.filter("", "Сукуленти");
        assertFalse(succulents.isEmpty());
        for (CatalogPlant p : succulents) {
            assertEquals("Сукуленти", p.getCategory());
        }
    }

    @Test
    public void testCatalogSearchFilter() {
        List<CatalogPlant> zamioculcas = PlantCatalogRepository.filter("доларове", "Всі");
        assertFalse(zamioculcas.isEmpty());
        assertEquals("Заміокулькас замієлистий", zamioculcas.get(0).getName());
    }
}
