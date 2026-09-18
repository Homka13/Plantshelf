package com.plantshelf.app.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.plantshelf.app.data.entity.PhotoEntity;

import java.util.List;

@Dao
public interface PhotoDao {

    @Query("SELECT * FROM photos WHERE plantId = :plantId ORDER BY date DESC")
    LiveData<List<PhotoEntity>> getPhotosForPlant(String plantId);

    @Query("SELECT * FROM photos WHERE plantId = :plantId ORDER BY date DESC")
    List<PhotoEntity> getPhotosForPlantSync(String plantId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(PhotoEntity photo);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<PhotoEntity> photos);

    @Delete
    void delete(PhotoEntity photo);

    @Query("DELETE FROM photos WHERE plantId = :plantId")
    void deletePhotosForPlant(String plantId);

    @Query("DELETE FROM photos")
    void deleteAll();
}
