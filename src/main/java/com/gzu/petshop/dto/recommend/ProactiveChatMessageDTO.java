package com.gzu.petshop.dto.recommend;

/**
 * 智能导购多轮对话中的一条消息（与前端 {@code role: 'user'|'assistant'} 对齐）。
 */
public class ProactiveChatMessageDTO {
    private String role;
    private String content;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
