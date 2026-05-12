package com.gzu.petshop.controller.user;

import com.gzu.petshop.common.Result;
import com.gzu.petshop.service.user.UserEventLogService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user/events")
@CrossOrigin
public class UserEventController {
    private final UserEventLogService userEventLogService;

    public UserEventController(UserEventLogService userEventLogService) {
        this.userEventLogService = userEventLogService;
    }

    /**
     * 上报用户行为（浏览/点击等），用于在线个性化重排推荐。
     * 请求体：{@code userId}、{@code eventType}（view/click/add_cart/buy）、{@code productId}（可选）、{@code sessionId}（可选）。
     */
    @PostMapping
    public Result<Void> postEvent(@RequestBody Map<String, Object> body) {
        if (body == null || body.get("userId") == null || body.get("eventType") == null) {
            return Result.error("参数无效");
        }
        try {
            Long userId = Long.valueOf(body.get("userId").toString());
            String eventType = String.valueOf(body.get("eventType"));
            Long productId = null;
            if (body.get("productId") != null) {
                productId = Long.valueOf(body.get("productId").toString());
            }
            String sessionId = body.get("sessionId") != null ? String.valueOf(body.get("sessionId")) : null;
            userEventLogService.tryRecord(userId, eventType, productId, sessionId);
            return Result.success();
        } catch (NumberFormatException e) {
            return Result.error("参数格式错误");
        }
    }
}
