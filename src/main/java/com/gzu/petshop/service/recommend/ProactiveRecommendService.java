package com.gzu.petshop.service.recommend;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzu.petshop.dto.common.ProductDTO;
import com.gzu.petshop.dto.recommend.ProactiveChatMessageDTO;
import com.gzu.petshop.dto.recommend.ProactiveSessionRestoreDTO;
import com.gzu.petshop.dto.recommend.SessionIdTitleDTO;
import com.gzu.petshop.dto.recommend.SessionFirstUserMessageDTO;
import com.gzu.petshop.dto.recommend.ProactiveSessionSummaryDTO;
import com.gzu.petshop.dto.recommend.ProactiveTurnResponse;
import com.gzu.petshop.dto.user.FrequentProductItemDTO;
import com.gzu.petshop.dto.user.RecommendationItemDTO;
import com.gzu.petshop.dto.user.UserProfileDTO;
import com.gzu.petshop.entity.ProactiveRecommendMessage;
import com.gzu.petshop.entity.ProactiveRecommendSession;
import com.gzu.petshop.mapper.recommend.ProactiveRecommendMessageMapper;
import com.gzu.petshop.mapper.recommend.ProactiveRecommendSessionMapper;
import com.gzu.petshop.service.account.UserProfileService;
import com.gzu.petshop.service.product.ProductService;
import com.gzu.petshop.service.recommend.AiProactiveClient.ProactiveAiHttpBody;
import com.gzu.petshop.service.user.UserProductFrequencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 主动追问式推荐：会话落库，候选商品来自 {@link RecommendationService} 与常购统计；
 * 可选调用 {@link AiProactiveClient} 对接外部 LLM。
 */
@Service
public class ProactiveRecommendService {

    private static final Logger log = LoggerFactory.getLogger(ProactiveRecommendService.class);

    /** 豆包 highlight 与前端展示：至多 5 个；能精准时鼓励模型只填 1 个 */
    private static final int MAX_SHOWN_PRODUCTS = 5;
    /** 传给豆包的候选条数上限（与首页同源打分，豆包可从中自主 highlight） */
    private static final int PROACTIVE_POOL_CAP = 50;
    private static final String ROLE_USER = "USER";
    private static final String ROLE_ASSISTANT = "ASSISTANT";
    /** 会话标题落库长度（与列 proactive_recommend_session.title 一致） */
    private static final int SESSION_TITLE_MAX_LEN = 200;

    private final ProactiveRecommendSessionMapper sessionMapper;
    private final ProactiveRecommendMessageMapper messageMapper;
    private final RecommendationService recommendationService;
    private final UserProductFrequencyService userProductFrequencyService;
    private final UserProfileService userProfileService;
    private final AiProactiveClient aiProactiveClient;
    private final ProductService productService;
    private final ObjectMapper objectMapper;
    private final ProactiveRecommendDisplayEventService proactiveRecommendDisplayEventService;

    @Value("${app.ai.proactive-max-history:12}")
    private int proactiveMaxHistory;

    /** 豆包每轮可见的推荐候选池大小（默认 24，与首页同流水线；豆包用 highlightProductIds 自主收窄） */
    @Value("${app.ai.proactive-candidate-pool-size:24}")
    private int proactiveCandidatePoolSize;

