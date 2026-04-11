package com.gzu.petshop.dto.common;

/**
 * 用户发起与商家的会话（与 {@code userGetMerchantSession} 一致）。
 */
public class MerchantSessionRequest {
    private Long userId;
    private Long merchantId;
    private Long orderId;

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
}
