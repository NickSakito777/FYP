package com.example.lbsdemo.task;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lbsdemo.R; // Assume R is in the base package

import java.util.ArrayList;
import java.util.List;

public class MissionLogAdapter extends RecyclerView.Adapter<MissionLogAdapter.ViewHolder> {

    private List<TaskData> missionLogs = new ArrayList<>();

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView textLogEntry;

        public ViewHolder(View itemView) {
            super(itemView);
            // Find the TextView from item_mission_log.xml
            textLogEntry = itemView.findViewById(R.id.text_log_entry);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the item layout
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_mission_log, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        // Get the data model based on position
        TaskData logEntry = missionLogs.get(position);

        // Set item views based on your views and data model
        // For now, just display the task title. You might want to format this.
        if (logEntry != null && logEntry.title != null) {
             // You could format this string better, e.g., add a timestamp if available
             holder.textLogEntry.setText("已完成: " + logEntry.title);
        } else {
             holder.textLogEntry.setText("日志条目无效");
        }
    }

    @Override
    public int getItemCount() {
        return missionLogs.size();
    }

    // Method to update the list of logs
    public void setLogs(List<TaskData> logs) {
        this.missionLogs = logs == null ? new ArrayList<>() : logs;
        // Notify the adapter that the data set has changed so it can update the RecyclerView
        notifyDataSetChanged();
    }
}
