package com.gzu.petshop.dto.recommend;

import java.time.LocalDateTime;

/** 智能导购会话列表项（侧栏/下拉用） */
public class ProactiveSessionSummaryDTO {
    private Long sessionId;
    /** 展示用会话名（首条用户提问）；可能为空串，前端可回退为「会话#id」 */
    private String title;
    private LocalDateTime updatedAt;

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
