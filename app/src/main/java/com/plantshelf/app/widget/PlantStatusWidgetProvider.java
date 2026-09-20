package com.plantshelf.app.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.plantshelf.app.R;
import com.plantshelf.app.data.database.PlantshelfDatabase;
import com.plantshelf.app.data.entity.PlantEntity;
import com.plantshelf.app.ui.MainActivity;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Compact Garden Status Widget (2x1 / 2x2).
 *
 * <p>Displays an aggregated count of plants needing watering, fertilizing,
 * or immediate attention due to overdue care or quarantine.
 */
public class PlantStatusWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_REFRESH_STATUS_WIDGET = "com.plantshelf.app.widget.ACTION_REFRESH_STATUS_WIDGET";
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
        if (intent != null && ACTION_REFRESH_STATUS_WIDGET.equals(intent.getAction())) {
            WidgetUpdateHelper.updateAllWidgets(context);
        }
    }

    /**
     * Calculates care metrics and applies them to all active status widget instances.
     */
    public static void updateWidgetsInternal(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        if (context == null || manager == null || appWidgetIds == null || appWidgetIds.length == 0) {
            return;
        }

        int waterCount = 0;
        int fertCount = 0;
        int urgentCount = 0;
        int totalPlants = 0;

        try {
            PlantshelfDatabase db = PlantshelfDatabase.getInstance(context);
            List<PlantEntity> plants = db.plantDao().getAllPlantsSync();
            if (plants != null) {
                totalPlants = plants.size();
                for (PlantEntity p : plants) {
                    if (p.isQuarantined()) {
                        urgentCount++;
                        continue;
                    }
                    int days = p.getDaysUntilWatering();
                    if (days < 0) {
                        waterCount++;
                        urgentCount++;
                    } else if (days == 0) {
                        waterCount++;
                    }

                    if (p.getDaysUntilFertilizing() <= 0) {
                        fertCount++;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        for (int widgetId : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_plant_status);

            views.setTextViewText(R.id.tvStatusWaterCount, String.valueOf(waterCount));
            views.setTextViewText(R.id.tvStatusFertCount, String.valueOf(fertCount));
            views.setTextViewText(R.id.tvStatusUrgentCount, String.valueOf(urgentCount));

            String summary;
            if (totalPlants == 0) {
                summary = "Додайте першу рослину 🌿";
            } else if (waterCount == 0 && fertCount == 0 && urgentCount == 0) {
                summary = "Усі " + totalPlants + " рослин доглянуті! 🌿";
            } else if (urgentCount > 0) {
                summary = "⚠️ Потребують уваги: " + urgentCount;
            } else {
                summary = "Сьогодні доглянути: " + (waterCount + fertCount);
            }
            views.setTextViewText(R.id.tvStatusSummary, summary);

            // Tap root -> open MainActivity
            Intent mainIntent = new Intent(context, MainActivity.class);
            PendingIntent mainPending = PendingIntent.getActivity(
                    context,
                    widgetId,
                    mainIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            views.setOnClickPendingIntent(R.id.widgetStatusRoot, mainPending);

            // Refresh button
            Intent refreshIntent = new Intent(context, PlantStatusWidgetProvider.class);
            refreshIntent.setAction(ACTION_REFRESH_STATUS_WIDGET);
            PendingIntent refreshPending = PendingIntent.getBroadcast(
                    context,
                    widgetId,
                    refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            views.setOnClickPendingIntent(R.id.btnStatusRefresh, refreshPending);

            manager.updateAppWidget(widgetId, views);
        }
    }
}
