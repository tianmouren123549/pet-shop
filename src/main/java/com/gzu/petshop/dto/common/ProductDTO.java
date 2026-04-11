package com.gzu.petshop.dto.common;

import java.math.BigDecimal;
import java.util.Map;

public class ProductDTO {
    private Long productId;
    /** 与表 {@code product.merchant_id}、前端 {@code product.merchantId} 一致。 */
    private Long merchantId;
    private String title;
    private Long categoryId;
    private String categoryName;
    private Long brandId;
    private String brandName;
    private BigDecimal price;
    private Integer stock;
    /** 与表 {@code product.status}、前端上下架判断一致：1 上架，0 下架。 */
    private Integer status;
    private ProductDetailDTO detail;

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

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Long getBrandId() {
        return brandId;
    }

    public void setBrandId(Long brandId) {
        this.brandId = brandId;
    }

    public String getBrandName() {
        return brandName;
    }

    public void setBrandName(String brandName) {
        this.brandName = brandName;
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

    public ProductDetailDTO getDetail() {
        return detail;
    }

    public void setDetail(ProductDetailDTO detail) {
        this.detail = detail;
    }

    public static class ProductDetailDTO {
        private String description;
        private Map<String, Object> specJson;
        private String imageUrl;

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Map<String, Object> getSpecJson() {
            return specJson;
        }

        public void setSpecJson(Map<String, Object> specJson) {
            this.specJson = specJson;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public void setImageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
        }
    }
}
