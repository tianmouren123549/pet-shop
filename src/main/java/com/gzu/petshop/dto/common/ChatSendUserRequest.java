package com.gzu.petshop.dto.common;

/**
 * 用户发送聊天消息。
 */
public class ChatSendUserRequest {
    private Long sessionId;
    private Long userId;
    private String content;

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
