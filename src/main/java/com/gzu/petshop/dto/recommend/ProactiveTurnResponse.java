package com.gzu.petshop.dto.recommend;

import com.gzu.petshop.dto.user.RecommendationItemDTO;

import java.util.List;

public class ProactiveTurnResponse {
    private Long sessionId;
    private String assistantMessage;
    private List<RecommendationItemDTO> candidateProducts;
    private boolean done;
    /**
     * 本轮助手正文来源：{@code DOUBAO} 火山豆包；{@code CUSTOM} 自建编排；{@code TEMPLATE} 内置话术。
     */
    private String replySource;

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getAssistantMessage() {
        return assistantMessage;
    }

    public void setAssistantMessage(String assistantMessage) {
        this.assistantMessage = assistantMessage;
    }

    public List<RecommendationItemDTO> getCandidateProducts() {
        return candidateProducts;
    }

    public void setCandidateProducts(List<RecommendationItemDTO> candidateProducts) {
        this.candidateProducts = candidateProducts;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    public String getReplySource() {
        return replySource;
    }

    public void setReplySource(String replySource) {
        this.replySource = replySource;
    }
}
