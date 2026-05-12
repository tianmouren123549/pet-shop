package com.gzu.petshop.dto.recommend;

import com.gzu.petshop.dto.user.RecommendationItemDTO;

import java.util.List;

/**
 * 恢复用户最近一次主动推荐会话：历史气泡 + 当前轮次展示用候选（来自末条助手消息的 payload）。
 */
public class ProactiveSessionRestoreDTO {
    private Long sessionId;
    private List<ProactiveChatMessageDTO> messages;
    private List<RecommendationItemDTO> candidateProducts;

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public List<ProactiveChatMessageDTO> getMessages() {
        return messages;
    }

    public void setMessages(List<ProactiveChatMessageDTO> messages) {
        this.messages = messages;
    }

    public List<RecommendationItemDTO> getCandidateProducts() {
        return candidateProducts;
    }

    public void setCandidateProducts(List<RecommendationItemDTO> candidateProducts) {
        this.candidateProducts = candidateProducts;
    }
}
