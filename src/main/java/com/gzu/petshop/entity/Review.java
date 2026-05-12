package com.gzu.petshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("review")
public class Review {
    @TableId(value = "review_id", type = IdType.AUTO)
    private Long reviewId;

    @TableField("user_id")
    private Long userId;

    @TableField("product_id")
    private Long productId;
    private Integer rating;

    private String content;

    @TableField("golden_retriever_score")
    private BigDecimal goldenRetrieverScore;

    @TableField("sentiment_label")
    private String sentimentLabel;

    @TableField("intent_label")
    private String intentLabel;

    @TableField("bert_version")
    private String bertVersion;

    @TableField("analyzed_at")
    private LocalDateTime analyzedAt;

    private Integer status;

    @TableField("created_at")
    private LocalDateTime createdAt;

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

    public String getSentimentLabel() {
        return sentimentLabel;
    }

    public void setSentimentLabel(String sentimentLabel) {
        this.sentimentLabel = sentimentLabel;
    }

    public String getIntentLabel() {
        return intentLabel;
    }

    public void setIntentLabel(String intentLabel) {
        this.intentLabel = intentLabel;
    }

    public String getBertVersion() {
        return bertVersion;
    }

    public void setBertVersion(String bertVersion) {
        this.bertVersion = bertVersion;
    }

    public LocalDateTime getAnalyzedAt() {
        return analyzedAt;
    }

    public void setAnalyzedAt(LocalDateTime analyzedAt) {
        this.analyzedAt = analyzedAt;
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
}
