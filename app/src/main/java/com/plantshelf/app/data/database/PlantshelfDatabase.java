package com.plantshelf.app.data.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.plantshelf.app.data.dao.CareLogDao;
import com.plantshelf.app.data.dao.CategoryDao;
import com.plantshelf.app.data.dao.PhotoDao;
import com.plantshelf.app.data.dao.PlantDao;
import com.plantshelf.app.data.entity.CareLogEntity;
import com.plantshelf.app.data.entity.CategoryEntity;
import com.plantshelf.app.data.entity.PhotoEntity;
import com.plantshelf.app.data.entity.PlantEntity;

@Database(
        entities = {CategoryEntity.class, PlantEntity.class, CareLogEntity.class, PhotoEntity.class},
        version = 2,
        exportSchema = false
)
public abstract class PlantshelfDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "plantshelf.db";
    private static volatile PlantshelfDatabase INSTANCE;

    public abstract CategoryDao categoryDao();
    public abstract PlantDao plantDao();
    public abstract CareLogDao careLogDao();
    public abstract PhotoDao photoDao();

    public static PlantshelfDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (PlantshelfDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            PlantshelfDatabase.class,
                            DATABASE_NAME
                    ).fallbackToDestructiveMigration().build();
                }
            }
        }
        return INSTANCE;
    }
}
