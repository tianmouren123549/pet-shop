package com.gzu.petshop.dto.user;

import java.math.BigDecimal;

/**
 * 常购清单单行（含商品摘要）。
 */
public class FrequentProductItemDTO {
    private Long productId;
    private String title;
    private String imageUrl;
    private Integer buyCount;
    private String lastBoughtAt;
    private BigDecimal price;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
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

    public Integer getBuyCount() {
        return buyCount;
    }

    public void setBuyCount(Integer buyCount) {
        this.buyCount = buyCount;
    }

    public String getLastBoughtAt() {
        return lastBoughtAt;
    }

    public void setLastBoughtAt(String lastBoughtAt) {
        this.lastBoughtAt = lastBoughtAt;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}
