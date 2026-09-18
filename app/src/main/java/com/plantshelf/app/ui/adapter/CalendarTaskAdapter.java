package com.plantshelf.app.ui.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.plantshelf.app.R;
import com.plantshelf.app.data.models.CalendarTask;
import com.plantshelf.app.databinding.ItemCalendarTaskBinding;

import java.util.ArrayList;
import java.util.List;

public class CalendarTaskAdapter extends RecyclerView.Adapter<CalendarTaskAdapter.TaskViewHolder> {

    public interface OnTaskCompleteListener {
        void onTaskComplete(CalendarTask task);
    }

    private final List<CalendarTask> tasks = new ArrayList<>();
    private final OnTaskCompleteListener listener;

    public CalendarTaskAdapter(OnTaskCompleteListener listener) {
        this.listener = listener;
    }

    public void setTasks(List<CalendarTask> newTasks) {
        tasks.clear();
        if (newTasks != null) {
            tasks.addAll(newTasks);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCalendarTaskBinding binding = ItemCalendarTaskBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new TaskViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        CalendarTask task = tasks.get(position);

        holder.binding.tvTaskPlantName.setText(task.getPlantName());
        holder.binding.tvTaskCategory.setText(task.getCategoryName());

        String kind = task.getTaskType();
        if ("water".equalsIgnoreCase(kind)) {
            holder.binding.tvTaskKind.setText(R.string.calendar_task_water);
            holder.binding.ivTaskIcon.setImageResource(R.drawable.ic_water);
        } else if ("fert".equalsIgnoreCase(kind)) {
            holder.binding.tvTaskKind.setText(R.string.calendar_task_fert);
            holder.binding.ivTaskIcon.setImageResource(R.drawable.ic_fert);
        } else {
            holder.binding.tvTaskKind.setText(R.string.calendar_task_mist);
            holder.binding.ivTaskIcon.setImageResource(R.drawable.ic_mist);
        }

        if (task.isCompleted()) {
            holder.binding.btnCompleteTask.setEnabled(false);
            holder.binding.btnCompleteTask.setIconResource(R.drawable.ic_check);
            holder.binding.getRoot().setAlpha(0.5f);
        } else {
            holder.binding.btnCompleteTask.setEnabled(true);
            holder.binding.getRoot().setAlpha(1.0f);
            holder.binding.btnCompleteTask.setOnClickListener(v -> {
                task.setCompleted(true);
                notifyItemChanged(holder.getAdapterPosition());
                if (listener != null) {
                    listener.onTaskComplete(task);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        final ItemCalendarTaskBinding binding;

        TaskViewHolder(ItemCalendarTaskBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
