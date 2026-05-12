package com.gzu.petshop.service.recommend;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gzu.petshop.dto.common.ProductDTO;
import com.gzu.petshop.dto.recommend.AiGuideRerankOutcome;
import com.gzu.petshop.dto.recommend.NlBridgeInterpretation;
import com.gzu.petshop.dto.recommend.OnlineScoreResponse;
import com.gzu.petshop.dto.recommend.ProductReviewStatsDTO;
import com.gzu.petshop.dto.recommend.ReviewBertScoreRequest;
import com.gzu.petshop.dto.recommend.ReviewBertScoreResponse;
import com.gzu.petshop.dto.user.RecommendationItemDTO;
import com.gzu.petshop.entity.Category;
import com.gzu.petshop.entity.Review;
import com.gzu.petshop.entity.UserProductRecommendation;
import com.gzu.petshop.mapper.order.OrderItemMapper;
import com.gzu.petshop.mapper.product.ReviewMapper;
import com.gzu.petshop.mapper.recommend.UserProductRecommendationMapper;
import com.gzu.petshop.service.product.CategoryService;
import com.gzu.petshop.service.product.ProductService;
import com.gzu.petshop.service.user.UserEventLogService;
import com.gzu.petshop.service.user.UserEventLogService.BehaviorSignals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 可选：开启 {@code app.recommendation.online-inference-enabled} 时，优先调用在线 XGB 服务对当前库可售商品实时打分；
 * 否则以离线表 {@code user_product_recommendation} 为主。近期浏览通过<strong>有界乘子</strong>在模型分上小幅重排；
 * 热门补位（{@code fallback-hot-v1}）不参与行为加权。
 * 开启 {@code app.recommendation.comment-boost-enabled} 时，按 {@code review} 表均分与条数对排序再做小幅加权（首页「评论强推」角标与乘子）。
 * 智能导购内「评论强推」见 {@link #rerankAiGuideCandidatesByReviews(Long, List, Long)}：强制调用评论 BERT 服务分析文本，再与 XGB 个性化分加权融合排序；返回列表中仅融合分第一的商品带「评论强推」角标（与首页按口碑阈值逐条打标不同）。
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    /** 与前端 categoryNav、库表约定一致：一级狗粮=1、猫粮=2 */
    private static final long ROOT_CATEGORY_DOG_MAIN = 1L;
    private static final long ROOT_CATEGORY_CAT_MAIN = 2L;

    private static final BigDecimal FALLBACK_BASE_SCORE = new BigDecimal("0.3000");
    /** 相对模型分的类目兴趣加成：按「类目系列」汇总命中后再乘（演示下略放大以便可见）。 */
    private static final BigDecimal CATEGORY_RELATIVE_PER_HIT = new BigDecimal("0.012");
    private static final int CATEGORY_HIT_CAP = 6;
    /** 类目兴趣对「模型分 × 乘子」的最大正向偏离（约 +7%）。 */
    private static final BigDecimal MAX_CATEGORY_RELATIVE_BOOST = new BigDecimal("0.070");
    /** 近期刚浏览过的商品：在模型分上乘以该系数（约 −3%），避免刷屏且仍紧贴模型序。 */
    private static final BigDecimal RECENT_VIEW_PRODUCT_FACTOR = new BigDecimal("0.970");
    /** 行为乘子上限，防止在线层压过离线排序。 */
    private static final BigDecimal MULT_CEIL = new BigDecimal("1.050");
    private static final BigDecimal MULT_FLOOR = new BigDecimal("0.930");
    /** 智能导购：单次请求最多处理的候选商品数 */
    private static final int AI_GUIDE_RERANK_CAP = 24;
    /** 每个商品参与 BERT 的最大评论条数（与 Python 侧上限协调） */
    private static final int AI_GUIDE_MAX_REVIEWS_PER_PRODUCT = 24;

    private final UserProductRecommendationMapper recommendationMapper;
    private final ProductService productService;
    private final UserEventLogService userEventLogService;
    private final CategoryService categoryService;
    private final OrderItemMapper orderItemMapper;
    private final OnlineInferenceClient onlineInferenceClient;
    private final ReviewBertInferenceClient reviewBertInferenceClient;
    private final ReviewMapper reviewMapper;
    private final ProactiveRecommendDisplayEventService proactiveRecommendDisplayEventService;
    private final AiGuideCommentRerankAuditService aiGuideCommentRerankAuditService;

    /**
     * 非空时只读该 {@code model_version}，避免同一 user 混存多套测试导入（如 xgb-v1-test 与 xgb_hybrid_v1）导致 rank 重复、顺序异常。
     */
    @Value("${app.recommendation.offline-model-version:}")
    private String offlineModelVersion;

    /**
     * 未指定 {@link #offlineModelVersion} 时，是否只采纳「generated_at 最新一条记录」上的 {@code model_version} 对应的全表行，
     * 用于去掉旧测试版本（如 xgb-v1-test）；不再用精确 {@code generated_at} 全表相等，避免同一批写入时间微差导致零行、只剩热门补位。
     */
    @Value("${app.recommendation.use-latest-generated-batch:true}")
    private boolean useLatestGeneratedBatch;

    @Value("${app.recommendation.online-trigger-enabled:true}")
    private boolean onlineTriggerEnabled;

    @Value("${app.recommendation.online-trigger-unique-view-threshold:10}")
    private int onlineTriggerUniqueViewThreshold;

    @Value("${app.recommendation.online-trigger-lookback-days:14}")
    private int onlineTriggerLookbackDays;

    @Value("${app.recommendation.online-trigger-cooldown-ms:180000}")
    private long onlineTriggerCooldownMs;

    @Value("${app.recommendation.online-cache-ttl-ms:1800000}")
    private long onlineCacheTtlMs;

    /**
     * 强制在线推理时（口语问荐、豆包导购）单次传给 XGB 服务的商品数上限；默认 56，避免一次 POST 上百 id 导致首包很慢。
     * 设为 0 表示沿用原公式 min(120, max(60, topN*6))。
     */
    @Value("${app.recommendation.force-online-batch-max:56}")
    private int forceOnlineBatchMax;

    @Value("${app.recommendation.comment-boost-enabled:true}")
    private boolean commentBoostEnabled;

    @Value("${app.recommendation.comment-strong-min-avg:4.5}")
    private BigDecimal commentStrongMinAvg;

    @Value("${app.recommendation.comment-strong-min-count:3}")
    private int commentStrongMinCount;

    /** 融合权重：XGB（归一化后） */
    @Value("${app.recommendation.ai-guide-fusion-xgb-weight:0.35}")
    private BigDecimal aiGuideFusionXgbWeight;

    /** 融合权重：评论 BERT 聚合分 */
    @Value("${app.recommendation.ai-guide-fusion-bert-weight:0.65}")
    private BigDecimal aiGuideFusionBertWeight;

    private final Map<Long, OnlineTriggerState> onlineTriggerStateByUser = new ConcurrentHashMap<>();

    public RecommendationService(
            UserProductRecommendationMapper recommendationMapper,
            ProductService productService,
            UserEventLogService userEventLogService,
            CategoryService categoryService,
            OrderItemMapper orderItemMapper,
            OnlineInferenceClient onlineInferenceClient,
            ReviewBertInferenceClient reviewBertInferenceClient,
            ReviewMapper reviewMapper,
            ProactiveRecommendDisplayEventService proactiveRecommendDisplayEventService,
            AiGuideCommentRerankAuditService aiGuideCommentRerankAuditService
    ) {
        this.recommendationMapper = recommendationMapper;
        this.productService = productService;
        this.userEventLogService = userEventLogService;
        this.categoryService = categoryService;
        this.orderItemMapper = orderItemMapper;
        this.onlineInferenceClient = onlineInferenceClient;
        this.reviewBertInferenceClient = reviewBertInferenceClient;
        this.reviewMapper = reviewMapper;
        this.proactiveRecommendDisplayEventService = proactiveRecommendDisplayEventService;
        this.aiGuideCommentRerankAuditService = aiGuideCommentRerankAuditService;
    }

    public List<RecommendationItemDTO> listByUser(Long userId, int topN) {
        return recommendForUser(userId, topN, false).items();
    }

    /**
     * AI 导购「首轮」与「解析并推荐」列表：不携带首页同源的 {@code commentStrongRecommend} 角标，
     * 避免用户未点「评论强推」即看到角标；用户点击重排后，仅由 {@link #rerankAiGuideCandidatesByReviews(Long, List, Long)}
     * 为<strong>融合分第一</strong>写回角标（其余为 false），避免批内多条均满足首页阈值时全部被标成「评论强推」。
     */
    public void stripCommentStrongBadgeForAiGuideDisplay(List<RecommendationItemDTO> items) {
        if (items == null) {
            return;
        }
        for (RecommendationItemDTO dto : items) {
            if (dto != null) {
                dto.setCommentStrongRecommend(Boolean.FALSE);
            }
        }
    }

    /**
     * 推荐列表 + 本次打分来源。{@code forceOnlineWhenEnabled=true} 时跳过「浏览满 N 次才刷新」触发器，直接请求在线 XGB 服务（与首页不同，适合口语问荐）。
     */
    public RecommendationRankingOutcome recommendForUser(Long userId, int topN, boolean forceOnlineWhenEnabled) {
        if (userId == null || userId <= 0) {
            return new RecommendationRankingOutcome(List.of(), "OFFLINE_TABLE");
        }
        int safeTopN = Math.max(1, Math.min(topN, 50));
        int poolLimit = resolvePoolLimit(safeTopN, forceOnlineWhenEnabled);

        BehaviorSignals behavior = userEventLogService.loadBehaviorSignals(userId);
        Map<Long, String> categoryPathById = loadCategoryPathById();

        List<Candidate> pool = new ArrayList<>();
        Set<Long> selectedProductIds = new HashSet<>();
        List<UserProductRecommendation> rows = List.of();

        String rankingSource = "OFFLINE_TABLE";
        List<Candidate> onlinePool =
                forceOnlineWhenEnabled
                        ? tryBuildOnlineModelPool(userId, poolLimit)
                        : tryBuildOnlineModelPoolByTrigger(userId, poolLimit);
        if (onlinePool != null && !onlinePool.isEmpty()) {
            rankingSource = "ONLINE_XGB";
            pool.addAll(onlinePool);
            for (Candidate c : onlinePool) {
                selectedProductIds.add(c.product.getProductId());
            }
        } else {
            if (forceOnlineWhenEnabled) {
                log.info(
                        "[recommend] userId={}：口语问荐强制在线推理未返回可用结果（服务未开或超时），已回退离线表。请确认 app.recommendation.online-inference-enabled 与 online_score_server 已启动。",
                        userId);
            }
            QueryWrapper<UserProductRecommendation> recQuery = new QueryWrapper<UserProductRecommendation>()
                    .eq("user_id", userId);
            applyOfflineBatchScope(recQuery, userId);
            recQuery.orderByAsc("rank_no").orderByDesc("score").last("LIMIT " + poolLimit);
            rows = recommendationMapper.selectList(recQuery);

            for (UserProductRecommendation row : rows) {
                ProductDTO product = productService.getProductById(row.getProductId());
                if (!isSellable(product)) {
                    continue;
                }
                if (!selectedProductIds.add(product.getProductId())) {
                    continue;
                }
                pool.add(Candidate.fromModel(row, product));
            }

            if (pool.isEmpty()) {
                if (rows.isEmpty()) {
                    log.warn(
                            "[recommend] userId={}：离线表无可用行（user_product_recommendation 与当前 user_id / model_version 过滤不匹配）。"
                                    + " 首页将仅用热门补位。请核对：① 登录用户 id 与表里 user_id 是否一致；② application.properties 里 app.recommendation.offline-model-version；"
                                    + "③ 若仅用「最新时间」推断版本，请确认该版本下确有推荐行。",
                            userId
                    );
                } else {
                    log.warn(
                            "[recommend] userId={}：有 {} 条离线推荐行，但对应商品在当前库中均不可售（不存在、已下架或库存为 0），"
                                    + " 故仅用热门补位。请核对 product_id 是否与 import 目录商品一致。",
                            userId,
                            rows.size()
                    );
                }
            }
        }

        if (pool.size() < poolLimit) {
            List<ProductDTO> fallbackProducts = productService.getAllProducts();
            fallbackProducts.sort(Comparator.comparing(ProductDTO::getStock, Comparator.nullsFirst(Integer::compareTo)).reversed());
            int seq = 0;
            for (ProductDTO product : fallbackProducts) {
                if (!isSellable(product)) {
                    continue;
                }
                if (!selectedProductIds.add(product.getProductId())) {
                    continue;
                }
                pool.add(Candidate.fallback(product, seq++));
                if (pool.size() >= poolLimit) {
                    break;
                }
            }
        }

        Map<Long, ProductReviewStatsDTO> reviewByPid = loadReviewStatsByProductIds(pool);

        pool.sort(buildComparator(behavior, categoryPathById, reviewByPid));

        List<RecommendationItemDTO> result = new ArrayList<>();
        int rank = 1;
        for (Candidate c : pool) {
            if (result.size() >= safeTopN) {
                break;
            }
            BigDecimal modelScore = c.baseScore != null ? c.baseScore : FALLBACK_BASE_SCORE;
            RecommendationItemDTO dto = new RecommendationItemDTO();
            dto.setProductId(c.product.getProductId());
            // 对外展示为模型分（在线或离线），顺序由内部 effective 决定。
            dto.setScore(modelScore.setScale(4, RoundingMode.HALF_UP));
            dto.setRankNo(rank++);
            dto.setModelVersion(c.modelVersion);
            dto.setProduct(c.product);
            ProductReviewStatsDTO st =
                    c.product.getProductId() != null ? reviewByPid.get(c.product.getProductId()) : null;
            if (st != null && st.getAvgRating() != null) {
                dto.setAvgReviewRating(st.getAvgRating().setScale(2, RoundingMode.HALF_UP));
            }
            if (st != null && st.getReviewCount() != null) {
                dto.setReviewCount(st.getReviewCount());
            }
            dto.setCommentStrongRecommend(commentBoostEnabled && meetsCommentStrongBadge(st));
            result.add(dto);
        }
        return new RecommendationRankingOutcome(result, rankingSource);
    }

    /** 强制在线时收紧批大小以降延迟；非强制保持原 min(120, max(60, topN*6))。 */
    private int resolvePoolLimit(int safeTopN, boolean forceOnlineWhenEnabled) {
        int base = Math.min(120, Math.max(60, safeTopN * 6));
        if (forceOnlineWhenEnabled && forceOnlineBatchMax > 0) {
            return Math.max(safeTopN, Math.min(base, forceOnlineBatchMax));
        }
        return base;
    }

    /**
     * 口语问荐等场景返回：推荐列表 + 本次是否走在线 XGB。
     */
    public static record RecommendationRankingOutcome(List<RecommendationItemDTO> items, String rankingSource) {}

    /**
     * 在已有模型排序列表上，按 NL 解析出的犬猫类目与标题关键词收窄展示（不改变原始 score，仅过滤与截断）。
     */
    public List<RecommendationItemDTO> filterRecommendationsByNlInterpretation(
            List<RecommendationItemDTO> ranked,
            NlBridgeInterpretation interp,
            int topN
    ) {
        if (ranked == null || ranked.isEmpty()) {
            return List.of();
        }
        int cap = Math.max(1, Math.min(topN, 50));
        List<RecommendationItemDTO> pool = new ArrayList<>(ranked);

        List<Long> speciesCats = resolveSpeciesCategoryFilter(interp != null ? interp.getPetSpecies() : null);
        if (interp != null
                && interp.getProductIntent() != null
                && "snack".equalsIgnoreCase(interp.getProductIntent().trim())) {
            // 零食常挂在非「主粮一级树」下，避免误用狗粮/猫粮根类目筛空
            speciesCats = null;
        }
        if (speciesCats != null && !speciesCats.isEmpty()) {
            List<RecommendationItemDTO> narrowed = new ArrayList<>();
            for (RecommendationItemDTO dto : pool) {
                Long cid = dto.getProduct() != null ? dto.getProduct().getCategoryId() : null;
                if (cid != null && speciesCats.contains(cid)) {
                    narrowed.add(dto);
                }
            }
            if (!narrowed.isEmpty()) {
                pool = narrowed;
            }
        }

        List<String> kws = new ArrayList<>();
        if (interp != null && interp.getTitleKeywords() != null) {
            for (String k : interp.getTitleKeywords()) {
                if (k != null && !k.isBlank()) {
                    kws.add(k.trim());
                }
            }
        }
        if (!kws.isEmpty()) {
            List<RecommendationItemDTO> kwMatch = new ArrayList<>();
            for (RecommendationItemDTO dto : pool) {
                if (titleMatchesAnyKeyword(dto, kws)) {
                    kwMatch.add(dto);
                }
            }
            if (!kwMatch.isEmpty()) {
                pool = kwMatch;
            }
        }

        pool = filterOutOppositeSpeciesTitles(pool, interp);

        return pool.stream().limit(cap).collect(Collectors.toList());
    }

    /** 猫场景去掉标题明显为犬粮的商品，反之亦然（防止关键词 OR 误召回）。 */
    private static List<RecommendationItemDTO> filterOutOppositeSpeciesTitles(
            List<RecommendationItemDTO> pool,
            NlBridgeInterpretation interp
    ) {
        if (pool == null || pool.isEmpty() || interp == null || interp.getPetSpecies() == null) {
            return pool == null ? List.of() : pool;
        }
        String sp = interp.getPetSpecies().trim().toLowerCase();
        if (!"cat".equals(sp) && !"dog".equals(sp)) {
            return pool;
        }
        List<RecommendationItemDTO> out = new ArrayList<>();
        for (RecommendationItemDTO dto : pool) {
            if ("cat".equals(sp) && titleLooksLikeDogStaple(dto)) {
                continue;
            }
            if ("dog".equals(sp) && titleLooksLikeCatStaple(dto)) {
                continue;
            }
            out.add(dto);
        }
        return out;
    }

    private static boolean titleLooksLikeDogStaple(RecommendationItemDTO dto) {
        String t = productTitle(dto);
        if (t.isEmpty()) {
            return false;
        }
        return t.contains("犬粮")
                || t.contains("幼犬粮")
                || t.contains("成犬粮")
                || t.contains("狗粮")
                || t.contains("全犬");
    }

    private static boolean titleLooksLikeCatStaple(RecommendationItemDTO dto) {
        String t = productTitle(dto);
        if (t.isEmpty()) {
            return false;
        }
        return t.contains("猫粮")
                || t.contains("幼猫粮")
                || t.contains("成猫粮")
                || t.contains("猫条")
                || t.contains("全猫");
    }

    private static String productTitle(RecommendationItemDTO dto) {
        if (dto == null || dto.getProduct() == null || dto.getProduct().getTitle() == null) {
            return "";
        }
        return dto.getProduct().getTitle().trim();
    }

    /**
     * @return null 表示不按类目过滤；非空为允许的 category_id 列表（含子类目）
     */
    private List<Long> resolveSpeciesCategoryFilter(String petSpecies) {
        if (petSpecies == null || petSpecies.isBlank()) {
            return null;
        }
        String s = petSpecies.trim().toLowerCase();
        if ("dog".equals(s)) {
            return categoryService.resolveCategoryIdsForProductFilter(ROOT_CATEGORY_DOG_MAIN);
        }
        if ("cat".equals(s)) {
            return categoryService.resolveCategoryIdsForProductFilter(ROOT_CATEGORY_CAT_MAIN);
        }
        return null;
    }

    private static boolean titleMatchesAnyKeyword(RecommendationItemDTO dto, List<String> keywords) {
        if (dto == null || dto.getProduct() == null || keywords == null || keywords.isEmpty()) {
            return false;
        }
        String title = dto.getProduct().getTitle();
        if (title == null || title.isBlank()) {
            return false;
        }
        for (String kw : keywords) {
            if (kw != null && !kw.isBlank() && title.contains(kw.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 达到阈值（默认每新增 10 个去重浏览商品）才重算一次在线结果；其余请求复用缓存，避免每次接口都触发在线推理。
     */
    private List<Candidate> tryBuildOnlineModelPoolByTrigger(Long userId, int poolLimit) {
        if (!onlineTriggerEnabled || onlineTriggerUniqueViewThreshold <= 0) {
            return tryBuildOnlineModelPool(userId, poolLimit);
        }
        long now = System.currentTimeMillis();
        int viewedEffective = userEventLogService.countViewLikeEventsCollapseConsecutiveRepeat(
                userId,
                Math.max(1, onlineTriggerLookbackDays)
        );
        int bucket = viewedEffective / Math.max(1, onlineTriggerUniqueViewThreshold);
        OnlineTriggerState state = onlineTriggerStateByUser.computeIfAbsent(userId, k -> new OnlineTriggerState());
        synchronized (state) {
            boolean cacheFresh = state.cachedPool != null && (now - state.cachedAtMs) <= Math.max(30_000L, onlineCacheTtlMs);
            boolean crossedBucket = bucket > state.lastTriggeredBucket;
            boolean cooldownOk = (now - state.lastTriggeredAtMs) >= Math.max(0L, onlineTriggerCooldownMs);
            if (crossedBucket && cooldownOk) {
                List<Candidate> refreshed = tryBuildOnlineModelPool(userId, poolLimit);
                if (refreshed != null && !refreshed.isEmpty()) {
                    state.cachedPool = refreshed;
                    state.cachedAtMs = now;
                    state.lastTriggeredAtMs = now;
                    state.lastTriggeredBucket = bucket;
                    return limitCopy(refreshed, poolLimit);
                }
            }
            if (cacheFresh) {
                return limitCopy(state.cachedPool, poolLimit);
            }
            return null;
        }
    }

    private static List<Candidate> limitCopy(List<Candidate> source, int limit) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        int end = Math.min(Math.max(1, limit), source.size());
        return new ArrayList<>(source.subList(0, end));
    }

    /**
     * 对当前库可售商品批量请求在线 XGB；成功则返回候选（与离线路径互斥）。
     */
    private List<Candidate> tryBuildOnlineModelPool(Long userId, int poolLimit) {
        List<ProductDTO> all = new ArrayList<>(productService.getAllProducts());
        all.sort(Comparator.comparing(ProductDTO::getStock, Comparator.nullsFirst(Integer::compareTo)).reversed());
        List<Long> productIds = new ArrayList<>();
        for (ProductDTO p : all) {
            if (!isSellable(p)) {
                continue;
            }
            productIds.add(p.getProductId());
            if (productIds.size() >= poolLimit) {
                break;
            }
        }
        if (productIds.isEmpty()) {
            return null;
        }
        Integer cnt = orderItemMapper.countDistinctPurchasedProducts(userId);
        int userOrderCnt = cnt == null ? 0 : Math.max(0, cnt);
        Optional<OnlineScoreResponse> resp = onlineInferenceClient.score(userOrderCnt, productIds);
        if (resp.isEmpty()) {
            return null;
        }
        OnlineScoreResponse body = resp.get();
        String modelVersion =
                body.getModelVersion() != null && !body.getModelVersion().isBlank()
                        ? body.getModelVersion().trim()
                        : "xgb_online_v1";
        List<Candidate> out = new ArrayList<>();
        int tie = 1;
        for (OnlineScoreResponse.ScoreEntry e : body.getScores()) {
            if (out.size() >= poolLimit) {
                break;
            }
            ProductDTO product = productService.getProductById(e.getProductId());
            if (!isSellable(product)) {
                continue;
            }
            BigDecimal s = BigDecimal.valueOf(e.getScore());
            out.add(Candidate.fromOnline(product, s, modelVersion, tie++));
        }
        return out.isEmpty() ? null : out;
    }

    private void applyOfflineBatchScope(QueryWrapper<UserProductRecommendation> qw, Long userId) {
        if (offlineModelVersion != null && !offlineModelVersion.isBlank()) {
            qw.eq("model_version", offlineModelVersion.trim());
            return;
        }
        if (!useLatestGeneratedBatch) {
            return;
        }
        String versionFromNewest = queryModelVersionOfNewestTimestampRow(userId);
        if (versionFromNewest != null && !versionFromNewest.isBlank()) {
            qw.eq("model_version", versionFromNewest.trim());
        }
    }

    /**
     * 取该用户「generated_at 最大」的一行上的 model_version；用于整批过滤，不要求同批每行时间戳完全一致。
     */
    private String queryModelVersionOfNewestTimestampRow(Long userId) {
        List<UserProductRecommendation> probe = recommendationMapper.selectList(
                new QueryWrapper<UserProductRecommendation>()
                        .select("model_version", "generated_at")
                        .eq("user_id", userId)
                        .isNotNull("generated_at")
                        .orderByDesc("generated_at")
                        .orderByDesc("score")
                        .last("LIMIT 1")
        );
        if (probe.isEmpty()) {
            return null;
        }
        return probe.get(0).getModelVersion();
    }

    private Map<Long, String> loadCategoryPathById() {
        Map<Long, String> map = new HashMap<>();
        for (Category c : categoryService.getAllCategories()) {
            map.put(c.getCategoryId(), c.getPath() != null ? c.getPath() : "");
        }
        return map;
    }

    /**
     * 与 {@code category.path} 首段一致视为同一「系列」（如 51006 下各叶子猫粮），避免只对完全相同的 leaf category_id 才加权。
     */
    private static long seriesKeyFromPath(String path) {
        if (path == null || path.isBlank()) {
            return -1L;
        }
        int slash = path.indexOf('/');
        String first = slash < 0 ? path.trim() : path.substring(0, slash).trim();
        if (first.isEmpty()) {
            return -1L;
        }
        try {
            return Long.parseLong(first);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private static int seriesInterestHits(
            Long productCategoryId,
            BehaviorSignals behavior,
            Map<Long, String> categoryPathById
    ) {
        if (productCategoryId == null || behavior == null || behavior.getCategoryHits().isEmpty()) {
            return 0;
        }
        String pPath = categoryPathById.getOrDefault(productCategoryId, "");
        long series = seriesKeyFromPath(pPath);
        if (series < 0) {
            return behavior.getCategoryHits().getOrDefault(productCategoryId, 0);
        }
        int sum = 0;
        for (Map.Entry<Long, Integer> e : behavior.getCategoryHits().entrySet()) {
            String vPath = categoryPathById.getOrDefault(e.getKey(), "");
            if (seriesKeyFromPath(vPath) == series) {
                sum += e.getValue();
            }
        }
        return sum;
    }

    private Comparator<Candidate> buildComparator(
            BehaviorSignals behavior,
            Map<Long, String> categoryPathById,
            Map<Long, ProductReviewStatsDTO> reviewByPid
    ) {
        return (a, b) -> {
            BigDecimal sa = effectiveRankingScore(a, behavior, categoryPathById, reviewByPid);
            BigDecimal sb = effectiveRankingScore(b, behavior, categoryPathById, reviewByPid);
            int cmp = sb.compareTo(sa);
            if (cmp != 0) {
                return cmp;
            }
            return Integer.compare(a.tieBreakRank, b.tieBreakRank);
        };
    }

    private Map<Long, ProductReviewStatsDTO> loadReviewStatsByProductIds(List<Candidate> pool) {
        if (pool == null || pool.isEmpty()) {
            return Map.of();
        }
        List<Long> ids =
                pool.stream()
                        .map(c -> c.product != null ? c.product.getProductId() : null)
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<ProductReviewStatsDTO> rows = reviewMapper.selectStatsByProductIds(ids);
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Map<Long, ProductReviewStatsDTO> map = new HashMap<>();
        for (ProductReviewStatsDTO row : rows) {
            if (row != null && row.getProductId() != null) {
                map.put(row.getProductId(), row);
            }
        }
        return map;
    }

    private boolean meetsCommentStrongBadge(ProductReviewStatsDTO s) {
        if (s == null || s.getReviewCount() == null || s.getAvgRating() == null) {
            return false;
        }
        if (s.getReviewCount() < Math.max(1, commentStrongMinCount)) {
            return false;
        }
        return s.getAvgRating().compareTo(commentStrongMinAvg) >= 0;
    }

    /**
     * 评论侧乘子：高均分且足够条数时略抬高有效分，使「口碑好」商品在池内更靠前。
     */
    private BigDecimal commentReviewMultiplier(ProductReviewStatsDTO s) {
        if (!commentBoostEnabled || s == null || s.getReviewCount() == null || s.getAvgRating() == null) {
            return BigDecimal.ONE;
        }
        int n = s.getReviewCount();
        BigDecimal avg = s.getAvgRating();
        if (n >= Math.max(1, commentStrongMinCount) && avg.compareTo(commentStrongMinAvg) >= 0) {
            return new BigDecimal("1.045");
        }
        if (n >= 2 && avg.compareTo(new BigDecimal("4.2")) >= 0) {
            return new BigDecimal("1.022");
        }
        return BigDecimal.ONE;
    }

    /**
     * 仅用于排序：模型分 × 有界行为乘子 × 评论口碑乘子；热门补位行为乘子恒为 1，评论乘子仍可按口碑微调。
     */
    private BigDecimal effectiveRankingScore(
            Candidate c,
            BehaviorSignals behavior,
            Map<Long, String> categoryPathById,
            Map<Long, ProductReviewStatsDTO> reviewByPid
    ) {
        BigDecimal base = c.baseScore != null ? c.baseScore : FALLBACK_BASE_SCORE;
        Long pid = c.product != null ? c.product.getProductId() : null;
        ProductReviewStatsDTO rev = pid != null && reviewByPid != null ? reviewByPid.get(pid) : null;
        return base.multiply(behaviorMultiplier(c, behavior, categoryPathById))
                .multiply(commentReviewMultiplier(rev));
    }

    /**
     * 智能导购「评论强推」：必须先成功调用评论 BERT 服务对 {@code review} 文本逐条推理并聚合，再与 XGB 个性化分融合排序。
     * <p>融合分 = w_xgb·normalize(XGB) + w_bert·BERT_agg（权重见 {@code app.recommendation.ai-guide-fusion-*-weight}）。
     * XGB 分来源与首页一致：在线推理优先，否则离线表，否则保底分；在本批候选内对 XGB 做 min-max 归一化到 [0,1]。
     * <p>若无可用评论文本、BERT 服务未启用或调用失败，返回 {@link AiGuideRerankOutcome#fail(int, String)}，不静默回退。
     * <p>返回项 {@code commentStrongRecommend} 仅对<strong>融合分第一</strong>为 true（本批「首推」）；其余为 false，与首页按口碑阈值逐条打标不同。
     */
    public AiGuideRerankOutcome rerankAiGuideCandidatesByReviews(Long userId, List<Long> rawProductIds, Long proactiveSessionId) {
        if (userId == null || userId <= 0) {
            return AiGuideRerankOutcome.fail(400, "用户无效");
        }
        if (rawProductIds == null || rawProductIds.isEmpty()) {
            return AiGuideRerankOutcome.fail(400, "请先展示 AI 推荐商品，或传入 productIds");
        }
        LinkedHashSet<Long> dedup = new LinkedHashSet<>();
        for (Long id : rawProductIds) {
            if (id != null && id > 0) {
                dedup.add(id);
                if (dedup.size() >= AI_GUIDE_RERANK_CAP) {
                    break;
                }
            }
        }
        List<Long> ids = new ArrayList<>(dedup);
        if (ids.isEmpty()) {
            return AiGuideRerankOutcome.fail(400, "无有效商品 id");
        }

        record IndexedRow(int inputOrder, long productId, ProductDTO product) {}

        List<IndexedRow> rows = new ArrayList<>();
        int ord = 0;
        for (Long pid : ids) {
            ProductDTO p = productService.getProductById(pid);
            if (p == null) {
                continue;
            }
            rows.add(new IndexedRow(ord++, pid, p));
        }
        if (rows.isEmpty()) {
            return AiGuideRerankOutcome.fail(400, "候选商品不存在或已下架");
        }

        List<Long> pids = rows.stream().map(IndexedRow::productId).collect(Collectors.toList());

        Map<Long, ProductReviewStatsDTO> statsMap = new HashMap<>();
        List<ProductReviewStatsDTO> statRows = reviewMapper.selectStatsByProductIds(pids);
        if (statRows != null) {
            for (ProductReviewStatsDTO row : statRows) {
                if (row != null && row.getProductId() != null) {
                    statsMap.put(row.getProductId(), row);
                }
            }
        }

        Map<Long, List<String>> textsByPid = loadReviewTextsForProducts(pids);
        ReviewBertScoreRequest bertReq = new ReviewBertScoreRequest();
        for (Long pid : pids) {
            ReviewBertScoreRequest.ProductReviewTexts pt = new ReviewBertScoreRequest.ProductReviewTexts();
            pt.setProductId(pid);
            List<ReviewBertScoreRequest.ReviewTextLine> lines = new ArrayList<>();
            for (String t : textsByPid.getOrDefault(pid, List.of())) {
                lines.add(new ReviewBertScoreRequest.ReviewTextLine(t));
            }
            pt.setReviews(lines);
            bertReq.getProducts().add(pt);
        }

        boolean anyReviewText =
                bertReq.getProducts().stream()
                        .anyMatch(p -> p.getReviews() != null && !p.getReviews().isEmpty());
        if (!anyReviewText) {
            return AiGuideRerankOutcome.fail(
                    400,
                    "本批商品暂无可用评论（无文字且无星级可供分析），无法执行 BERT。请先为相关商品补充评价数据。");
        }

        if (!reviewBertInferenceClient.isEnabled()) {
            return AiGuideRerankOutcome.fail(
                    503,
                    "评论 BERT 推理未启用。请配置 app.recommendation.review-bert-inference-enabled=true 与 base-url，并启动 tools/review_bert_score_server.py。");
        }
        Optional<ReviewBertScoreResponse> bertOpt = reviewBertInferenceClient.scoreReviews(bertReq);
        if (bertOpt.isEmpty()) {
            return AiGuideRerankOutcome.fail(
                    503,
                    "评论 BERT 推理服务不可用（连接失败或服务异常）。请启动：python tools/review_bert_score_server.py（默认端口 8766），并设置环境变量 REVIEW_BERT_MODEL_DIR。");
        }
        ReviewBertScoreResponse bertBody = bertOpt.get();
        Map<Long, Double> bertByPid = new HashMap<>();
        if (bertBody.getScores() != null) {
            for (ReviewBertScoreResponse.ScoreEntry e : bertBody.getScores()) {
                if (e != null) {
                    bertByPid.put(e.getProductId(), e.getScore());
                }
            }
        }

        Integer orderCnt = orderItemMapper.countDistinctPurchasedProducts(userId);
        int userOrderCnt = orderCnt == null ? 0 : Math.max(0, orderCnt);

        Map<Long, BigDecimal> onlineScoreByPid = new HashMap<>();
        String onlineModelVersion = null;
        Optional<OnlineScoreResponse> onlineResp = onlineInferenceClient.score(userOrderCnt, pids);
        if (onlineResp.isPresent()) {
            OnlineScoreResponse body = onlineResp.get();
            onlineModelVersion =
                    body.getModelVersion() != null && !body.getModelVersion().isBlank()
                            ? body.getModelVersion().trim()
                            : "xgb_online_v1";
            if (body.getScores() != null) {
                for (OnlineScoreResponse.ScoreEntry e : body.getScores()) {
                    if (e == null) {
                        continue;
                    }
                    onlineScoreByPid.put(e.getProductId(), BigDecimal.valueOf(e.getScore()));
                }
            }
        }

        List<Long> missingForOffline = new ArrayList<>();
        for (Long pid : pids) {
            if (!onlineScoreByPid.containsKey(pid)) {
                missingForOffline.add(pid);
            }
        }
        Map<Long, UserProductRecommendation> offlineByPid =
                loadOfflineRecommendationsForUserProducts(userId, missingForOffline);

        Map<Long, BigDecimal> xgbRawByPid = new HashMap<>();
        Map<Long, String> xgbVerByPid = new HashMap<>();
        List<BigDecimal> xgbRaws = new ArrayList<>();
        for (IndexedRow r : rows) {
            long pid = r.productId();
            BigDecimal raw;
            String ver;
            if (onlineScoreByPid.containsKey(pid)) {
                raw = onlineScoreByPid.get(pid);
                ver = onlineModelVersion != null ? onlineModelVersion : "xgb_online_v1";
            } else if (offlineByPid.containsKey(pid)) {
                UserProductRecommendation row = offlineByPid.get(pid);
                if (row != null && row.getScore() != null) {
                    raw = row.getScore();
                    ver = row.getModelVersion() != null ? row.getModelVersion() : "offline_xgb";
                } else {
                    raw = FALLBACK_BASE_SCORE;
                    ver = "no_xgb_score";
                }
            } else {
                raw = FALLBACK_BASE_SCORE;
                ver = "no_xgb_score";
            }
            xgbRawByPid.put(pid, raw);
            xgbVerByPid.put(pid, ver);
            xgbRaws.add(raw);
        }

        BigDecimal xgbMin =
                xgbRaws.stream().min(Comparator.naturalOrder()).orElse(FALLBACK_BASE_SCORE);
        BigDecimal xgbMax =
                xgbRaws.stream().max(Comparator.naturalOrder()).orElse(FALLBACK_BASE_SCORE);

        BigDecimal wx = aiGuideFusionXgbWeight != null ? aiGuideFusionXgbWeight : new BigDecimal("0.35");
        BigDecimal wb = aiGuideFusionBertWeight != null ? aiGuideFusionBertWeight : new BigDecimal("0.65");
        BigDecimal sumW = wx.add(wb);
        if (sumW.compareTo(BigDecimal.ZERO) <= 0) {
            wx = new BigDecimal("0.35");
            wb = new BigDecimal("0.65");
            sumW = wx.add(wb);
        }
        wx = wx.divide(sumW, 8, RoundingMode.HALF_UP);
        wb = wb.divide(sumW, 8, RoundingMode.HALF_UP);

        String bertMv =
                bertBody.getModelVersion() != null && !bertBody.getModelVersion().isBlank()
                        ? bertBody.getModelVersion().trim()
                        : "bert_review";

        record FusionRow(
                int inputOrder,
                long productId,
                ProductDTO product,
                BigDecimal fusion,
                BigDecimal bertScore,
                BigDecimal normXgb,
                String xgbVer
        ) {}

        List<FusionRow> fusionRows = new ArrayList<>();
        for (IndexedRow r : rows) {
            long pid = r.productId();
            BigDecimal normXgb = normalizeMinMax01(xgbRawByPid.get(pid), xgbMin, xgbMax);
            double b = bertByPid.getOrDefault(pid, 0.5);
            BigDecimal bertB = BigDecimal.valueOf(b);
            BigDecimal fusion = wx.multiply(normXgb).add(wb.multiply(bertB));
            fusionRows.add(new FusionRow(r.inputOrder(), pid, r.product(), fusion, bertB, normXgb, xgbVerByPid.get(pid)));
        }

        fusionRows.sort(
                Comparator.comparing((FusionRow fr) -> fr.fusion())
                        .reversed()
                        .thenComparingInt(FusionRow::inputOrder));

        List<RecommendationItemDTO> out = new ArrayList<>();
        int rank = 1;
        for (FusionRow fr : fusionRows) {
            RecommendationItemDTO dto = new RecommendationItemDTO();
            dto.setProductId(fr.productId());
            dto.setProduct(fr.product());
            dto.setRankNo(rank);
            dto.setScore(fr.fusion().setScale(4, RoundingMode.HALF_UP));
            dto.setReviewModelScore(fr.bertScore().setScale(4, RoundingMode.HALF_UP));
            dto.setXgbPersonalizedScore(fr.normXgb().setScale(4, RoundingMode.HALF_UP));
            String xgv = fr.xgbVer() != null ? fr.xgbVer() : "";
            String mv = "ai_guide_bert_xgb_fusion_v1|bert=" + bertMv + "|xgb=" + xgv;
            if (mv.length() > 220) {
                mv = mv.substring(0, 217) + "...";
            }
            dto.setModelVersion(mv);
            ProductReviewStatsDTO st = statsMap.get(fr.productId());
            if (st != null && st.getAvgRating() != null) {
                dto.setAvgReviewRating(st.getAvgRating().setScale(2, RoundingMode.HALF_UP));
            }
            if (st != null && st.getReviewCount() != null) {
                dto.setReviewCount(st.getReviewCount());
            }
            // 与首页不同：此处角标表示「本批 BERT+XGB 融合分第一」，不复用「高均分+条数」逐条达标，
            // 否则种子评论下整批商品会全部被标成「评论强推」。
            dto.setCommentStrongRecommend(rank == 1);
            rank++;
            out.add(dto);
        }
        Map<Long, Integer> inputOrderByPid = new HashMap<>();
        for (IndexedRow r : rows) {
            inputOrderByPid.put(r.productId(), r.inputOrder());
        }
        aiGuideCommentRerankAuditService.recordSuccessfulRerank(
                userId,
                proactiveSessionId,
                ids,
                wx,
                wb,
                bertMv,
                out,
                inputOrderByPid);
        if (proactiveSessionId != null && proactiveSessionId > 0) {
            proactiveRecommendDisplayEventService.recordCommentRerank(proactiveSessionId, userId, ids, out);
        }
        return AiGuideRerankOutcome.success(out);
    }

    private Map<Long, List<String>> loadReviewTextsForProducts(List<Long> pids) {
        Map<Long, List<String>> out = new HashMap<>();
        for (Long pid : pids) {
            out.put(pid, new ArrayList<>());
        }
        if (pids.isEmpty()) {
            return out;
        }
        List<Review> reviewRows =
                reviewMapper.selectList(
                        Wrappers.<Review>lambdaQuery()
                                .in(Review::getProductId, pids)
                                .and(w -> w.eq(Review::getStatus, 1).or().isNull(Review::getStatus))
                                .orderByDesc(Review::getCreatedAt));
        Map<Long, List<Review>> byPid =
                reviewRows.stream().collect(Collectors.groupingBy(Review::getProductId));
        for (Long pid : pids) {
            List<String> bucket = out.get(pid);
            List<Review> rs = byPid.getOrDefault(pid, List.of());
            for (Review rv : rs) {
                if (bucket.size() >= AI_GUIDE_MAX_REVIEWS_PER_PRODUCT) {
                    break;
                }
                String t = buildAnalyzableReviewText(rv);
                if (t != null && !t.isBlank()) {
                    bucket.add(t.trim());
                }
            }
        }
        return out;
    }

    private static String buildAnalyzableReviewText(Review r) {
        if (r == null) {
            return null;
        }
        String c = r.getContent();
        if (c != null && !c.isBlank()) {
            return c;
        }
        if (r.getRating() != null) {
            return "用户评分：" + r.getRating() + "星，未填写文字评价。";
        }
        return null;
    }

    private static BigDecimal normalizeMinMax01(BigDecimal x, BigDecimal min, BigDecimal max) {
        if (x == null) {
            return new BigDecimal("0.5000");
        }
        BigDecimal lo = min != null ? min : FALLBACK_BASE_SCORE;
        BigDecimal hi = max != null ? max : FALLBACK_BASE_SCORE;
        BigDecimal span = hi.subtract(lo).abs();
        if (span.compareTo(new BigDecimal("1e-9")) < 0) {
            return new BigDecimal("0.5000");
        }
        return x.subtract(lo).divide(span, 8, RoundingMode.HALF_UP);
    }

    private Map<Long, UserProductRecommendation> loadOfflineRecommendationsForUserProducts(
            Long userId, List<Long> productIds
    ) {
        if (userId == null || userId <= 0 || productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        QueryWrapper<UserProductRecommendation> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).in("product_id", productIds);
        applyOfflineBatchScope(qw, userId);
        List<UserProductRecommendation> rows = recommendationMapper.selectList(qw);
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Map<Long, UserProductRecommendation> out = new HashMap<>();
        for (UserProductRecommendation r : rows) {
            if (r != null && r.getProductId() != null) {
                out.putIfAbsent(r.getProductId(), r);
            }
        }
        return out;
    }

    private static BigDecimal behaviorMultiplier(
            Candidate c,
            BehaviorSignals behavior,
            Map<Long, String> categoryPathById
    ) {
        if (!c.fromOfflineModel || behavior == null || behavior.isEmpty()) {
            return BigDecimal.ONE;
        }
        BigDecimal mult = BigDecimal.ONE;
        Long cat = c.product.getCategoryId();
        if (cat != null) {
            int hits = seriesInterestHits(cat, behavior, categoryPathById);
            if (hits > 0) {
                int capped = Math.min(hits, CATEGORY_HIT_CAP);
                BigDecimal rel = CATEGORY_RELATIVE_PER_HIT.multiply(BigDecimal.valueOf(capped));
                if (rel.compareTo(MAX_CATEGORY_RELATIVE_BOOST) > 0) {
                    rel = MAX_CATEGORY_RELATIVE_BOOST;
                }
                mult = mult.add(rel);
            }
        }
        Long pid = c.product.getProductId();
        if (pid != null && behavior.getRecentProductIds().contains(pid)) {
            mult = mult.multiply(RECENT_VIEW_PRODUCT_FACTOR);
        }
        if (mult.compareTo(MULT_CEIL) > 0) {
            mult = MULT_CEIL;
        }
        if (mult.compareTo(MULT_FLOOR) < 0) {
            mult = MULT_FLOOR;
        }
        return mult;
    }

    private static boolean isSellable(ProductDTO product) {
        return product != null
                && product.getStatus() != null
                && product.getStatus() == 1
                && product.getStock() != null
                && product.getStock() > 0;
    }

    private static final class Candidate {
        final ProductDTO product;
        final BigDecimal baseScore;
        final String modelVersion;
        /** 离线 rank 越小越优先；热门补齐用大数排在后。 */
        final int tieBreakRank;
        /** 是否来自 {@code user_product_recommendation}（XGB 等离线模型产出）。 */
        final boolean fromOfflineModel;

        private Candidate(
                ProductDTO product,
                BigDecimal baseScore,
                String modelVersion,
                int tieBreakRank,
                boolean fromOfflineModel
        ) {
            this.product = product;
            this.baseScore = baseScore;
            this.modelVersion = modelVersion;
            this.tieBreakRank = tieBreakRank;
            this.fromOfflineModel = fromOfflineModel;
        }

        static Candidate fromModel(UserProductRecommendation row, ProductDTO product) {
            int rk = row.getRankNo() != null ? row.getRankNo() : 10_000;
            return new Candidate(product, row.getScore(), row.getModelVersion(), rk, true);
        }

        static Candidate fromOnline(ProductDTO product, BigDecimal score, String modelVersion, int tieBreakRank) {
            return new Candidate(product, score, modelVersion, tieBreakRank, true);
        }

        static Candidate fallback(ProductDTO product, int seq) {
            return new Candidate(product, FALLBACK_BASE_SCORE, "fallback-hot-v1", 100_000 + seq, false);
        }
    }

    private static final class OnlineTriggerState {
        int lastTriggeredBucket = 0;
        long lastTriggeredAtMs = 0L;
        long cachedAtMs = 0L;
        List<Candidate> cachedPool;
    }
}
