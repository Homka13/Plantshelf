package com.plantshelf.app.widget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;

import com.plantshelf.app.R;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Central orchestrator for synchronizing all launcher AppWidgets with database changes.
 *
 * <p>Rationale (MIT Comm Lab Style - "Why over What"):
 * Decouples domain write operations from specific AppWidgetProvider implementations.
 * Whenever plants are added, edited, watered, misted, or fertilized, a single call to
 * {@link #updateAllWidgets(Context)} ensures the entire suite of desktop widgets
 * (Care List, Garden Status, Quick Water, and Shortcuts) updates atomically.
 */
public final class WidgetUpdateHelper {

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    private WidgetUpdateHelper() {}

    /**
     * Triggers asynchronous data reload and visual refresh across all installed PlantShelf widgets.
     */
    public static void updateAllWidgets(Context context) {
        if (context == null) return;
        final Context appContext = context.getApplicationContext();

        executor.execute(() -> {
            try {
                AppWidgetManager manager = AppWidgetManager.getInstance(appContext);

                // 1. Scrollable Care List Widget (4x3)
                ComponentName careComponent = new ComponentName(appContext, PlantCareWidgetProvider.class);
                int[] careIds = manager.getAppWidgetIds(careComponent);
                if (careIds != null && careIds.length > 0) {
                    manager.notifyAppWidgetViewDataChanged(careIds, R.id.widgetListView);
                }

                // 2. Compact Garden Status Widget (2x1 / 2x2)
                ComponentName statusComponent = new ComponentName(appContext, PlantStatusWidgetProvider.class);
                int[] statusIds = manager.getAppWidgetIds(statusComponent);
                if (statusIds != null && statusIds.length > 0) {
                    PlantStatusWidgetProvider.updateWidgetsInternal(appContext, manager, statusIds);
                }

                // 3. Urgent Quick Water Widget (3x2)
                ComponentName quickComponent = new ComponentName(appContext, QuickWaterWidgetProvider.class);
                int[] quickIds = manager.getAppWidgetIds(quickComponent);
                if (quickIds != null && quickIds.length > 0) {
                    QuickWaterWidgetProvider.updateWidgetsInternal(appContext, manager, quickIds);
                }

                // 4. Launcher Action Shortcuts Widget (4x1)
                ComponentName shortcutsComponent = new ComponentName(appContext, PlantShortcutsWidgetProvider.class);
                int[] shortcutIds = manager.getAppWidgetIds(shortcutsComponent);
                if (shortcutIds != null && shortcutIds.length > 0) {
                    PlantShortcutsWidgetProvider.updateWidgetsInternal(appContext, manager, shortcutIds);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
