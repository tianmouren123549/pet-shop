package com.gzu.petshop.dto.recommend;

import java.math.BigDecimal;

/**
 * 商品维度评论聚合（用于推荐侧「评论强推」加权与展示）。
 */
public class ProductReviewStatsDTO {

    private Long productId;
    /** 平均星级，1～5 */
    private BigDecimal avgRating;
    /** 有效评论条数 */
    private Integer reviewCount;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public BigDecimal getAvgRating() {
        return avgRating;
    }

    public void setAvgRating(BigDecimal avgRating) {
        this.avgRating = avgRating;
    }

    public Integer getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(Integer reviewCount) {
        this.reviewCount = reviewCount;
    }
}
