package com.plantshelf.app.ui.adapter;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.plantshelf.app.data.entity.CategoryEntity;
import com.plantshelf.app.databinding.ItemShelfChipBinding;

import java.util.ArrayList;
import java.util.List;

public class ShelfAdapter extends RecyclerView.Adapter<ShelfAdapter.ShelfViewHolder> {

    public interface OnShelfClickListener {
        void onShelfClick(String categoryId);
    }

    private final List<CategoryEntity> categories = new ArrayList<>();
    private String selectedCategoryId = null; // null means "All"
    private final OnShelfClickListener listener;

    public ShelfAdapter(OnShelfClickListener listener) {
        this.listener = listener;
    }

    public void setCategories(List<CategoryEntity> newCategories) {
        categories.clear();
        if (newCategories != null) {
            categories.addAll(newCategories);
        }
        notifyDataSetChanged();
    }

    public void setSelectedCategoryId(String id) {
        this.selectedCategoryId = id;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ShelfViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemShelfChipBinding binding = ItemShelfChipBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new ShelfViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ShelfViewHolder holder, int position) {
        if (position == 0) {
            // "All Plants" chip
            boolean isSelected = (selectedCategoryId == null);
            holder.binding.tvShelfName.setText("Всі рослини");
            holder.binding.viewColorDot.setVisibility(ViewGroup.GONE);

            styleChip(holder.binding, isSelected, "#2D6A4F");

            holder.itemView.setOnClickListener(v -> {
                selectedCategoryId = null;
                notifyDataSetChanged();
                if (listener != null) listener.onShelfClick(null);
            });
        } else {
            CategoryEntity category = categories.get(position - 1);
            boolean isSelected = category.getId().equals(selectedCategoryId);

            holder.binding.tvShelfName.setText(category.getName());
            holder.binding.viewColorDot.setVisibility(ViewGroup.VISIBLE);

            try {
                int dotColor = Color.parseColor(category.getColor());
                GradientDrawable dot = new GradientDrawable();
                dot.setShape(GradientDrawable.OVAL);
                dot.setColor(dotColor);
                holder.binding.viewColorDot.setBackground(dot);
            } catch (Exception ignored) {}

            styleChip(holder.binding, isSelected, category.getColor());

            holder.itemView.setOnClickListener(v -> {
                selectedCategoryId = category.getId();
                notifyDataSetChanged();
                if (listener != null) listener.onShelfClick(category.getId());
            });
        }
    }

    private void styleChip(ItemShelfChipBinding binding, boolean isSelected, String colorHex) {
        if (isSelected) {
            try {
                int col = Color.parseColor(colorHex);
                binding.cardShelf.setCardBackgroundColor(col);
                binding.tvShelfName.setTextColor(Color.WHITE);
                binding.cardShelf.setStrokeWidth(0);
            } catch (Exception e) {
                binding.cardShelf.setCardBackgroundColor(Color.DKGRAY);
                binding.tvShelfName.setTextColor(Color.WHITE);
            }
        } else {
            binding.cardShelf.setCardBackgroundColor(Color.TRANSPARENT);
            binding.tvShelfName.setTextColor(Color.DKGRAY);
            binding.cardShelf.setStrokeWidth(2);
        }
    }

    @Override
    public int getItemCount() {
        return categories.size() + 1; // +1 for "All"
    }

    static class ShelfViewHolder extends RecyclerView.ViewHolder {
        final ItemShelfChipBinding binding;

        ShelfViewHolder(ItemShelfChipBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
