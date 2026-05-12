package com.gzu.petshop.controller.user;

import com.gzu.petshop.common.Result;
import com.gzu.petshop.dto.recommend.NlRecommendRequest;
import com.gzu.petshop.dto.recommend.NlRecommendResponse;
import com.gzu.petshop.security.UserPrincipalUtil;
import com.gzu.petshop.service.recommend.NlRecommendationBridgeService;
import com.gzu.petshop.service.recommend.RecommendationScriptRefreshService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 自然语言问荐：LLM 将口语转为槽位，再在既有推荐模型排序结果上过滤返回。
 */
@RestController
@RequestMapping("/api/recommendations/nl")
@CrossOrigin
public class NlRecommendController {

    private final NlRecommendationBridgeService nlRecommendationBridgeService;
    private final RecommendationScriptRefreshService recommendationScriptRefreshService;

    public NlRecommendController(
            NlRecommendationBridgeService nlRecommendationBridgeService,
            RecommendationScriptRefreshService recommendationScriptRefreshService
    ) {
        this.nlRecommendationBridgeService = nlRecommendationBridgeService;
        this.recommendationScriptRefreshService = recommendationScriptRefreshService;
    }

    @PostMapping("/user/{userId}/query")
    public Result<NlRecommendResponse> query(
            HttpServletRequest request,
            @PathVariable Long userId,
            @RequestBody(required = false) NlRecommendRequest body
    ) {
        String deny = UserPrincipalUtil.requireSameUser(request, userId);
        if (deny != null) {
            return deny.contains("登录") ? Result.error(401, deny) : Result.error(403, deny);
        }
        String q = body != null ? body.getQuestion() : null;
        if (q == null || q.isBlank()) {
            return Result.error("请输入问题");
        }
        Integer topN = body != null ? body.getTopN() : null;
        int tn = topN == null ? 5 : Math.max(1, Math.min(5, topN));
        recommendationScriptRefreshService.notifyRecommendationApiCalled();
        NlRecommendResponse payload = nlRecommendationBridgeService.query(userId, q.trim(), tn);
        return Result.success(payload);
    }
}
