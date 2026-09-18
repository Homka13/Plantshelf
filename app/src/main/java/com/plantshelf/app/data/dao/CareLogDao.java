package com.plantshelf.app.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.plantshelf.app.data.entity.CareLogEntity;

import java.util.List;

@Dao
public interface CareLogDao {

    @Query("SELECT * FROM care_logs WHERE plantId = :plantId ORDER BY timestamp DESC")
    LiveData<List<CareLogEntity>> getLogsForPlant(String plantId);

    @Query("SELECT * FROM care_logs WHERE plantId = :plantId ORDER BY timestamp DESC")
    List<CareLogEntity> getLogsForPlantSync(String plantId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CareLogEntity log);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<CareLogEntity> logs);

    @Query("DELETE FROM care_logs WHERE plantId = :plantId")
    void deleteLogsForPlant(String plantId);

    @Query("DELETE FROM care_logs")
    void deleteAll();
}
