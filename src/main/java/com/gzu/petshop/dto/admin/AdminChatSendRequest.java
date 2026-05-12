package com.gzu.petshop.dto.admin;

/**
 * 管理员回复用户/商家咨询。
 */
public class AdminChatSendRequest {
    private Long sessionId;
    private String content;

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
