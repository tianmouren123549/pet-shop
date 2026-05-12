package com.gzu.petshop.dto.recommend;

import com.gzu.petshop.dto.user.RecommendationItemDTO;

import java.util.List;

/**
 * 智能导购「评论强推」：须成功调用评论 BERT 服务；失败时 {@link #ok()} 为 false。
 */
public class AiGuideRerankOutcome {

    private final boolean ok;
    private final int errorCode;
    private final String message;
    private final List<RecommendationItemDTO> items;

    private AiGuideRerankOutcome(boolean ok, int errorCode, String message, List<RecommendationItemDTO> items) {
        this.ok = ok;
        this.errorCode = errorCode;
        this.message = message;
        this.items = items;
    }

    public static AiGuideRerankOutcome success(List<RecommendationItemDTO> items) {
        return new AiGuideRerankOutcome(true, 200, null, items);
    }

    public static AiGuideRerankOutcome fail(int errorCode, String message) {
        return new AiGuideRerankOutcome(false, errorCode, message, List.of());
    }

    public boolean isOk() {
        return ok;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getMessage() {
        return message;
    }

    public List<RecommendationItemDTO> getItems() {
        return items;
    }
}
