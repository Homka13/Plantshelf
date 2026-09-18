package com.plantshelf.app.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.plantshelf.app.data.catalog.CatalogPlant;
import com.plantshelf.app.databinding.ItemCatalogPlantBinding;

import java.util.ArrayList;
import java.util.List;

public class PlantCatalogAdapter extends RecyclerView.Adapter<PlantCatalogAdapter.CatalogViewHolder> {

    public interface OnCatalogActionListener {
        void onAddToShelf(CatalogPlant plant);
        void onPlantDetails(CatalogPlant plant);
    }

    private final List<CatalogPlant> plantList = new ArrayList<>();
    private final OnCatalogActionListener listener;

    public PlantCatalogAdapter(OnCatalogActionListener listener) {
        this.listener = listener;
    }

    public void setPlants(List<CatalogPlant> plants) {
        plantList.clear();
        if (plants != null) {
            plantList.addAll(plants);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CatalogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCatalogPlantBinding binding = ItemCatalogPlantBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new CatalogViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull CatalogViewHolder holder, int position) {
        CatalogPlant plant = plantList.get(position);

        holder.binding.tvCatalogName.setText(plant.getName());
        holder.binding.tvCatalogLatin.setText(plant.getLatin());
        holder.binding.tvCatalogCategoryBadge.setText(plant.getCategory());
        holder.binding.tvCatalogDiffBadge.setText(plant.getDifficulty());
        holder.binding.tvCatalogWaterBadge.setText("Полив: " + plant.getIntervalSummer() + " / " + plant.getIntervalWinter() + " дн.");
        holder.binding.tvCatalogDescription.setText(plant.getDescription());

        holder.binding.btnAddToShelf.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAddToShelf(plant);
            }
        });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPlantDetails(plant);
            }
        });
    }

    @Override
    public int getItemCount() {
        return plantList.size();
    }

    static class CatalogViewHolder extends RecyclerView.ViewHolder {
        final ItemCatalogPlantBinding binding;

        CatalogViewHolder(ItemCatalogPlantBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
