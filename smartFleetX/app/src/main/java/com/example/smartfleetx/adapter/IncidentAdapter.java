package com.example.smartfleetx.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.example.smartfleetx.R;
import com.example.smartfleetx.model.Incident;

import java.util.List;

public class IncidentAdapter extends RecyclerView.Adapter<IncidentAdapter.VH> {

    public interface OnIncidentClickListener {
        void onIncidentClick(Incident incident);
    }

    private final List<Incident> items;
    private final OnIncidentClickListener listener;

    public IncidentAdapter(List<Incident> items, OnIncidentClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @Override
    public VH onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_incident, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(VH holder, int position) {
        Incident it = items.get(position);
        holder.tvTitle.setText(it.title);
        holder.tvSubtitle.setText(it.subtitle);
        holder.tvStatus.setText(it.status);
        holder.tvStatus.setOnClickListener(v -> {
            if (listener != null) {
                listener.onIncidentClick(it);
            }
        });
        // optionally change icon / color based on status or type
        if ("Resolved".equalsIgnoreCase(it.status)) {
            holder.tvStatus.setAlpha(0.6f);
        } else {
            holder.tvStatus.setAlpha(1f);
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvSubtitle, tvStatus;
        ImageView ivType;
        VH(View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvSubtitle = itemView.findViewById(R.id.tvSubtitle);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            ivType = itemView.findViewById(R.id.ivType);
        }
    }
}
