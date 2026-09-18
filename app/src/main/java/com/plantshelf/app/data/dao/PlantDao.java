package com.plantshelf.app.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.plantshelf.app.data.entity.PlantEntity;

import java.util.List;

@Dao
public interface PlantDao {

    @Query("SELECT * FROM plants ORDER BY name ASC")
    LiveData<List<PlantEntity>> getAllPlants();

    @Query("SELECT * FROM plants ORDER BY name ASC")
    List<PlantEntity> getAllPlantsSync();

    @Query("SELECT * FROM plants WHERE categoryId = :categoryId ORDER BY name ASC")
    LiveData<List<PlantEntity>> getPlantsByCategory(String categoryId);

    @Query("SELECT * FROM plants WHERE id = :id LIMIT 1")
    LiveData<PlantEntity> getPlantById(String id);

    @Query("SELECT * FROM plants WHERE id = :id LIMIT 1")
    PlantEntity getPlantByIdSync(String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(PlantEntity plant);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<PlantEntity> plants);

    @Update
    void update(PlantEntity plant);

    @Delete
    void delete(PlantEntity plant);

    @Query("DELETE FROM plants")
    void deleteAll();

    @Query("UPDATE plants SET lastWatered = :today WHERE id = :plantId")
    void updateWatered(String plantId, String today);

    @Query("UPDATE plants SET lastFert = :today WHERE id = :plantId")
    void updateFertilized(String plantId, String today);

    @Query("UPDATE plants SET lastMisted = :today WHERE id = :plantId")
    void updateMisted(String plantId, String today);
}
