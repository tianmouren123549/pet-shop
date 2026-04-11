package com.gzu.petshop.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理端商品列表行：表 {@code product} 字段 + 商家展示信息（对齐前端 Mock 的 merchantShopName 等）。
 */
public class AdminProductListItemDTO {
    private Long productId;
    private Long merchantId;
    private String merchantShopName;
    private String merchantContactName;
    private String title;
    private Long categoryId;
    private Long brandId;
    private BigDecimal price;
    private Integer stock;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public String getMerchantShopName() {
        return merchantShopName;
    }

    public void setMerchantShopName(String merchantShopName) {
        this.merchantShopName = merchantShopName;
    }

    public String getMerchantContactName() {
        return merchantContactName;
    }

    public void setMerchantContactName(String merchantContactName) {
        this.merchantContactName = merchantContactName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Long getBrandId() {
        return brandId;
    }

    public void setBrandId(Long brandId) {
        this.brandId = brandId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
