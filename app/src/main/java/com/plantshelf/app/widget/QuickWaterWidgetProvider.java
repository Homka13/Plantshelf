package com.plantshelf.app.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.plantshelf.app.R;
import com.plantshelf.app.data.database.PlantshelfDatabase;
import com.plantshelf.app.data.entity.CategoryEntity;
import com.plantshelf.app.data.entity.PlantEntity;
import com.plantshelf.app.data.repository.PlantRepository;
import com.plantshelf.app.ui.MainActivity;
import com.plantshelf.app.ui.PlantDetailActivity;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Interactive Urgent Care & Quick Water AppWidget (3x2).
 *
 * <p>Identifies the single most urgent plant requiring water, provides plant photography
 * and location context, and exposes a direct 1-tap "Water Now" button right on the desktop
 * without requiring the user to open the full app.
 */
public class QuickWaterWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_REFRESH_QUICK_WIDGET = "com.plantshelf.app.widget.ACTION_REFRESH_QUICK_WIDGET";
    public static final String ACTION_QUICK_WATER_NOW = "com.plantshelf.app.widget.ACTION_QUICK_WATER_NOW";
    public static final String EXTRA_PLANT_ID = "extra_plant_id";
    public static final String EXTRA_PLANT_NAME = "extra_plant_name";

    private static final ExecutorService workerExecutor = Executors.newSingleThreadExecutor();

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        final Context appContext = context.getApplicationContext();
        workerExecutor.execute(() -> updateWidgetsInternal(appContext, appWidgetManager, appWidgetIds));
        super.onUpdate(context, appWidgetManager, appWidgetIds);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent == null) return;

        String action = intent.getAction();
        if (ACTION_REFRESH_QUICK_WIDGET.equals(action)) {
            WidgetUpdateHelper.updateAllWidgets(context);
        } else if (ACTION_QUICK_WATER_NOW.equals(action)) {
            String plantId = intent.getStringExtra(EXTRA_PLANT_ID);
            String plantName = intent.getStringExtra(EXTRA_PLANT_NAME);
            if (plantId != null && !plantId.isEmpty()) {
                PlantRepository repo = new PlantRepository(context.getApplicationContext());
                repo.recordWatering(plantId);

                new Handler(Looper.getMainLooper()).post(() -> {
                    String name = plantName != null ? plantName : "Рослину";
                    Toast.makeText(context, "🌿 " + name + " полито!", Toast.LENGTH_SHORT).show();
                });
            }
        }
    }

    public static void updateWidgetsInternal(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        if (context == null || manager == null || appWidgetIds == null || appWidgetIds.length == 0) {
            return;
        }

        PlantEntity mostUrgentPlant = null;
        String locationName = "";

        try {
            PlantshelfDatabase db = PlantshelfDatabase.getInstance(context);
            List<CategoryEntity> categories = db.categoryDao().getAllCategoriesSync();
            Map<String, String> catMap = new HashMap<>();
            if (categories != null) {
                for (CategoryEntity cat : categories) {
                    catMap.put(cat.getId(), cat.getName());
                }
            }

            List<PlantEntity> plants = db.plantDao().getAllPlantsSync();
            if (plants != null) {
                for (PlantEntity p : plants) {
                    if (p.isQuarantined()) continue;
                    if (mostUrgentPlant == null || p.getDaysUntilWatering() < mostUrgentPlant.getDaysUntilWatering()) {
                        mostUrgentPlant = p;
                    }
                }
            }

            if (mostUrgentPlant != null) {
                locationName = catMap.get(mostUrgentPlant.getCategoryId());
                if (locationName == null || locationName.isEmpty()) {
                    locationName = mostUrgentPlant.getVariety() != null ? mostUrgentPlant.getVariety() : "Полиця";
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        for (int widgetId : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_quick_water);

            // Refresh action
            Intent refreshIntent = new Intent(context, QuickWaterWidgetProvider.class);
            refreshIntent.setAction(ACTION_REFRESH_QUICK_WIDGET);
            PendingIntent refreshPending = PendingIntent.getBroadcast(
                    context,
                    widgetId,
                    refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            views.setOnClickPendingIntent(R.id.btnQuickRefresh, refreshPending);

            // Either we have an urgent plant or everything is happy
            if (mostUrgentPlant != null && mostUrgentPlant.getDaysUntilWatering() <= 0) {
                views.setViewVisibility(R.id.layoutQuickActivePlant, View.VISIBLE);
                views.setViewVisibility(R.id.layoutQuickAllDone, View.GONE);

                views.setTextViewText(R.id.tvQuickPlantName, mostUrgentPlant.getName());
                views.setTextViewText(R.id.tvQuickPlantLocation, locationName);

                int days = mostUrgentPlant.getDaysUntilWatering();
                if (days < 0) {
                    views.setTextViewText(R.id.tvQuickPlantBadge, "⚠️ Прострочено на " + Math.abs(days) + " дн.");
                } else {
                    views.setTextViewText(R.id.tvQuickPlantBadge, "💧 Потребує поливу сьогодні");
                }

                // Plant photo safely downsampled
                boolean photoLoaded = false;
                String photoPath = mostUrgentPlant.getPrimaryPhotoPath();
                if (photoPath != null && !photoPath.isEmpty()) {
                    File f = new File(photoPath);
                    if (f.exists()) {
                        try {
                            BitmapFactory.Options opts = new BitmapFactory.Options();
                            opts.inSampleSize = 4;
                            Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
                            if (bmp != null) {
                                views.setImageViewBitmap(R.id.ivQuickPlantPhoto, bmp);
                                photoLoaded = true;
                            }
                        } catch (Throwable ignored) {}
                    }
                }
                if (!photoLoaded) {
                    views.setImageViewResource(R.id.ivQuickPlantPhoto, R.drawable.ic_placeholder_plant);
                }

                // Click plant target -> Open PlantDetailActivity
                Intent detailIntent = new Intent(context, PlantDetailActivity.class);
                detailIntent.putExtra(PlantDetailActivity.EXTRA_PLANT_ID, mostUrgentPlant.getId());
                PendingIntent detailPending = PendingIntent.getActivity(
                        context,
                        widgetId,
                        detailIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
                views.setOnClickPendingIntent(R.id.layoutPlantClickTarget, detailPending);

                // Click "Water Now" button -> Send Broadcast to water immediately!
                Intent waterIntent = new Intent(context, QuickWaterWidgetProvider.class);
                waterIntent.setAction(ACTION_QUICK_WATER_NOW);
                waterIntent.putExtra(EXTRA_PLANT_ID, mostUrgentPlant.getId());
                waterIntent.putExtra(EXTRA_PLANT_NAME, mostUrgentPlant.getName());
                PendingIntent waterPending = PendingIntent.getBroadcast(
                        context,
                        widgetId,
                        waterIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
                views.setOnClickPendingIntent(R.id.btnQuickWaterAction, waterPending);

            } else {
                // Happy state: all plants are well-watered!
                views.setViewVisibility(R.id.layoutQuickActivePlant, View.GONE);
                views.setViewVisibility(R.id.layoutQuickAllDone, View.VISIBLE);

                Intent mainIntent = new Intent(context, MainActivity.class);
                PendingIntent mainPending = PendingIntent.getActivity(
                        context,
                        widgetId,
                        mainIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
                views.setOnClickPendingIntent(R.id.layoutQuickAllDone, mainPending);
            }

            manager.updateAppWidget(widgetId, views);
        }
    }
}
