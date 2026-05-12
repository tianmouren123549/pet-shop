package com.gzu.petshop.dto.recommend;

import java.util.ArrayList;
import java.util.List;

/**
 * 智能导购：在 AI 已给出的候选商品 id 集合内，按站内 XGB（在线/离线，训练侧含 BERT 评论特征）与评论、行为乘子重排。
 */
public class AiGuideCommentRerankRequest {

    /** 来自当前轮 AI 候选，顺序保留用于同分稳定排序；至多取前 24 个有效 id */
    private List<Long> productIds = new ArrayList<>();
    /** 可选：多轮导购会话 id，评论强推成功后会写入 proactive_recommend_display_event */
    private Long sessionId;

    public List<Long> getProductIds() {
        return productIds;
    }

    public void setProductIds(List<Long> productIds) {
        this.productIds = productIds != null ? productIds : new ArrayList<>();
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }
}
