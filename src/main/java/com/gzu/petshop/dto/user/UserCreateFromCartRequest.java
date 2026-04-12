package com.gzu.petshop.dto.user;

/**
 * 从购物车下单请求体：与前端 {@code { userId, merchantId? }} 一致。
 * {@code merchantId} 有值时仅结算该商家下的购物车行，其余行保留。
 */
public class UserCreateFromCartRequest {
    private Long userId;
    /** 可选；指定时只结算该 {@code product.merchant_id} 对应的行 */
    private Long merchantId;

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
}
