package com.gzu.petshop.dto.merchant;

/**
 * 商家端订单详情中的本店明细行。
 */
public class MerchantOrderLineDTO {
    private Long productId;
    /** 展示用 SKU（无独立 SKU 表时为 PW-00001 形式） */
    private String skuCode;
    private String title;
    private String imageUrl;
    private Integer quantity;
    /** 单价，两位小数字符串 */
    private String unitPrice;
    /** 小计，两位小数字符串 */
    private String subtotal;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getSkuCode() {
        return skuCode;
    }

    public void setSkuCode(String skuCode) {
        this.skuCode = skuCode;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(String unitPrice) {
        this.unitPrice = unitPrice;
    }

    public String getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(String subtotal) {
        this.subtotal = subtotal;
    }
}
