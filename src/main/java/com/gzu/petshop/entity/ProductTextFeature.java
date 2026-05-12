package com.gzu.petshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品语义/评论聚合特征（与 {@code product_text_feature} 表一致，供运营分析与后续推荐扩展）。
 */
@TableName("product_text_feature")
public class ProductTextFeature {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("product_id")
    private Long productId;

    @TableField("model_version")
    private String modelVersion;

    @TableField("embed_json")
    private String embedJson;

    @TableField("pca_json")
    private String pcaJson;

    @TableField("review_cnt")
    private Integer reviewCnt;

    @TableField("avg_rating")
    private BigDecimal avgRating;

    @TableField("sentiment_pos_rate")
    private BigDecimal sentimentPosRate;

    @TableField("sentiment_neu_rate")
    private BigDecimal sentimentNeuRate;

    @TableField("sentiment_neg_rate")
    private BigDecimal sentimentNegRate;

    @TableField("intent_feed_rate")
    private BigDecimal intentFeedRate;

    @TableField("intent_product_consult_rate")
    private BigDecimal intentProductConsultRate;

    @TableField("intent_price_rate")
    private BigDecimal intentPriceRate;

    @TableField("intent_logistics_rate")
    private BigDecimal intentLogisticsRate;

    @TableField("intent_after_sale_rate")
    private BigDecimal intentAfterSaleRate;

    @TableField("intent_feedback_rate")
    private BigDecimal intentFeedbackRate;

    @TableField("feature_window_days")
    private Integer featureWindowDays;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String getEmbedJson() {
        return embedJson;
    }

    public void setEmbedJson(String embedJson) {
        this.embedJson = embedJson;
    }

    public String getPcaJson() {
        return pcaJson;
    }

    public void setPcaJson(String pcaJson) {
        this.pcaJson = pcaJson;
    }

    public Integer getReviewCnt() {
        return reviewCnt;
    }

    public void setReviewCnt(Integer reviewCnt) {
        this.reviewCnt = reviewCnt;
    }

    public BigDecimal getAvgRating() {
        return avgRating;
    }

    public void setAvgRating(BigDecimal avgRating) {
        this.avgRating = avgRating;
    }

    public BigDecimal getSentimentPosRate() {
        return sentimentPosRate;
    }

    public void setSentimentPosRate(BigDecimal sentimentPosRate) {
        this.sentimentPosRate = sentimentPosRate;
    }

    public BigDecimal getSentimentNeuRate() {
        return sentimentNeuRate;
    }

    public void setSentimentNeuRate(BigDecimal sentimentNeuRate) {
        this.sentimentNeuRate = sentimentNeuRate;
    }

    public BigDecimal getSentimentNegRate() {
        return sentimentNegRate;
    }

    public void setSentimentNegRate(BigDecimal sentimentNegRate) {
        this.sentimentNegRate = sentimentNegRate;
    }

    public BigDecimal getIntentFeedRate() {
        return intentFeedRate;
    }

    public void setIntentFeedRate(BigDecimal intentFeedRate) {
        this.intentFeedRate = intentFeedRate;
    }

    public BigDecimal getIntentProductConsultRate() {
        return intentProductConsultRate;
    }

    public void setIntentProductConsultRate(BigDecimal intentProductConsultRate) {
        this.intentProductConsultRate = intentProductConsultRate;
    }

    public BigDecimal getIntentPriceRate() {
        return intentPriceRate;
    }

    public void setIntentPriceRate(BigDecimal intentPriceRate) {
        this.intentPriceRate = intentPriceRate;
    }

    public BigDecimal getIntentLogisticsRate() {
        return intentLogisticsRate;
    }

    public void setIntentLogisticsRate(BigDecimal intentLogisticsRate) {
        this.intentLogisticsRate = intentLogisticsRate;
    }

    public BigDecimal getIntentAfterSaleRate() {
        return intentAfterSaleRate;
    }

    public void setIntentAfterSaleRate(BigDecimal intentAfterSaleRate) {
        this.intentAfterSaleRate = intentAfterSaleRate;
    }

    public BigDecimal getIntentFeedbackRate() {
        return intentFeedbackRate;
    }

    public void setIntentFeedbackRate(BigDecimal intentFeedbackRate) {
        this.intentFeedbackRate = intentFeedbackRate;
    }

    public Integer getFeatureWindowDays() {
        return featureWindowDays;
    }

    public void setFeatureWindowDays(Integer featureWindowDays) {
        this.featureWindowDays = featureWindowDays;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
