package com.gzu.petshop.controller.user;

import com.gzu.petshop.common.Result;
import com.gzu.petshop.dto.recommend.ProactiveSessionRestoreDTO;
import com.gzu.petshop.dto.recommend.ProactiveSessionSummaryDTO;
import com.gzu.petshop.dto.recommend.ProactiveTurnRequest;
import com.gzu.petshop.dto.recommend.ProactiveTurnResponse;
import com.gzu.petshop.security.UserPrincipalUtil;
import com.gzu.petshop.service.recommend.ProactiveRecommendService;
import com.gzu.petshop.service.recommend.RecommendationScriptRefreshService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 主动追问式推荐（与 {@code GET /api/recommendations/user/{id}} 共用推荐流水线）。
 */
@RestController
@RequestMapping("/api/recommendations/proactive")
@CrossOrigin
public class ProactiveRecommendController {

    private final ProactiveRecommendService proactiveRecommendService;
    private final RecommendationScriptRefreshService recommendationScriptRefreshService;

    public ProactiveRecommendController(
            ProactiveRecommendService proactiveRecommendService,
            RecommendationScriptRefreshService recommendationScriptRefreshService
    ) {
        this.proactiveRecommendService = proactiveRecommendService;
        this.recommendationScriptRefreshService = recommendationScriptRefreshService;
    }

    /**
     * 恢复当前用户最近一次多轮导购会话（按会话更新时间），无记录时返回空列表。
     */
    @GetMapping("/user/{userId}/latest-session")
    public Result<ProactiveSessionRestoreDTO> latestSession(HttpServletRequest request, @PathVariable Long userId) {
        String deny = UserPrincipalUtil.requireSameUser(request, userId);
        if (deny != null) {
            return deny.contains("登录") ? Result.error(401, deny) : Result.error(403, deny);
        }
        return Result.success(proactiveRecommendService.loadLatestSessionForResume(userId));
    }

    /**
     * 当前用户最近若干条导购会话（侧栏/下拉）。
     *
     * @param includeSessionId 可选：当前正在查看的会话 id；若不在最近 {@code limit} 条内，会插入结果首位并仍只返回至多 {@code limit} 条，避免旧会话从下拉中消失。
     */
    @GetMapping("/user/{userId}/sessions")
    public Result<List<ProactiveSessionSummaryDTO>> listSessions(
            HttpServletRequest request,
            @PathVariable Long userId,
            @RequestParam(name = "limit", defaultValue = "5") int limit,
            @RequestParam(name = "includeSessionId", required = false) Long includeSessionId
    ) {
        String deny = UserPrincipalUtil.requireSameUser(request, userId);
        if (deny != null) {
            return deny.contains("登录") ? Result.error(401, deny) : Result.error(403, deny);
        }
        return Result.success(proactiveRecommendService.listSessionSummaries(userId, limit, includeSessionId));
    }

    /** 按会话 id 恢复完整消息与底部候选（须本人会话）。 */
    @GetMapping("/user/{userId}/session/{sessionId}")
    public Result<ProactiveSessionRestoreDTO> sessionById(
            HttpServletRequest request,
            @PathVariable Long userId,
            @PathVariable Long sessionId
    ) {
        String deny = UserPrincipalUtil.requireSameUser(request, userId);
        if (deny != null) {
            return deny.contains("登录") ? Result.error(401, deny) : Result.error(403, deny);
        }
        return Result.success(proactiveRecommendService.restoreSessionById(userId, sessionId));
    }

    @PostMapping("/user/{userId}/turn")
    public Result<ProactiveTurnResponse> turn(
            HttpServletRequest request,
            @PathVariable Long userId,
            @RequestBody(required = false) ProactiveTurnRequest body
    ) {
        String deny = UserPrincipalUtil.requireSameUser(request, userId);
        if (deny != null) {
            return deny.contains("登录") ? Result.error(401, deny) : Result.error(403, deny);
        }
        recommendationScriptRefreshService.notifyRecommendationApiCalled();
        try {
            Long sessionId = body != null ? body.getSessionId() : null;
            String msg = body != null ? body.getUserMessage() : null;
            ProactiveTurnResponse data = proactiveRecommendService.turn(userId, sessionId, msg);
            return Result.success(data);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }
}
