package com.plantshelf.app.widget;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.plantshelf.app.R;
import com.plantshelf.app.data.database.PlantshelfDatabase;
import com.plantshelf.app.data.entity.CategoryEntity;
import com.plantshelf.app.data.entity.PlantEntity;
import com.plantshelf.app.ui.PlantDetailActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlantCareWidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new PlantCareRemoteViewsFactory(this.getApplicationContext());
    }

    static class PlantCareRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {

        private final Context context;
        private final List<PlantEntity> plants = new ArrayList<>();
        private final Map<String, String> categoryMap = new HashMap<>();

        public PlantCareRemoteViewsFactory(Context context) {
            this.context = context;
        }

        @Override
        public void onCreate() {
            loadData();
        }

        @Override
        public void onDataSetChanged() {
            loadData();
        }

        private void loadData() {
            try {
                PlantshelfDatabase db = PlantshelfDatabase.getInstance(context);
                List<CategoryEntity> categories = db.categoryDao().getAllCategoriesSync();
                categoryMap.clear();
                if (categories != null) {
                    for (CategoryEntity cat : categories) {
                        categoryMap.put(cat.getId(), cat.getName());
                    }
                }

                List<PlantEntity> all = db.plantDao().getAllPlantsSync();
                plants.clear();
                if (all != null) {
                    // Sort plants: overdue/today first, then closest upcoming watering
                    List<PlantEntity> sorted = new ArrayList<>(all);
                    Collections.sort(sorted, (p1, p2) -> Integer.compare(p1.getDaysUntilWatering(), p2.getDaysUntilWatering()));
                    plants.addAll(sorted);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDestroy() {
            plants.clear();
            categoryMap.clear();
        }

        @Override
        public int getCount() {
            return plants.size();
        }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= plants.size()) {
                return null;
            }

            PlantEntity plant = plants.get(position);
            RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_item_plant);

            rv.setTextViewText(R.id.widgetPlantName, plant.getName());

            String location = categoryMap.get(plant.getCategoryId());
            if (location == null || location.isEmpty()) {
                location = plant.getVariety() != null && !plant.getVariety().isEmpty() ? plant.getVariety() : "Полиця";
            }
            rv.setTextViewText(R.id.widgetPlantLocation, location);

            int days = plant.getDaysUntilWatering();
            if (plant.isQuarantined()) {
                rv.setTextViewText(R.id.widgetStatusBadge, "⚠️ Карантин");
            } else if (days < 0) {
                rv.setTextViewText(R.id.widgetStatusBadge, "⚠️ -" + Math.abs(days) + " дн.");
            } else if (days == 0) {
                rv.setTextViewText(R.id.widgetStatusBadge, "💧 Полити");
            } else {
                rv.setTextViewText(R.id.widgetStatusBadge, "Через " + days + " дн.");
            }

            // Safely decode scaled down photo thumbnail for widget to avoid IPC limits
            boolean photoLoaded = false;
            String photoPath = plant.getPrimaryPhotoPath();
            if (photoPath != null && !photoPath.isEmpty()) {
                File file = new File(photoPath);
                if (file.exists()) {
                    try {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inSampleSize = 4; // Downsample 4x to be lightweight (~100x100 max)
                        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                        if (bitmap != null) {
                            rv.setImageViewBitmap(R.id.widgetPlantPhoto, bitmap);
                            photoLoaded = true;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
            if (!photoLoaded) {
                rv.setImageViewResource(R.id.widgetPlantPhoto, R.drawable.ic_placeholder_plant);
            }

            // Fill-in intent to open PlantDetailActivity
            Intent fillInIntent = new Intent();
            fillInIntent.putExtra(PlantDetailActivity.EXTRA_PLANT_ID, plant.getId());
            rv.setOnClickFillInIntent(R.id.widget_item_container, fillInIntent);

            return rv;
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }
    }
}
