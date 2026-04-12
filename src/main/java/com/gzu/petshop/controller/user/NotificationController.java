package com.gzu.petshop.controller.user;

import com.gzu.petshop.common.Result;
import com.gzu.petshop.dto.common.NotificationViewDTO;
import com.gzu.petshop.dto.common.RestockSubscribeOutcome;
import com.gzu.petshop.dto.common.RestockSubscribeRequest;
import com.gzu.petshop.dto.user.UserIdRequest;
import com.gzu.petshop.service.support.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户通知与到货订阅。
 */
@RestController
@RequestMapping("/api/notifications")
@CrossOrigin
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/user/{userId}")
    public Result<List<NotificationViewDTO>> listByUser(@PathVariable Long userId) {
        if (userId == null || userId <= 0) {
            return Result.error("请先登录");
        }
        return Result.success(notificationService.listForUser(userId));
    }

    @PutMapping("/{noticeId}/read")
    public Result<Void> markRead(@PathVariable Long noticeId, @RequestBody UserIdRequest body) {
        Long userId = body != null ? body.getUserId() : null;
        String err = notificationService.markUserRead(noticeId, userId);
        return err == null ? Result.success() : Result.error(err);
    }

    @PostMapping("/restock-subscribe")
    public Result<Void> restockSubscribe(@RequestBody RestockSubscribeRequest body) {
        RestockSubscribeOutcome out = notificationService.subscribeRestock(body);
        if (out.errorMessage() != null) {
            return Result.error(out.errorMessage());
        }
        return Result.successWithMessage(out.successMessage());
    }
}
