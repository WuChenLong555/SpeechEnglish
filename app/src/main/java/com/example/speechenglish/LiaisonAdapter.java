package com.example.speechenglish;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class LiaisonAdapter extends RecyclerView.Adapter<LiaisonAdapter.ViewHolder> {
    private final List<LiaisonItem> items;

    public LiaisonAdapter(List<LiaisonItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_liaison, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LiaisonItem item = items.get(position);
        holder.tvTransition.setText(String.format("转换：%s", item.transition));
        holder.tvTime.setText(String.format("时间：%.2f秒", item.timePoint));
        holder.tvConfidence.setText(String.format("置信度：%.2f", item.confidence));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTransition;
        final TextView tvTime;
        final TextView tvConfidence;

        ViewHolder(View view) {
            super(view);
            tvTransition = view.findViewById(R.id.tv_transition);
            tvTime = view.findViewById(R.id.tv_time);
            tvConfidence = view.findViewById(R.id.tv_confidence);
        }
    }
} 