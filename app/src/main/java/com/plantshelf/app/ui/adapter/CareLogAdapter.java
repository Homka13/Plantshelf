package com.plantshelf.app.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.plantshelf.app.R;
import com.plantshelf.app.data.entity.CareLogEntity;
import com.plantshelf.app.databinding.ItemCareLogBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CareLogAdapter extends RecyclerView.Adapter<CareLogAdapter.LogViewHolder> {

    private final List<CareLogEntity> logs = new ArrayList<>();

    public void setLogs(List<CareLogEntity> newLogs) {
        logs.clear();
        if (newLogs != null) {
            logs.addAll(newLogs);
        }
        notifyDataSetChanged();
    }

    public CareLogEntity getItem(int position) {
        if (position >= 0 && position < logs.size()) {
            return logs.get(position);
        }
        return null;
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCareLogBinding binding = ItemCareLogBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new LogViewHolder(binding);
    }

    public static String getLocalizedKindTitle(CareLogEntity log) {
        if (log == null) return "";
        String kind = log.getKind();
        if ("water".equalsIgnoreCase(kind)) {
            return "Полив";
        } else if ("fert".equalsIgnoreCase(kind)) {
            return "Внесення добрив";
        } else if ("mist".equalsIgnoreCase(kind)) {
            return "Обприскування";
        } else if ("treatment".equalsIgnoreCase(kind)) {
            String drug = log.getTreatmentDrug();
            return "Лікування" + (drug != null && !drug.trim().isEmpty() ? " (" + drug.trim() + ")" : " / Обробка");
        } else {
            return kind != null ? kind : "Дія";
        }
    }

    public static String getFormattedDate(CareLogEntity log) {
        if (log == null) return "";
        if (log.getTimestamp() > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMMM yyyy, HH:mm", new Locale("uk"));
            return sdf.format(new Date(log.getTimestamp()));
        } else if (log.getDate() != null) {
            return log.getDate();
        }
        return "";
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        CareLogEntity log = logs.get(position);

        String kind = log.getKind();
        if ("water".equalsIgnoreCase(kind)) {
            holder.binding.tvActionTitle.setText("Полив");
            holder.binding.ivActionIcon.setImageResource(R.drawable.ic_water);
        } else if ("fert".equalsIgnoreCase(kind)) {
            holder.binding.tvActionTitle.setText("Внесення добрив");
            holder.binding.ivActionIcon.setImageResource(R.drawable.ic_fert);
        } else if ("mist".equalsIgnoreCase(kind)) {
            holder.binding.tvActionTitle.setText("Обприскування");
            holder.binding.ivActionIcon.setImageResource(R.drawable.ic_mist);
        } else if ("treatment".equalsIgnoreCase(kind)) {
            String drug = log.getTreatmentDrug();
            holder.binding.tvActionTitle.setText("💊 " + (drug != null && !drug.isEmpty() ? drug : "Лікування / Обробка"));
            holder.binding.ivActionIcon.setImageResource(R.drawable.ic_check);
        } else {
            holder.binding.tvActionTitle.setText(kind != null ? kind : "");
            holder.binding.ivActionIcon.setImageResource(R.drawable.ic_check);
        }

        holder.binding.tvActionDate.setText(getFormattedDate(log));

        String notes = log.getNotes();
        if (notes != null && !notes.trim().isEmpty()) {
            holder.binding.tvActionNotes.setText(notes);
            holder.binding.tvActionNotes.setVisibility(android.view.View.VISIBLE);
        } else {
            holder.binding.tvActionNotes.setVisibility(android.view.View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return logs.size();
    }

    static class LogViewHolder extends RecyclerView.ViewHolder {
        final ItemCareLogBinding binding;

        LogViewHolder(ItemCareLogBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
