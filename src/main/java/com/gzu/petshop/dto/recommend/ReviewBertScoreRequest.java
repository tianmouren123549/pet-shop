package com.gzu.petshop.dto.recommend;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * 请求评论情感 BERT 服务：按商品聚合待分析文本（来自 review 表）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReviewBertScoreRequest {

    private List<ProductReviewTexts> products = new ArrayList<>();

    public List<ProductReviewTexts> getProducts() {
        return products;
    }

    public void setProducts(List<ProductReviewTexts> products) {
        this.products = products != null ? products : new ArrayList<>();
    }

    public static class ProductReviewTexts {
        private long productId;
        private List<ReviewTextLine> reviews = new ArrayList<>();

        public long getProductId() {
            return productId;
        }

        public void setProductId(long productId) {
            this.productId = productId;
        }

        public List<ReviewTextLine> getReviews() {
            return reviews;
        }

        public void setReviews(List<ReviewTextLine> reviews) {
            this.reviews = reviews != null ? reviews : new ArrayList<>();
        }
    }

    public static class ReviewTextLine {
        private String text;

        public ReviewTextLine() {}

        public ReviewTextLine(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }
}
