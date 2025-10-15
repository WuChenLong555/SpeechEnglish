package com.example.speechenglish;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class PhonemeAlignmentAdapter extends RecyclerView.Adapter<PhonemeAlignmentAdapter.ViewHolder> {
    private final List<PhonemeAlignmentItem> items;

    public PhonemeAlignmentAdapter(List<PhonemeAlignmentItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_phoneme_alignment, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PhonemeAlignmentItem item = items.get(position);
        holder.tvPhoneme.setText(String.format("音素：%s", item.phoneme));
        holder.tvTime.setText(String.format("时间：%.2f秒", item.timePoint));
        holder.tvScore.setText(String.format("得分：%.2f", item.score));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvPhoneme;
        final TextView tvTime;
        final TextView tvScore;

        ViewHolder(View view) {
            super(view);
            tvPhoneme = view.findViewById(R.id.tv_phoneme);
            tvTime = view.findViewById(R.id.tv_time);
            tvScore = view.findViewById(R.id.tv_score);
        }
    }
} 