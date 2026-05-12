package com.gzu.petshop.dto.recommend;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 与 Python {@code online_score_server.py} 的 POST /v1/score 请求体一致。
 */
public class OnlineScoreRequest {

    @JsonProperty("user_order_cnt")
    private int userOrderCnt;

    @JsonProperty("product_ids")
    private List<Long> productIds;

    public OnlineScoreRequest() {
    }

    public OnlineScoreRequest(int userOrderCnt, List<Long> productIds) {
        this.userOrderCnt = userOrderCnt;
        this.productIds = productIds;
    }

    public int getUserOrderCnt() {
        return userOrderCnt;
    }

    public void setUserOrderCnt(int userOrderCnt) {
        this.userOrderCnt = userOrderCnt;
    }

    public List<Long> getProductIds() {
        return productIds;
    }

    public void setProductIds(List<Long> productIds) {
        this.productIds = productIds;
    }
}
