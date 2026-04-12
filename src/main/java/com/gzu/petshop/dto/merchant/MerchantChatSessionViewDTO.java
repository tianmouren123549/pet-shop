package com.gzu.petshop.dto.merchant;

/**
 * 商家端会话列表行（含用户昵称、关联订单与主商品图文，便于识别咨询上下文）。
 */
public class MerchantChatSessionViewDTO {
    private Long sessionId;
    private Long userId;
    private Long merchantId;
    private Long orderId;
    private String sessionType;
    private String status;
    private String createdAt;
    private String updatedAt;
    private String userNickname;
    /** 关联订单号；无关联订单时为空 */
    private String orderNo;
    /** 本店在该订单中的首件商品标题（或说明文案） */
    private String productTitle;
    /** 主图 URL */
    private String productImageUrl;
    /** 该订单中属于本店的明细行数 */
    private Integer relatedLineCount;
    /** 本会话是否有用户发送且商家尚未打开消息列表读过的消息 */
    private boolean unreadFromUser;

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

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getSessionType() {
        return sessionType;
    }

    public void setSessionType(String sessionType) {
        this.sessionType = sessionType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUserNickname() {
        return userNickname;
    }

    public void setUserNickname(String userNickname) {
        this.userNickname = userNickname;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getProductTitle() {
        return productTitle;
    }

    public void setProductTitle(String productTitle) {
        this.productTitle = productTitle;
    }

    public String getProductImageUrl() {
        return productImageUrl;
    }

    public void setProductImageUrl(String productImageUrl) {
        this.productImageUrl = productImageUrl;
    }

    public Integer getRelatedLineCount() {
        return relatedLineCount;
    }

    public void setRelatedLineCount(Integer relatedLineCount) {
        this.relatedLineCount = relatedLineCount;
    }

    public boolean isUnreadFromUser() {
        return unreadFromUser;
    }

    public void setUnreadFromUser(boolean unreadFromUser) {
        this.unreadFromUser = unreadFromUser;
    }
}
