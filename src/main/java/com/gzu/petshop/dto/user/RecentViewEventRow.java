package com.gzu.petshop.dto.user;

/**
 * 与 {@code UserEventLogMapper#selectRecentViewLikeEvents} 结果列别名对应。
 */
public class RecentViewEventRow {
    private Long productId;
    private Long categoryId;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }
}
