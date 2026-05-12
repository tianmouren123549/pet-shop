package com.gzu.petshop.controller.user;

import com.gzu.petshop.common.Result;
import com.gzu.petshop.dto.user.RecommendationItemDTO;
import com.gzu.petshop.security.JwtAuthFilter;
import com.gzu.petshop.service.recommend.RecommendationScriptRefreshService;
import com.gzu.petshop.service.recommend.RecommendationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
@CrossOrigin
public class RecommendationController {
    private final RecommendationService recommendationService;
    private final RecommendationScriptRefreshService recommendationScriptRefreshService;

    public RecommendationController(
            RecommendationService recommendationService,
            RecommendationScriptRefreshService recommendationScriptRefreshService
    ) {
        this.recommendationService = recommendationService;
        this.recommendationScriptRefreshService = recommendationScriptRefreshService;
    }

    @GetMapping("/user/{userId}")
    public Result<List<RecommendationItemDTO>> listByUser(
            HttpServletRequest request,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "10") Integer topN
    ) {
        if (userId == null || userId <= 0) {
            return Result.error("请先登录");
        }
        Object principal = request != null ? request.getAttribute(JwtAuthFilter.ATTR_PRINCIPAL_ID) : null;
        Long loginUserId = null;
        if (principal instanceof Long l) {
            loginUserId = l;
        } else if (principal != null) {
            try {
                loginUserId = Long.valueOf(principal.toString());
            } catch (NumberFormatException ignored) {
                return Result.error(401, "请先登录");
            }
        }
        if (loginUserId == null || loginUserId <= 0) {
            return Result.error(401, "请先登录");
        }
        if (!loginUserId.equals(userId)) {
            return Result.error(403, "禁止查询其他用户推荐");
        }
        recommendationScriptRefreshService.notifyRecommendationApiCalled();
        return Result.success(recommendationService.listByUser(userId, topN == null ? 10 : topN));
    }
}
