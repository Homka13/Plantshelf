package com.plantshelf.app.notifications;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.plantshelf.app.R;
import com.plantshelf.app.data.database.PlantshelfDatabase;
import com.plantshelf.app.data.entity.PlantEntity;

import java.util.ArrayList;
import java.util.List;

public class CareReminderWorker extends Worker {

    public CareReminderWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        PlantshelfDatabase db = PlantshelfDatabase.getInstance(context);

        List<PlantEntity> plants = db.plantDao().getAllPlantsSync();
        if (plants == null || plants.isEmpty()) {
            return Result.success();
        }

        List<PlantEntity> needsCare = new ArrayList<>();
        for (PlantEntity plant : plants) {
            if (plant.getDaysUntilWatering() <= 0 && !plant.isQuarantined()) {
                needsCare.add(plant);
            }
        }

        if (!needsCare.isEmpty()) {
            String title = context.getString(R.string.notif_title_care_needed);
            String message;
            if (needsCare.size() == 1) {
                message = context.getString(R.string.notif_body_single, needsCare.get(0).getName());
            } else {
                message = context.getString(R.string.notif_body_multiple, needsCare.size());
            }

            NotificationHelper.sendCareReminderNotification(context, title, message);
        }

        return Result.success();
    }
}
