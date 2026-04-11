package com.gzu.petshop.dto.common;

/**
 * 商家发送聊天消息。
 */
public class ChatSendMerchantRequest {
    private Long sessionId;
    private Long merchantId;
    private String content;

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
