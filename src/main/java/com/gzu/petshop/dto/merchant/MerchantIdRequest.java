package com.gzu.petshop.dto.merchant;

/**
 * 商家身份请求体（与前端 {@code { merchantId }} 一致）。
 */
public class MerchantIdRequest {
    private Long merchantId;

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }
}
