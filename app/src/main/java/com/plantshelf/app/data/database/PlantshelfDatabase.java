package com.plantshelf.app.data.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

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
        version = 3,
        exportSchema = true
)
public abstract class PlantshelfDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "plantshelf.db";
    private static volatile PlantshelfDatabase INSTANCE;

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE care_logs ADD COLUMN treatmentDrug TEXT");
            database.execSQL("ALTER TABLE care_logs ADD COLUMN notes TEXT");
            database.execSQL("ALTER TABLE plants ADD COLUMN quarantineReason TEXT");
        }
    };

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE plants ADD COLUMN recommendedFertilizers TEXT");
            database.execSQL("ALTER TABLE plants ADD COLUMN fertilizeIntervalSummerDays INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE plants ADD COLUMN fertilizeIntervalWinterDays INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE plants ADD COLUMN calendarEventId TEXT");
        }
    };

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
                    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build();
                }
            }
        }
        return INSTANCE;
    }
}
