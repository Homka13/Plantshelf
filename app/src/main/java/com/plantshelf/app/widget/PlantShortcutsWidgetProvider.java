package com.plantshelf.app.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.plantshelf.app.R;
import com.plantshelf.app.ui.AddEditPlantActivity;
import com.plantshelf.app.ui.CareCalendarActivity;
import com.plantshelf.app.ui.LightMeterActivity;
import com.plantshelf.app.ui.PlantCatalogActivity;

/**
 * Quick Action Launcher Shortcuts Widget (4x1).
 *
 * <p>Provides instant 1-tap navigation from the Android home screen to:
 * 1. Add Plant screen ({@link AddEditPlantActivity})
 * 2. Light Meter lux measurement tool ({@link LightMeterActivity})
 * 3. Care Schedule Calendar ({@link CareCalendarActivity})
 * 4. Botanical Encyclopedia Catalog ({@link PlantCatalogActivity})
 */
public class PlantShortcutsWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        updateWidgetsInternal(context, appWidgetManager, appWidgetIds);
        super.onUpdate(context, appWidgetManager, appWidgetIds);
    }

    public static void updateWidgetsInternal(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        if (context == null || manager == null || appWidgetIds == null || appWidgetIds.length == 0) {
            return;
        }

        for (int widgetId : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_plant_shortcuts);

            // 1. Add Plant Shortcut
            Intent addIntent = new Intent(context, AddEditPlantActivity.class);
            PendingIntent addPending = PendingIntent.getActivity(
                    context,
                    widgetId * 10 + 1,
                    addIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            views.setOnClickPendingIntent(R.id.btnShortcutAdd, addPending);

            // 2. Light Meter Shortcut
            Intent lightIntent = new Intent(context, LightMeterActivity.class);
            PendingIntent lightPending = PendingIntent.getActivity(
                    context,
                    widgetId * 10 + 2,
                    lightIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            views.setOnClickPendingIntent(R.id.btnShortcutLight, lightPending);

            // 3. Calendar Shortcut
            Intent calIntent = new Intent(context, CareCalendarActivity.class);
            PendingIntent calPending = PendingIntent.getActivity(
                    context,
                    widgetId * 10 + 3,
                    calIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            views.setOnClickPendingIntent(R.id.btnShortcutCalendar, calPending);

            // 4. Botanical Catalog Shortcut
            Intent catalogIntent = new Intent(context, PlantCatalogActivity.class);
            PendingIntent catalogPending = PendingIntent.getActivity(
                    context,
                    widgetId * 10 + 4,
                    catalogIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            views.setOnClickPendingIntent(R.id.btnShortcutCatalog, catalogPending);

            manager.updateAppWidget(widgetId, views);
        }
    }
}
