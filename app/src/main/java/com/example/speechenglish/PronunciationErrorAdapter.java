package com.example.speechenglish;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * 发音错误适配器
 * 用于在RecyclerView中显示发音错误列表
 */
public class PronunciationErrorAdapter extends RecyclerView.Adapter<PronunciationErrorAdapter.ErrorViewHolder> {
    
    private List<PronunciationErrorItem> errorItems;
    
    public PronunciationErrorAdapter(List<PronunciationErrorItem> errorItems) {
        this.errorItems = errorItems;
    }
    
    @NonNull
    @Override
    public ErrorViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_pronunciation_error, parent, false);
        return new ErrorViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ErrorViewHolder holder, int position) {
        PronunciationErrorItem item = errorItems.get(position);
        holder.bind(item);
    }
    
    @Override
    public int getItemCount() {
        return errorItems != null ? errorItems.size() : 0;
    }
    
    static class ErrorViewHolder extends RecyclerView.ViewHolder {
        private TextView tvErrorType;
        private TextView tvDescription;
        private TextView tvPhonemes;
        private TextView tvTimeRange;
        private TextView tvConfidence;
        private View severityIndicator;
        
        public ErrorViewHolder(@NonNull View itemView) {
            super(itemView);
            tvErrorType = itemView.findViewById(R.id.tv_error_type);
            tvDescription = itemView.findViewById(R.id.tv_description);
            tvPhonemes = itemView.findViewById(R.id.tv_phonemes);
            tvTimeRange = itemView.findViewById(R.id.tv_time_range);
            tvConfidence = itemView.findViewById(R.id.tv_confidence);
            severityIndicator = itemView.findViewById(R.id.severity_indicator);
        }
        
        public void bind(PronunciationErrorItem item) {
            // 设置错误类型
            tvErrorType.setText(getErrorTypeDisplayName(item.getErrorType()));
            
            // 设置描述
            tvDescription.setText(item.getDescription());
            
            // 设置音素信息
            if (item.getActualPhoneme() != null && !item.getActualPhoneme().isEmpty()) {
                tvPhonemes.setText(String.format("期望: %s → 实际: %s", 
                    item.getExpectedPhoneme(), item.getActualPhoneme()));
            } else {
                tvPhonemes.setText(String.format("期望: %s", item.getExpectedPhoneme()));
            }
            
            // 设置时间范围
            tvTimeRange.setText(item.getTimeRange());
            
            // 设置置信度
            tvConfidence.setText(String.format("置信度: %.1f%%", item.getConfidence() * 100));
            
            // 设置严重程度指示器颜色
            setSeverityIndicator(item.getSeverityLevel());
            
            // 根据错误类型设置背景色
            setErrorTypeBackground(item);
        }
        
        private String getErrorTypeDisplayName(String errorType) {
            switch (errorType) {
                case "DELETION":
                    return "缺失";
                case "SUBSTITUTION":
                    return "替换";
                case "INSERTION":
                    return "插入";
                case "SUBSTITUTION_Y_LINKING":
                    return "Y连读";
                case "SUBSTITUTION_SH_LINKING":
                    return "SH连读";
                case "SUBSTITUTION_NASALIZATION":
                    return "鼻音化";
                case "SUBSTITUTION_WEAK":
                    return "弱读";
                case "DELETION_H_DROPPING":
                    return "H音脱落";
                case "DELETION_PLOSIVE_ELISION":
                    return "爆破音省略";
                case "DELETION_SAME_CONSONANT":
                    return "相同辅音连读";
                default:
                    return errorType;
            }
        }
        
        private void setSeverityIndicator(int severityLevel) {
            int color;
            switch (severityLevel) {
                case 0: // 轻微
                    color = Color.parseColor("#4CAF50"); // 绿色
                    break;
                case 1: // 中等
                    color = Color.parseColor("#FF9800"); // 橙色
                    break;
                case 2: // 严重
                    color = Color.parseColor("#F44336"); // 红色
                    break;
                default:
                    color = Color.parseColor("#9E9E9E"); // 灰色
                    break;
            }
            severityIndicator.setBackgroundColor(color);
        }
        
        private void setErrorTypeBackground(PronunciationErrorItem item) {
            int backgroundColor;
            if (item.isLiaisonError()) {
                // 连读错误 - 浅蓝色背景
                backgroundColor = Color.parseColor("#E3F2FD");
            } else if (item.isWeakFormError()) {
                // 弱读错误 - 浅紫色背景
                backgroundColor = Color.parseColor("#F3E5F5");
            } else {
                // 其他错误 - 浅灰色背景
                backgroundColor = Color.parseColor("#F5F5F5");
            }
            itemView.setBackgroundColor(backgroundColor);
        }
    }
    
    /**
     * 更新错误列表
     * @param newErrorItems 新的错误列表
     */
    public void updateErrorItems(List<PronunciationErrorItem> newErrorItems) {
        this.errorItems = newErrorItems;
        notifyDataSetChanged();
    }
    
    /**
     * 获取指定严重程度的错误数量
     * @param severityLevel 严重程度 (0-轻微, 1-中等, 2-严重)
     * @return 错误数量
     */
    public int getErrorCountBySeverity(int severityLevel) {
        if (errorItems == null) return 0;
        
        int count = 0;
        for (PronunciationErrorItem item : errorItems) {
            if (item.getSeverityLevel() == severityLevel) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * 获取连读错误数量
     * @return 连读错误数量
     */
    public int getLiaisonErrorCount() {
        if (errorItems == null) return 0;
        
        int count = 0;
        for (PronunciationErrorItem item : errorItems) {
            if (item.isLiaisonError()) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * 获取弱读错误数量
     * @return 弱读错误数量
     */
    public int getWeakFormErrorCount() {
        if (errorItems == null) return 0;
        
        int count = 0;
        for (PronunciationErrorItem item : errorItems) {
            if (item.isWeakFormError()) {
                count++;
            }
        }
        return count;
    }
}