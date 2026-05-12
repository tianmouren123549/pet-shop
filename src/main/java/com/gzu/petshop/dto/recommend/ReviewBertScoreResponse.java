package com.gzu.petshop.dto.recommend;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** 与 {@code tools/review_bert_score_server.py} 的响应一致。 */
public class ReviewBertScoreResponse {

    @JsonProperty("model_version")
    private String modelVersion;

    private List<ScoreEntry> scores;

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public List<ScoreEntry> getScores() {
        return scores;
    }

    public void setScores(List<ScoreEntry> scores) {
        this.scores = scores;
    }

    public static class ScoreEntry {
        @JsonProperty("product_id")
        private long productId;

        /** 聚合后的评论情感分，约 0~1，越高越偏好评。 */
        private double score;

        @JsonProperty("review_count_used")
        private int reviewCountUsed;

        public long getProductId() {
            return productId;
        }

        public void setProductId(long productId) {
            this.productId = productId;
        }

        public double getScore() {
            return score;
        }

        public void setScore(double score) {
            this.score = score;
        }

        public int getReviewCountUsed() {
            return reviewCountUsed;
        }

        public void setReviewCountUsed(int reviewCountUsed) {
            this.reviewCountUsed = reviewCountUsed;
        }
    }
}
