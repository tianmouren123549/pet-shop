package com.gzu.petshop.dto.merchant;

/**
 * 商家更新商品详情（纯 JSON），与 {@code multipart/form-data} 版接口二选一。
 * 可含 {@code imageUrl}（外链或 data URL），与 multipart 的 {@code imageUrl} 表单字段一致。
 */
public class MerchantProductContentJsonRequest {
    private String description;
    private String specJson;
    /** 主图 URL 或 data URL，与 multipart 的 {@code imageUrl} 参数一致 */
    private String imageUrl;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSpecJson() {
        return specJson;
    }

    public void setSpecJson(String specJson) {
        this.specJson = specJson;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
