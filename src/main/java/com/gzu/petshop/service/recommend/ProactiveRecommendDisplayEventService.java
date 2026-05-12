package com.gzu.petshop.service.recommend;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzu.petshop.dto.user.RecommendationItemDTO;
import com.gzu.petshop.entity.ProactiveRecommendDisplayEvent;
import com.gzu.petshop.entity.ProactiveRecommendSession;
import com.gzu.petshop.mapper.recommend.ProactiveRecommendDisplayEventMapper;
import com.gzu.petshop.mapper.recommend.ProactiveRecommendSessionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 智能导购「当前展示候选」流水写库；失败仅打日志，不影响主对话事务提交。
 */
@Service
public class ProactiveRecommendDisplayEventService {

    private static final Logger log = LoggerFactory.getLogger(ProactiveRecommendDisplayEventService.class);

    public static final String EVENT_ASSISTANT_HIGHLIGHT = "ASSISTANT_HIGHLIGHT";
    public static final String EVENT_COMMENT_RERANK = "COMMENT_RERANK";

    private final ProactiveRecommendDisplayEventMapper displayEventMapper;
    private final ProactiveRecommendSessionMapper sessionMapper;
    private final ObjectMapper objectMapper;

    public ProactiveRecommendDisplayEventService(
            ProactiveRecommendDisplayEventMapper displayEventMapper,
            ProactiveRecommendSessionMapper sessionMapper,
            ObjectMapper objectMapper
    ) {
        this.displayEventMapper = displayEventMapper;
        this.sessionMapper = sessionMapper;
        this.objectMapper = objectMapper;
    }

    /** 豆包/模板助手一轮结束后，记录本轮展示的商品顺序（与 proactive_recommend_message 中助手 payload 一致）。 */
    public void recordAssistantHighlight(long sessionId, long userId, Long messageId, List<RecommendationItemDTO> shown) {
        try {
            if (sessionId <= 0 || userId <= 0) {
                return;
            }
            if (!sessionOwnedByUser(sessionId, userId)) {
                log.warn("proactive display_event skip: session {} not owned by user {}", sessionId, userId);
                return;
            }
            List<Long> ids = productIdsInOrder(shown);
            ProactiveRecommendDisplayEvent row = new ProactiveRecommendDisplayEvent();
            row.setSessionId(sessionId);
            row.setUserId(userId);
            row.setEventType(EVENT_ASSISTANT_HIGHLIGHT);
            row.setMessageId(messageId);
            row.setProductIdsJson(objectMapper.writeValueAsString(ids));
            row.setInputProductIdsJson(null);
            row.setScoresJson(buildScoresJson(shown));
            row.setCreatedAt(LocalDateTime.now());
            displayEventMapper.insert(row);
        } catch (Exception e) {
            log.warn("proactive display_event ASSISTANT_HIGHLIGHT insert failed (表未迁移或库异常): {}", e.getMessage());
        }
    }

    /** 评论强推成功后，记录重排后的展示顺序（通常 1 条）。 */
    public void recordCommentRerank(long sessionId, long userId, List<Long> inputProductIds, List<RecommendationItemDTO> out) {
        try {
            if (sessionId <= 0 || userId <= 0) {
                return;
            }
            if (!sessionOwnedByUser(sessionId, userId)) {
                log.warn("proactive display_event skip rerank: session {} not owned by user {}", sessionId, userId);
                return;
            }
            ProactiveRecommendDisplayEvent row = new ProactiveRecommendDisplayEvent();
            row.setSessionId(sessionId);
            row.setUserId(userId);
            row.setEventType(EVENT_COMMENT_RERANK);
            row.setMessageId(null);
            row.setProductIdsJson(objectMapper.writeValueAsString(productIdsInOrder(out)));
            row.setInputProductIdsJson(
                    inputProductIds == null || inputProductIds.isEmpty()
                            ? null
                            : objectMapper.writeValueAsString(inputProductIds));
            row.setScoresJson(buildScoresJson(out));
            row.setCreatedAt(LocalDateTime.now());
            displayEventMapper.insert(row);
        } catch (Exception e) {
            log.warn("proactive display_event COMMENT_RERANK insert failed (表未迁移或库异常): {}", e.getMessage());
        }
    }

    /** 本会话下最新一条展示事件里的商品 ID 顺序；无表或无记录时返回空列表。 */
    public List<Long> loadLatestProductIdsForSession(long sessionId) {
        if (sessionId <= 0) {
            return List.of();
        }
        try {
            ProactiveRecommendDisplayEvent ev =
                    displayEventMapper.selectOne(
                            new QueryWrapper<ProactiveRecommendDisplayEvent>()
                                    .eq("session_id", sessionId)
                                    .orderByDesc("created_at")
                                    .last("LIMIT 1"));
            if (ev == null || ev.getProductIdsJson() == null || ev.getProductIdsJson().isBlank()) {
                return List.of();
            }
            return parseIdArrayJson(ev.getProductIdsJson());
        } catch (Exception e) {
            log.warn("proactive display_event read failed: {}", e.getMessage());
            return List.of();
        }
    }

    private boolean sessionOwnedByUser(long sessionId, long userId) {
        ProactiveRecommendSession s = sessionMapper.selectById(sessionId);
        return s != null && Objects.equals(s.getUserId(), userId);
    }

    private static List<Long> productIdsInOrder(List<RecommendationItemDTO> shown) {
        List<Long> ids = new ArrayList<>();
        if (shown == null) {
            return ids;
        }
        for (RecommendationItemDTO it : shown) {
            if (it != null && it.getProductId() != null && it.getProductId() > 0) {
                ids.add(it.getProductId());
            }
        }
        return ids;
    }

    private List<Long> parseIdArrayJson(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        if (!root.isArray()) {
            return List.of();
        }
        List<Long> out = new ArrayList<>();
        for (JsonNode n : root) {
            if (n != null && n.isNumber()) {
                long v = n.longValue();
                if (v > 0) {
                    out.add(v);
                }
            }
        }
        return out;
    }

    private String buildScoresJson(List<RecommendationItemDTO> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        try {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (RecommendationItemDTO it : items) {
                if (it == null || it.getProductId() == null) {
                    continue;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("productId", it.getProductId());
                if (it.getScore() != null) {
                    m.put("fusion", it.getScore());
                }
                if (it.getReviewModelScore() != null) {
                    m.put("bert", it.getReviewModelScore());
                }
                if (it.getXgbPersonalizedScore() != null) {
                    m.put("xgbNorm", it.getXgbPersonalizedScore());
                }
                rows.add(m);
            }
            if (rows.isEmpty()) {
                return null;
            }
            return objectMapper.writeValueAsString(rows);
        } catch (Exception e) {
            return null;
        }
    }
}
