package com.gzu.petshop.service.recommend;

import com.gzu.petshop.dto.recommend.NlBridgeInterpretation;
import com.gzu.petshop.dto.recommend.NlRecommendResponse;
import com.gzu.petshop.dto.user.RecommendationItemDTO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 自然语言（豆包解析或规则兜底）→ 结构化槽位 → 在 {@link RecommendationService#listByUser} 的模型排序结果上过滤展示。
 */
@Service
public class NlRecommendationBridgeService {

    /** 解析并推荐：返回商品条数上限（可精准时由豆包文案主推 1 款，列表仍至多 5 条） */
    private static final int NL_MAX_PRODUCTS = 5;

    private final AiProactiveClient aiProactiveClient;
    private final RecommendationService recommendationService;

    public NlRecommendationBridgeService(
            AiProactiveClient aiProactiveClient,
            RecommendationService recommendationService
    ) {
        this.aiProactiveClient = aiProactiveClient;
        this.recommendationService = recommendationService;
    }

    public NlRecommendResponse query(Long userId, String question, int topN) {
        int safeTop = Math.max(1, Math.min(topN <= 0 ? NL_MAX_PRODUCTS : topN, NL_MAX_PRODUCTS));
        int poolSize = Math.min(80, Math.max(safeTop * 8, 40));

        Optional<NlBridgeInterpretation> ai = aiProactiveClient.interpretNlForRecommendation(question);
        NlBridgeInterpretation interp;
        String replySource;
        if (ai.isPresent()) {
            interp = ai.get();
            replySource = "DOUBAO";
        } else {
            interp = heuristicInterpret(question);
            replySource = "HEURISTIC";
        }
        sanitizeTitleKeywordsForSpecies(interp);
        enrichKeywordsFromLifeStage(interp);
        sanitizeTitleKeywordsForSpecies(interp);

        RecommendationService.RecommendationRankingOutcome rankedOutcome =
                recommendationService.recommendForUser(userId, poolSize, true);
        List<RecommendationItemDTO> ranked = rankedOutcome.items();

        List<RecommendationItemDTO> shown =
                recommendationService.filterRecommendationsByNlInterpretation(ranked, interp, safeTop);
        recommendationService.stripCommentStrongBadgeForAiGuideDisplay(shown);

        NlRecommendResponse out = new NlRecommendResponse();
        out.setInterpretation(interp);
        out.setCandidateProducts(shown);
        out.setReplySource(replySource);
        out.setRankingBackend(rankedOutcome.rankingSource());
        return out;
    }

    /** 去掉与 petSpecies 冲突的关键词（避免「幼犬」命中幼犬粮）。 */
    private static void sanitizeTitleKeywordsForSpecies(NlBridgeInterpretation i) {
        if (i == null || i.getTitleKeywords() == null) {
            return;
        }
        String sp = i.getPetSpecies() != null ? i.getPetSpecies().trim().toLowerCase() : "unknown";
        List<String> cleaned = new ArrayList<>();
        for (String k : i.getTitleKeywords()) {
            if (k == null || k.isBlank()) {
                continue;
            }
            String t = k.trim();
            if ("cat".equals(sp)) {
                if ("幼犬".equals(t) || t.contains("犬粮") || t.contains("狗粮") || "成犬".equals(t)) {
                    continue;
                }
            } else if ("dog".equals(sp)) {
                if ("幼猫".equals(t) || t.contains("猫粮") || t.contains("猫条") || "成猫".equals(t)) {
                    continue;
                }
            }
            cleaned.add(t);
        }
        i.setTitleKeywords(cleaned);
    }

    private static void enrichKeywordsFromLifeStage(NlBridgeInterpretation i) {
        if (i == null || i.getLifeStage() == null || i.getLifeStage().isBlank()) {
            return;
        }
        List<String> kws = i.getTitleKeywords();
        if (kws == null) {
            kws = new ArrayList<>();
            i.setTitleKeywords(kws);
        }
        String ls = i.getLifeStage().trim().toLowerCase();
        if (!"puppy_kitten".equals(ls)) {
            if ("senior".equals(ls)) {
                addUnique(kws, "老年");
                addUnique(kws, "高龄");
            }
            return;
        }
        String sp = i.getPetSpecies() != null ? i.getPetSpecies().trim().toLowerCase() : "unknown";
        if ("cat".equals(sp)) {
            addUnique(kws, "幼猫");
            addUnique(kws, "幼猫粮");
            addUnique(kws, "奶糕");
        } else if ("dog".equals(sp)) {
            addUnique(kws, "幼犬");
            addUnique(kws, "幼犬粮");
            addUnique(kws, "奶糕");
        } else {
            addUnique(kws, "奶糕");
        }
    }

    private static void addUnique(List<String> list, String s) {
        if (list.stream().noneMatch(x -> s.equals(x))) {
            list.add(s);
        }
    }

    /**
     * 大模型不可用时：用轻量规则填充槽位，保证链路可用。
     */
    private static NlBridgeInterpretation heuristicInterpret(String raw) {
        String q = raw == null ? "" : raw.trim();
        NlBridgeInterpretation i = new NlBridgeInterpretation();
        if (q.contains("猫") || q.contains("喵")) {
            i.setPetSpecies("cat");
        } else if (q.contains("狗")
                || q.contains("犬")
                || q.contains("金毛")
                || q.contains("柯基")
                || q.contains("哈士奇")) {
            i.setPetSpecies("dog");
        } else {
            i.setPetSpecies("unknown");
        }
        if (q.contains("零食") || q.contains("罐头") || q.contains("冻干")) {
            i.setProductIntent("snack");
        } else if (q.contains("用品") || q.contains("玩具") || q.contains("牵引")) {
            i.setProductIntent("supply");
        } else {
            i.setProductIntent("staple_food");
        }
        if (q.contains("幼") || q.contains("小狗") || q.contains("小猫") || q.contains("奶猫") || q.contains("奶狗")) {
            i.setLifeStage("puppy_kitten");
        } else if (q.contains("老") || q.contains("高龄")) {
            i.setLifeStage("senior");
        } else {
            i.setLifeStage("adult");
        }
        List<String> kws = new ArrayList<>();
        String[] breeds = {"金毛", "柯基", "哈士奇", "泰迪", "英短", "美短", "布偶"};
        for (String b : breeds) {
            if (q.contains(b)) {
                kws.add(b);
            }
        }
        i.setTitleKeywords(kws);
        i.setModelQuerySummary("规则兜底：关键词识别犬/猫、主粮/零食及品种词");
        i.setAssistantMessage(
                "当前使用规则解析您的问题（大模型未启用或不可用）。推荐分已优先请求在线 XGB；若服务未开则使用离线表。结果已按犬猫类目与标题做了筛选。");
        return i;
    }
}
