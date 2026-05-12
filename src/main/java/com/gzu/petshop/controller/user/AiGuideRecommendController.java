package com.gzu.petshop.controller.user;

import com.gzu.petshop.common.Result;
import com.gzu.petshop.dto.recommend.AiGuideCommentRerankRequest;
import com.gzu.petshop.dto.recommend.AiGuideRerankOutcome;
import com.gzu.petshop.dto.user.RecommendationItemDTO;
import com.gzu.petshop.security.UserPrincipalUtil;
import com.gzu.petshop.service.recommend.RecommendationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 智能导购专用：对 AI 当前候选在子集内按与首页一致的 XGB（在线/离线）分与评论、行为乘子重排。
 */
@RestController
@RequestMapping("/api/recommendations/ai-guide")
@CrossOrigin
public class AiGuideRecommendController {

    private final RecommendationService recommendationService;

    public AiGuideRecommendController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @PostMapping("/user/{userId}/comment-rerank")
    public Result<List<RecommendationItemDTO>> commentRerank(
            HttpServletRequest request,
            @PathVariable Long userId,
            @RequestBody(required = false) AiGuideCommentRerankRequest body
    ) {
        String deny = UserPrincipalUtil.requireSameUser(request, userId);
        if (deny != null) {
            return deny.contains("登录") ? Result.error(401, deny) : Result.error(403, deny);
        }
        List<Long> ids = body != null ? body.getProductIds() : List.of();
        if (ids == null || ids.isEmpty()) {
            return Result.error("请先展示 AI 推荐商品，或传入 productIds");
        }
        Long sessionId = body != null ? body.getSessionId() : null;
        AiGuideRerankOutcome outcome = recommendationService.rerankAiGuideCandidatesByReviews(userId, ids, sessionId);
        if (!outcome.isOk()) {
            return Result.error(outcome.getErrorCode(), outcome.getMessage());
        }
        return Result.success(outcome.getItems());
    }
}
