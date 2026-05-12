package com.gzu.petshop.dto.user;

import com.gzu.petshop.dto.common.ProductDTO;

import java.math.BigDecimal;

public class RecommendationItemDTO {
    private Long productId;
    private BigDecimal score;
    private Integer rankNo;
    private String modelVersion;
    private ProductDTO product;
    /** 是否满足「评论强推」阈值（高均分 + 足够条数），与排序加权一致。 */
    private Boolean commentStrongRecommend;
    /** 展示用：评论均分 */
    private BigDecimal avgReviewRating;
    /** 展示用：评论条数 */
    private Integer reviewCount;
    /** 智能导购融合：评论 BERT 聚合分（约 0~1） */
    private BigDecimal reviewModelScore;
    /** 智能导购融合：个性化 XGB 分在本批内的 min-max 归一化（0~1） */
    private BigDecimal xgbPersonalizedScore;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public Integer getRankNo() {
        return rankNo;
    }

    public void setRankNo(Integer rankNo) {
        this.rankNo = rankNo;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public ProductDTO getProduct() {
        return product;
    }

    public void setProduct(ProductDTO product) {
        this.product = product;
    }

    public Boolean getCommentStrongRecommend() {
        return commentStrongRecommend;
    }

    public void setCommentStrongRecommend(Boolean commentStrongRecommend) {
        this.commentStrongRecommend = commentStrongRecommend;
    }

    public BigDecimal getAvgReviewRating() {
        return avgReviewRating;
    }

    public void setAvgReviewRating(BigDecimal avgReviewRating) {
        this.avgReviewRating = avgReviewRating;
    }

    public Integer getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(Integer reviewCount) {
        this.reviewCount = reviewCount;
    }

    public BigDecimal getReviewModelScore() {
        return reviewModelScore;
    }

    public void setReviewModelScore(BigDecimal reviewModelScore) {
        this.reviewModelScore = reviewModelScore;
    }

    public BigDecimal getXgbPersonalizedScore() {
        return xgbPersonalizedScore;
    }

    public void setXgbPersonalizedScore(BigDecimal xgbPersonalizedScore) {
        this.xgbPersonalizedScore = xgbPersonalizedScore;
    }
}
