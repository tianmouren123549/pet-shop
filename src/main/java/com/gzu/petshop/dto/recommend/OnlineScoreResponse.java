package com.gzu.petshop.dto.recommend;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 与 Python {@code online_score_server.py} 的 POST /v1/score 响应体一致。
 */
public class OnlineScoreResponse {

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

        private double score;

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
    }
}
