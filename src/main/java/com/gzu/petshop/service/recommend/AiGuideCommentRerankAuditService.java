package com.gzu.petshop.service.recommend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzu.petshop.dto.user.RecommendationItemDTO;
import com.gzu.petshop.entity.AiGuideCommentRerankRun;
import com.gzu.petshop.entity.AiGuideCommentRerankScore;
import com.gzu.petshop.mapper.recommend.AiGuideCommentRerankRunMapper;
import com.gzu.petshop.mapper.recommend.AiGuideCommentRerankScoreMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 评论强推（BERT+XGB 融合）成功后的打分明细落库；失败仅打日志，不影响接口返回。
 */
@Service
public class AiGuideCommentRerankAuditService {

    private static final Logger log = LoggerFactory.getLogger(AiGuideCommentRerankAuditService.class);
    private static final int MODEL_VERSION_MAX = 255;

    private final AiGuideCommentRerankRunMapper runMapper;
    private final AiGuideCommentRerankScoreMapper scoreMapper;
    private final ObjectMapper objectMapper;

    public AiGuideCommentRerankAuditService(
            AiGuideCommentRerankRunMapper runMapper,
            AiGuideCommentRerankScoreMapper scoreMapper,
            ObjectMapper objectMapper
    ) {
        this.runMapper = runMapper;
        this.scoreMapper = scoreMapper;
        this.objectMapper = objectMapper;
    }

    public void recordSuccessfulRerank(
            long userId,
            Long sessionId,
            List<Long> inputProductIdsOrder,
            BigDecimal weightXgb,
            BigDecimal weightBert,
            String bertModelVersion,
            List<RecommendationItemDTO> items,
            Map<Long, Integer> inputOrderByProductId
    ) {
        if (items == null || items.isEmpty()) {
            return;
        }
        try {
            AiGuideCommentRerankRun run = new AiGuideCommentRerankRun();
            run.setUserId(userId);
            run.setSessionId(sessionId != null && sessionId > 0 ? sessionId : null);
            run.setProductCount(items.size());
            run.setWeightXgb(weightXgb);
            run.setWeightBert(weightBert);
            run.setBertModelVersion(truncate(bertModelVersion, 128));
            if (inputProductIdsOrder != null && !inputProductIdsOrder.isEmpty()) {
                run.setInputProductIdsJson(objectMapper.writeValueAsString(inputProductIdsOrder));
            }
            run.setCreatedAt(LocalDateTime.now());
            runMapper.insert(run);
            Long runId = run.getRunId();
            if (runId == null || runId <= 0) {
                log.warn("comment rerank audit: run insert returned no run_id");
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            for (RecommendationItemDTO it : items) {
                if (it == null || it.getProductId() == null || it.getProductId() <= 0) {
                    continue;
                }
                AiGuideCommentRerankScore row = new AiGuideCommentRerankScore();
                row.setRunId(runId);
                row.setProductId(it.getProductId());
                if (inputOrderByProductId != null) {
                    row.setInputPosition(inputOrderByProductId.get(it.getProductId()));
                }
                row.setRankNo(it.getRankNo() != null ? it.getRankNo() : 0);
                row.setFusionScore(it.getScore());
                row.setBertScore(it.getReviewModelScore());
                row.setXgbNorm(it.getXgbPersonalizedScore());
                row.setAvgReviewRating(it.getAvgReviewRating());
                row.setReviewCount(it.getReviewCount());
                row.setFusionTopBadge(Boolean.TRUE.equals(it.getCommentStrongRecommend()));
                row.setModelVersion(truncate(it.getModelVersion(), MODEL_VERSION_MAX));
                row.setCreatedAt(now);
                scoreMapper.insert(row);
            }
        } catch (Exception e) {
            log.warn(
                    "comment rerank audit insert failed (请执行 migrate_ai_guide_comment_rerank_audit.sql 或检查库): {}",
                    e.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }
}