    public ProactiveRecommendService(
            ProactiveRecommendSessionMapper sessionMapper,
            ProactiveRecommendMessageMapper messageMapper,
            RecommendationService recommendationService,
            UserProductFrequencyService userProductFrequencyService,
            UserProfileService userProfileService,
            AiProactiveClient aiProactiveClient,
            ProductService productService,
            ObjectMapper objectMapper,
            ProactiveRecommendDisplayEventService proactiveRecommendDisplayEventService
    ) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.recommendationService = recommendationService;
        this.userProductFrequencyService = userProductFrequencyService;
        this.userProfileService = userProfileService;
        this.aiProactiveClient = aiProactiveClient;
        this.productService = productService;
        this.objectMapper = objectMapper;
        this.proactiveRecommendDisplayEventService = proactiveRecommendDisplayEventService;
    }

    @Transactional
    public ProactiveTurnResponse turn(Long userId, Long sessionId, String userMessageRaw) {
        List<RecommendationItemDTO> baseCandidates = loadProactiveBaseCandidates(userId);
        UserProfileDTO profile = userProfileService.getProfile(userId);
        List<FrequentProductItemDTO> frequentTop = userProductFrequencyService.topByUser(userId, 5);

        String userMsg = userMessageRaw == null ? "" : userMessageRaw.trim();

        if (sessionId == null || sessionId <= 0) {
            if (userMsg.isEmpty()) {
                throw new IllegalArgumentException("请发送首条消息以开启导购对话");
            }
            return openSessionWithFirstUserMessage(userId, profile, baseCandidates, frequentTop, userMsg);
        }

        ProactiveRecommendSession session = sessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw new IllegalArgumentException("会话不存在");
        }
        if (userMsg.isEmpty()) {
            throw new IllegalArgumentException("请输入回复内容");
        }

        insertMessageReturnId(sessionId, ROLE_USER, userMsg, null);
        backfillSessionTitleIfBlankInDb(sessionId);
        touchSession(sessionId);
        return assistantTurnAfterUserMessage(userId, sessionId, profile, baseCandidates, frequentTop, userMsg);
    }

    /**
     * 首条用户消息：创建会话、落库用户句，再走与后续轮相同的助手生成逻辑。
     */
    private ProactiveTurnResponse openSessionWithFirstUserMessage(
            Long userId,
            UserProfileDTO profile,
            List<RecommendationItemDTO> baseCandidates,
            List<FrequentProductItemDTO> frequentTop,
            String userMsg
    ) {
        LocalDateTime now = LocalDateTime.now();
        ProactiveRecommendSession s = new ProactiveRecommendSession();
        s.setUserId(userId);
        s.setCreatedAt(now);
        s.setUpdatedAt(now);
        sessionMapper.insert(s);
        Long sid = s.getSessionId();
        insertMessageReturnId(sid, ROLE_USER, userMsg, null);
        persistSessionTitle(sid, userMsg);
        touchSession(sid);
        return assistantTurnAfterUserMessage(userId, sid, profile, baseCandidates, frequentTop, userMsg);
    }

    /** 用户句已写入后：拉历史、调模型、写助手与展示事件。 */
    private ProactiveTurnResponse assistantTurnAfterUserMessage(
            Long userId,
            Long sessionId,
            UserProfileDTO profile,
            List<RecommendationItemDTO> baseCandidates,
            List<FrequentProductItemDTO> frequentTop,
            String userMsg
    ) {
        int userTurnIndex = countUserTurns(sessionId) - 1;

        List<ProactiveRecommendMessage> histRows = messageMapper.selectList(
                new QueryWrapper<ProactiveRecommendMessage>()
                        .eq("session_id", sessionId)
                        .orderByAsc("created_at")
                        .last("LIMIT " + Math.max(4, proactiveMaxHistory)));

        List<Map<String, String>> chatHistory = histRows.stream()
                .map(r -> {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("role", r.getRole());
                    m.put("content", r.getContent() != null ? r.getContent() : "");
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> aiBody = AiProactiveClient.buildPayload(
                userId,
                sessionId,
                profile != null ? profile.getPetPreference() : null,
                profile != null ? profile.getMembershipTier() : null,
                AiProactiveClient.summarizeRecommendations(baseCandidates),
                AiProactiveClient.summarizeFrequent(frequentTop),
                chatHistory,
                userMsg
        );

        AiProactiveTurnOutcome aiOutcome = aiProactiveClient.turnWithOutcome(aiBody);
        Optional<ProactiveAiHttpBody> ai = aiOutcome.getBody();
        String assistantText;
        List<RecommendationItemDTO> shown = List.of();
        if (ai.isPresent()) {
            assistantText = ai.get().getAssistantMessage().trim();
            List<Long> hl = normalizeHighlightProductIds(ai.get().getHighlightProductIds());
            shown = resolveHighlightedProducts(baseCandidates, hl);
        } else {
            assistantText = fallbackReply(profile, baseCandidates, userMsg, userTurnIndex);
        }
        shown = capShown(shown);

        String payloadJson = payloadJsonForProducts(shown);
        Long assistantMsgId = insertMessageReturnId(sessionId, ROLE_ASSISTANT, assistantText, payloadJson);
        touchSession(sessionId);
        proactiveRecommendDisplayEventService.recordAssistantHighlight(sessionId, userId, assistantMsgId, shown);

        ProactiveTurnResponse out = new ProactiveTurnResponse();
        out.setSessionId(sessionId);
        out.setAssistantMessage(assistantText);
        out.setCandidateProducts(shown);
        out.setDone(false);
        out.setReplySource(aiOutcome.getKind().name());
        return out;
    }

    /**
     * 恢复用户最近一次会话（按 {@code proactive_recommend_session.updated_at}），用于前端再次进入页面。
     * 候选商品来自<strong>时间顺序上最后一条</strong>含非空 {@code productIds} 的助手消息 payload。
     */
    @Transactional(readOnly = true)
    public ProactiveSessionRestoreDTO loadLatestSessionForResume(long userId) {
        ProactiveRecommendSession latest =
                sessionMapper.selectOne(
                        new QueryWrapper<ProactiveRecommendSession>()
                                .eq("user_id", userId)
                                .inSql(
                                        "session_id",
                                        "SELECT DISTINCT session_id FROM proactive_recommend_message WHERE role = 'USER'")
                                .orderByDesc("updated_at")
                                .last("LIMIT 1"));
        if (latest == null || latest.getSessionId() == null) {
            return emptyRestore();
        }
        return buildRestoreForSession(userId, latest.getSessionId());
    }

    /**
     * 列出用户最近若干条导购会话（侧栏/下拉）。
     *
     * @param includeSessionId 若不为空且不在「最近 n 条」内，则将该会话摘要置于列表首位，再补足其余项，使总条数仍不超过 {@code limit}（便于旧会话被选中后仍出现在下拉里）。
     */
    @Transactional(readOnly = true)
    public List<ProactiveSessionSummaryDTO> listSessionSummaries(long userId, int limit, Long includeSessionId) {
        int n = Math.max(1, Math.min(50, limit));
        List<ProactiveRecommendSession> recentSessions =
                sessionMapper.selectList(
                        new QueryWrapper<ProactiveRecommendSession>()
                                .eq("user_id", userId)
                                .inSql(
                                        "session_id",
                                        "SELECT DISTINCT session_id FROM proactive_recommend_message WHERE role = 'USER'")
                                .orderByDesc("updated_at")
                                .last("LIMIT " + n));
        List<ProactiveSessionSummaryDTO> recent = buildSessionSummaries(recentSessions);
        if (includeSessionId == null || includeSessionId <= 0) {
            return recent;
        }
        if (recent.stream()
                .anyMatch(d -> d != null && includeSessionId.equals(d.getSessionId()))) {
            return recent;
        }
        ProactiveRecommendSession extra = sessionMapper.selectById(includeSessionId);
        if (extra == null || !Objects.equals(extra.getUserId(), userId)) {
            return recent;
        }
        if (!proactiveSessionHasUserTurn(includeSessionId)) {
            return recent;
        }
        List<ProactiveSessionSummaryDTO> pinned = buildSessionSummaries(List.of(extra));
        if (pinned.isEmpty()) {
            return recent;
        }
        ProactiveSessionSummaryDTO pin = pinned.get(0);
        List<ProactiveSessionSummaryDTO> merged = new ArrayList<>();
        merged.add(pin);
        for (ProactiveSessionSummaryDTO x : recent) {
            if (merged.size() >= n) {
                break;
            }
            if (x == null || x.getSessionId() == null) {
                continue;
            }
            if (!includeSessionId.equals(x.getSessionId())) {
                merged.add(x);
            }
        }
        return merged;
    }

    private boolean proactiveSessionHasUserTurn(long sessionId) {
        Long c =
                messageMapper.selectCount(
                        new QueryWrapper<ProactiveRecommendMessage>()
                                .eq("session_id", sessionId)
                                .eq("role", ROLE_USER));
        return c != null && c > 0;
    }

    private List<ProactiveSessionSummaryDTO> buildSessionSummaries(List<ProactiveRecommendSession> rows) {
        List<ProactiveSessionSummaryDTO> out = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return out;
        }
        List<Long> allIds = new ArrayList<>();
        for (ProactiveRecommendSession s : rows) {
            if (s != null && s.getSessionId() != null) {
                allIds.add(s.getSessionId());
            }
        }
        Map<Long, String> dbTitles = loadSessionTitlesFromDbOrEmpty(allIds);
        List<Long> missingTitleIds = new ArrayList<>();
        for (ProactiveRecommendSession s : rows) {
            if (s == null || s.getSessionId() == null) {
                continue;
            }
            ProactiveSessionSummaryDTO d = new ProactiveSessionSummaryDTO();
            d.setSessionId(s.getSessionId());
            d.setUpdatedAt(s.getUpdatedAt());
            String tit = dbTitles.get(s.getSessionId());
            if (tit != null && !tit.isBlank()) {
                d.setTitle(tit.trim());
            } else {
                missingTitleIds.add(s.getSessionId());
                d.setTitle("");
            }
            out.add(d);
        }
        if (!missingTitleIds.isEmpty()) {
            List<SessionFirstUserMessageDTO> fetched =
                    messageMapper.selectFirstUserMessageBySessionIds(missingTitleIds);
            Map<Long, String> bySid = new HashMap<>();
            if (fetched != null) {
                for (SessionFirstUserMessageDTO e : fetched) {
                    if (e == null || e.getSessionId() == null) {
                        continue;
                    }
                    bySid.put(e.getSessionId(), e.getContent());
                }
            }
            for (ProactiveSessionSummaryDTO d : out) {
                if (d.getTitle() != null && !d.getTitle().isBlank()) {
                    continue;
                }
                String raw = bySid.get(d.getSessionId());
                String derived = normalizeSessionTitleForStorage(raw != null ? raw : "");
                d.setTitle(derived.isEmpty() ? "会话 #" + d.getSessionId() : derived);
            }
        }
        return out;
    }

    private String normalizeSessionTitleForStorage(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim().replaceAll("\\s+", " ");
        if (t.isEmpty()) {
            return "";
        }
        if (t.length() <= SESSION_TITLE_MAX_LEN) {
            return t;
        }
        return t.substring(0, SESSION_TITLE_MAX_LEN);
    }

    private void persistSessionTitle(long sessionId, String rawUserText) {
        String t = normalizeSessionTitleForStorage(rawUserText);
        if (t.isEmpty()) {
            return;
        }
        try {
            sessionMapper.updateSessionTitle(sessionId, t);
        } catch (Exception ex) {
            log.warn(
                    "persist proactive session title skipped (若需落库标题请执行 migrate_proactive_session_title.sql): {}",
                    ex.getMessage());
        }
    }

    /** 批量读 title；库无该列或查询失败时返回空 Map，由首条用户消息兜底展示。 */
    private Map<Long, String> loadSessionTitlesFromDbOrEmpty(List<Long> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return Map.of();
        }
        try {
            List<SessionIdTitleDTO> list = sessionMapper.selectTitlesBySessionIds(sessionIds);
            Map<Long, String> m = new HashMap<>();
            if (list != null) {
                for (SessionIdTitleDTO row : list) {
                    if (row == null || row.getSessionId() == null) {
                        continue;
                    }
                    String tit = row.getTitle();
                    if (tit != null && !tit.isBlank()) {
                        m.put(row.getSessionId(), tit.trim());
                    }
                }
            }
            return m;
        } catch (Exception ex) {
            log.debug("proactive session.title unavailable: {}", ex.getMessage());
            return new HashMap<>();
        }
    }

    /** 旧数据 title 为空时，用库中最早一条 USER 正文补写标题。 */
    private void backfillSessionTitleIfBlankInDb(long sessionId) {
        Map<Long, String> existing = loadSessionTitlesFromDbOrEmpty(List.of(sessionId));
        String cur = existing.get(sessionId);
        if (cur != null && !cur.isBlank()) {
            return;
        }
        List<SessionFirstUserMessageDTO> rows =
                messageMapper.selectFirstUserMessageBySessionIds(List.of(sessionId));
        if (rows == null || rows.isEmpty()) {
            return;
        }
        String raw = rows.get(0).getContent();
        persistSessionTitle(sessionId, raw != null ? raw : "");
    }

    /** 按会话 id 恢复（须属于当前用户）。 */
    @Transactional(readOnly = true)
    public ProactiveSessionRestoreDTO restoreSessionById(long userId, long sessionId) {
        ProactiveRecommendSession s = sessionMapper.selectById(sessionId);
        if (s == null || !Objects.equals(s.getUserId(), userId)) {
            return emptyRestore();
        }
        return buildRestoreForSession(userId, sessionId);
    }

    private ProactiveSessionRestoreDTO emptyRestore() {
        ProactiveSessionRestoreDTO empty = new ProactiveSessionRestoreDTO();
        empty.setSessionId(null);
        empty.setMessages(List.of());
        empty.setCandidateProducts(List.of());
        return empty;
    }

    private ProactiveSessionRestoreDTO buildRestoreForSession(long userId, long sid) {
        ProactiveRecommendSession session = sessionMapper.selectById(sid);
        if (session == null || !Objects.equals(session.getUserId(), userId)) {
            return emptyRestore();
        }
        List<ProactiveRecommendMessage> rows =
                messageMapper.selectList(
                        new QueryWrapper<ProactiveRecommendMessage>()
                                .eq("session_id", sid)
                                .orderByAsc("created_at")
                                .last("LIMIT 800"));
        if (rows == null || rows.isEmpty()) {
            return emptyRestore();
        }

        List<ProactiveChatMessageDTO> messages = new ArrayList<>();
        for (ProactiveRecommendMessage r : rows) {
            ProactiveChatMessageDTO m = new ProactiveChatMessageDTO();
            if (ROLE_USER.equalsIgnoreCase(r.getRole())) {
                m.setRole("user");
            } else {
                m.setRole("assistant");
            }
            m.setContent(r.getContent() != null ? r.getContent() : "");
            messages.add(m);
        }

        List<RecommendationItemDTO> candidates = List.of();
        List<Long> fromDisplay = proactiveRecommendDisplayEventService.loadLatestProductIdsForSession(sid);
        if (!fromDisplay.isEmpty()) {
            candidates = hydrateRecommendationItems(fromDisplay);
            recommendationService.stripCommentStrongBadgeForAiGuideDisplay(candidates);
        } else {
            for (int i = rows.size() - 1; i >= 0; i--) {
                ProactiveRecommendMessage r = rows.get(i);
                if (!ROLE_ASSISTANT.equalsIgnoreCase(r.getRole())) {
                    continue;
                }
                List<Long> ids = parseProductIdsFromPayload(r.getPayloadJson());
                if (!ids.isEmpty()) {
                    candidates = hydrateRecommendationItems(ids);
                    recommendationService.stripCommentStrongBadgeForAiGuideDisplay(candidates);
                    break;
                }
            }
        }

        ProactiveSessionRestoreDTO out = new ProactiveSessionRestoreDTO();
        out.setSessionId(sid);
        out.setMessages(messages);
        out.setCandidateProducts(candidates);
        return out;
    }

    private List<Long> parseProductIdsFromPayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(payloadJson);
            JsonNode arr = root.get("productIds");
            if (arr == null || !arr.isArray() || arr.isEmpty()) {
                return List.of();
            }
            List<Long> out = new ArrayList<>();
            for (JsonNode n : arr) {
                if (n != null && n.isNumber()) {
                    long v = n.longValue();
                    if (v > 0) {
                        out.add(v);
                    }
                }
            }
            return out;
        } catch (JsonProcessingException e) {
            log.warn("proactive restore payload parse failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<RecommendationItemDTO> hydrateRecommendationItems(List<Long> ids) {
        List<RecommendationItemDTO> list = new ArrayList<>();
        for (Long pid : ids) {
            if (pid == null || pid <= 0) {
                continue;
            }
            ProductDTO p = productService.getProductById(pid);
            if (p == null) {
                continue;
            }
            RecommendationItemDTO dto = new RecommendationItemDTO();
            dto.setProductId(pid);
            dto.setProduct(p);
            list.add(dto);
        }
        return list;
    }

    /**
     * 与「解析并推荐」一致：优先直连在线 XGB；扩大池子让豆包在每轮用 highlightProductIds 自主筛选子集。
     */
    private List<RecommendationItemDTO> loadProactiveBaseCandidates(long userId) {
        int n = Math.max(8, Math.min(proactiveCandidatePoolSize, PROACTIVE_POOL_CAP));
        List<RecommendationItemDTO> items = recommendationService.recommendForUser(userId, n, true).items();
        recommendationService.stripCommentStrongBadgeForAiGuideDisplay(items);
        return items;
    }

    private Long insertMessageReturnId(Long sessionId, String role, String content, String payloadJson) {
        ProactiveRecommendMessage m = new ProactiveRecommendMessage();
        m.setSessionId(sessionId);
        m.setRole(role);
        m.setContent(content);
        m.setPayloadJson(payloadJson);
        m.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(m);
        return m.getMessageId();
    }

    private void touchSession(Long sessionId) {
        ProactiveRecommendSession patch = new ProactiveRecommendSession();
        patch.setSessionId(sessionId);
        patch.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(patch);
    }

    private int countUserTurns(Long sessionId) {
        Long n = messageMapper.selectCount(
                new QueryWrapper<ProactiveRecommendMessage>().eq("session_id", sessionId).eq("role", ROLE_USER));
        return n == null ? 0 : n.intValue();
    }

    /** 豆包返回的 id 去重、保序，最多保留 5 个 */
    private static List<Long> normalizeHighlightProductIds(List<Long> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> s = new LinkedHashSet<>();
        for (Long id : raw) {
            if (id != null && id > 0) {
                s.add(id);
                if (s.size() >= MAX_SHOWN_PRODUCTS) {
                    break;
                }
            }
        }
        return new ArrayList<>(s);
    }

    /** 前端最多展示 5 条（仅对已有高亮列表截断） */
    private static List<RecommendationItemDTO> capShown(List<RecommendationItemDTO> list) {
        if (list == null || list.isEmpty()) {
            return list == null ? List.of() : list;
        }
        if (list.size() <= MAX_SHOWN_PRODUCTS) {
            return list;
        }
        return new ArrayList<>(list.subList(0, MAX_SHOWN_PRODUCTS));
    }

    /**
     * 仅当模型给出非空 highlight 时才返回商品；追问阶段不展示推荐卡片。
     */
    private static List<RecommendationItemDTO> resolveHighlightedProducts(
            List<RecommendationItemDTO> base,
            List<Long> highlightIds
    ) {
        if (highlightIds == null || highlightIds.isEmpty() || base == null || base.isEmpty()) {
            return List.of();
        }
        Set<Long> want = new HashSet<>(highlightIds);
        List<RecommendationItemDTO> picked = new ArrayList<>();
        for (RecommendationItemDTO it : base) {
            if (it.getProductId() != null && want.contains(it.getProductId())) {
                picked.add(it);
            }
        }
        return picked.isEmpty() ? List.of() : picked;
    }

    private String payloadJsonForProducts(List<RecommendationItemDTO> items) {
        List<Long> ids = items.stream()
                .map(RecommendationItemDTO::getProductId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toList());
        Map<String, Object> m = new HashMap<>();
        m.put("productIds", ids);
        try {
            return objectMapper.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            log.warn("proactive payload json failed: {}", e.getMessage());
            return "{}";
        }
    }

    private static String fallbackReply(
            UserProfileDTO profile,
            List<RecommendationItemDTO> candidates,
            String userMsg,
            int userTurnIndex
    ) {
        String lower = userMsg.toLowerCase();
        if (looksLikeGreetingOrSmallTalk(userMsg)) {
            return String.format(
                    "您好！很高兴为您服务。您在站的宠物偏好是「%s」。您可以说说更想买主食粮还是零食、有没有预算或品牌偏好，我帮您一步步收窄，再为您推荐具体商品。",
                    describePetPreference(profile != null ? profile.getPetPreference() : null));
        }
        boolean budget = lower.contains("预算") || lower.contains("便宜") || lower.contains("元");
        boolean snack = lower.contains("零食") || lower.contains("罐头") || lower.contains("冻干");
        String tail = "您可以继续说说品类或价位，我会结合模型结果保持卡片与描述一致。";
        if (budget) {
            tail = "若预算有限，建议优先从卡片中选评分较高且单价适中的款；也可告诉我大致区间（如「两百以内」）。";
        } else if (snack) {
            tail = "更偏零食的话，可在商品列表里筛「零食」类目，同时下方候选里若有湿粮/冻干可以优先看详情配料。";
        }
        return String.format(
                "收到（第 %d 轮）。结合您在站的偏好「%s」。%s",
                userTurnIndex + 1,
                describePetPreference(profile != null ? profile.getPetPreference() : null),
                tail);
    }

    /** 短句寒暄/闲聊，用于内置话术时避免机械追问（含明确选购意图的不算） */
    private static boolean looksLikeGreetingOrSmallTalk(String raw) {
        if (raw == null) {
            return false;
        }
        String t = raw.trim();
        if (t.length() > 28) {
            return false;
        }
        if (t.contains("猫") || t.contains("狗") || t.contains("粮") || t.contains("罐头")
                || t.contains("零食") || t.contains("预算") || t.contains("买") || t.contains("要")
                || t.contains("推荐") || t.contains("价格")) {
            return false;
        }
        String lower = t.toLowerCase();
        return t.matches("(你好|您好|嗨|哈喽|哈啰|在吗|在么)([！!。.…啦呀\\s]*)?")
                || t.matches("(谢谢|多谢|辛苦了)([！!。.…啦呀\\s]*)?")
                || lower.matches("hi([!.\\s]*)?")
                || lower.matches("hello([!.\\s]*)?");
    }

    private static String describePetPreference(String pref) {
        if (pref == null || pref.isBlank()) {
            return "未指定（猫狗通用）";
        }
        return switch (pref.trim().toLowerCase()) {
            case "cat" -> "猫咪";
            case "dog" -> "狗狗";
            case "both" -> "猫狗兼养";
            default -> pref;
        };
    }
}
