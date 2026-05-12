package com.gzu.petshop.dto.recommend;

import com.gzu.petshop.dto.user.RecommendationItemDTO;

import java.util.List;

/**
 * AI 解析 + XGB/离线推荐流水线结果。
 */
public class NlRecommendResponse {

    /** 豆包解析的结构化结果（或规则兜底） */
    private NlBridgeInterpretation interpretation;

    /** 与 {@link com.gzu.petshop.service.recommend.RecommendationService#listByUser} 同源打分后，再按解析结果过滤 */
    private List<RecommendationItemDTO> candidateProducts;

    /** DOUBAO | HEURISTIC */
    private String replySource;

    /**
     * 本次推荐排序来源：{@code ONLINE_XGB} 为直连在线推理服务；{@code OFFLINE_TABLE} 为读库离线表（或在线服务不可用时的回退）。
     */
    private String rankingBackend;

    public NlBridgeInterpretation getInterpretation() {
        return interpretation;
    }

    public void setInterpretation(NlBridgeInterpretation interpretation) {
        this.interpretation = interpretation;
    }

    public List<RecommendationItemDTO> getCandidateProducts() {
        return candidateProducts;
    }

    public void setCandidateProducts(List<RecommendationItemDTO> candidateProducts) {
        this.candidateProducts = candidateProducts;
    }

    public String getReplySource() {
        return replySource;
    }

    public void setReplySource(String replySource) {
        this.replySource = replySource;
    }

    public String getRankingBackend() {
        return rankingBackend;
    }

    public void setRankingBackend(String rankingBackend) {
        this.rankingBackend = rankingBackend;
    }
}
