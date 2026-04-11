package com.gzu.petshop.dto.common;

import java.math.BigDecimal;

/**
 * 商品评价展示（表 {@code review} + 用户昵称，供商品详情页展示）。
 */
public class ReviewViewDTO {
    private Long reviewId;
    private Long userId;
    /** 来自 {@code user.nickname}，无则前端可回退为「用户」+ userId */
    private String userNickname;
    private Long productId;
    private Integer rating;
    private String content;
    private BigDecimal goldenRetrieverScore;
    private String createdAt;

    public Long getReviewId() {
        return reviewId;
    }

    public void setReviewId(Long reviewId) {
        this.reviewId = reviewId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserNickname() {
        return userNickname;
    }

    public void setUserNickname(String userNickname) {
        this.userNickname = userNickname;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public BigDecimal getGoldenRetrieverScore() {
        return goldenRetrieverScore;
    }

    public void setGoldenRetrieverScore(BigDecimal goldenRetrieverScore) {
        this.goldenRetrieverScore = goldenRetrieverScore;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
