package com.gzu.petshop.dto.recommend;

/**
 * 主动推荐对话一轮：无 {@code sessionId} 时须传非空 {@code userMessage}，首条用户消息创建会话并得到助手回复；有 {@code sessionId} 时为追问。
 */
public class ProactiveTurnRequest {
    private Long sessionId;
    private String userMessage;

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }
}
