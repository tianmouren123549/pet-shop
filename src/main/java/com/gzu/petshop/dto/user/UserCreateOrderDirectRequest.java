package com.gzu.petshop.dto.user;

/**
 * 立即购买下单请求（与前端 {@code userCreateOrderDirect} 一致）。
 */
public class UserCreateOrderDirectRequest {
    private Long userId;
    private Long productId;
    private Integer quantity;

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

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
