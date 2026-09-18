package com.plantshelf.app.ui.adapter;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.plantshelf.app.R;
import com.plantshelf.app.data.entity.CategoryEntity;
import com.plantshelf.app.data.entity.PlantEntity;
import com.plantshelf.app.databinding.ItemPlantCardBinding;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlantAdapter extends RecyclerView.Adapter<PlantAdapter.PlantViewHolder> {

    public interface OnPlantClickListener {
        void onPlantClick(PlantEntity plant);
        void onQuickWater(PlantEntity plant);
        void onQuickMist(PlantEntity plant);
    }

    private final List<PlantEntity> plants = new ArrayList<>();
    private final Map<String, CategoryEntity> categoryMap = new HashMap<>();
    private final OnPlantClickListener listener;

    public PlantAdapter(OnPlantClickListener listener) {
        this.listener = listener;
    }

    public void setPlants(List<PlantEntity> newPlants) {
        plants.clear();
        if (newPlants != null) {
            plants.addAll(newPlants);
        }
        notifyDataSetChanged();
    }

    public void setCategories(List<CategoryEntity> categories) {
        categoryMap.clear();
        if (categories != null) {
            for (CategoryEntity c : categories) {
                categoryMap.put(c.getId(), c);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PlantViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPlantCardBinding binding = ItemPlantCardBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new PlantViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PlantViewHolder holder, int position) {
        PlantEntity plant = plants.get(position);

        holder.binding.tvPlantName.setText(plant.getName());

        String subtitle = !plant.getVariety().isEmpty() ? plant.getVariety() : plant.getLatin();
        holder.binding.tvPlantVariety.setText(subtitle);

        // Category badge
        CategoryEntity category = categoryMap.get(plant.getCategoryId());
        if (category != null) {
            holder.binding.tvShelfBadge.setVisibility(ViewGroup.VISIBLE);
            holder.binding.tvShelfBadge.setText(category.getName());
            try {
                int catColor = Color.parseColor(category.getColor());
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(16);
                bg.setColor(Color.argb(35, Color.red(catColor), Color.green(catColor), Color.blue(catColor)));
                holder.binding.tvShelfBadge.setBackground(bg);
                holder.binding.tvShelfBadge.setTextColor(catColor);
            } catch (Exception ignored) {}
        } else {
            holder.binding.tvShelfBadge.setVisibility(ViewGroup.GONE);
        }

        // Watering status badge
        int daysLeft = plant.getDaysUntilWatering();
        GradientDrawable statusBg = new GradientDrawable();
        statusBg.setCornerRadius(16);

        if (plant.isQuarantined()) {
            String qText = holder.itemView.getContext().getString(R.string.status_quarantine_badge);
            if (plant.getQuarantineReason() != null && !plant.getQuarantineReason().isEmpty()) {
                qText += ": " + plant.getQuarantineReason();
            }
            holder.binding.tvWaterStatusBadge.setText(qText);
            statusBg.setColor(Color.parseColor("#EEDBFF"));
            holder.binding.tvWaterStatusBadge.setTextColor(Color.parseColor("#6A1B9A"));
        } else if (daysLeft < 0) {
            holder.binding.tvWaterStatusBadge.setText(
                    holder.itemView.getContext().getString(R.string.status_overdue, Math.abs(daysLeft))
            );
            statusBg.setColor(Color.parseColor("#FFD8D8"));
            holder.binding.tvWaterStatusBadge.setTextColor(Color.parseColor("#C62828"));
        } else if (daysLeft == 0) {
            holder.binding.tvWaterStatusBadge.setText(R.string.status_today);
            statusBg.setColor(Color.parseColor("#FFE0B2"));
            holder.binding.tvWaterStatusBadge.setTextColor(Color.parseColor("#E65100"));
        } else {
            holder.binding.tvWaterStatusBadge.setText(
                    holder.itemView.getContext().getString(R.string.status_days_left, daysLeft)
            );
            statusBg.setColor(Color.parseColor("#D8F3DC"));
            holder.binding.tvWaterStatusBadge.setTextColor(Color.parseColor("#2D6A4F"));
        }
        holder.binding.tvWaterStatusBadge.setBackground(statusBg);

        // Photo loading with Glide
        if (plant.getPrimaryPhotoPath() != null && new File(plant.getPrimaryPhotoPath()).exists()) {
            Glide.with(holder.itemView.getContext())
                    .load(new File(plant.getPrimaryPhotoPath()))
                    .transform(new CenterCrop(), new RoundedCorners(24))
                    .placeholder(R.drawable.ic_placeholder_plant)
                    .into(holder.binding.ivPlantPhoto);
        } else {
            holder.binding.ivPlantPhoto.setImageResource(R.drawable.ic_placeholder_plant);
        }

        // Click listeners
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onPlantClick(plant);
        });

        holder.binding.btnQuickWater.setOnClickListener(v -> {
            if (listener != null) listener.onQuickWater(plant);
        });

        holder.binding.btnQuickMist.setOnClickListener(v -> {
            if (listener != null) listener.onQuickMist(plant);
        });
    }

    @Override
    public int getItemCount() {
        return plants.size();
    }

    static class PlantViewHolder extends RecyclerView.ViewHolder {
        final ItemPlantCardBinding binding;

        PlantViewHolder(ItemPlantCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
