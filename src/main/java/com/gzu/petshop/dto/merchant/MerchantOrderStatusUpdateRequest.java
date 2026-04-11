package com.gzu.petshop.dto.merchant;

/**
 * 商家更新订单状态请求体（与前端 {@code putJson(..., { merchantId, status })} 一致）。
 */
public class MerchantOrderStatusUpdateRequest {
    private Long merchantId;
    private String status;

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
