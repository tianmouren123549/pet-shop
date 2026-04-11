package com.gzu.petshop.dto.common;

/**
 * 到货提醒订阅（与前端 {@code userSubscribeRestock} 一致）。
 */
public class RestockSubscribeRequest {
    private Long userId;
    private Long productId;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }
}
