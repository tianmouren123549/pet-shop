package com.gzu.petshop.controller.merchant;

import com.gzu.petshop.common.Result;
import com.gzu.petshop.dto.merchant.MerchantIdRequest;
import com.gzu.petshop.dto.common.NotificationViewDTO;
import com.gzu.petshop.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商家端通知。
 */
@RestController
@RequestMapping("/api/merchant/notifications")
@CrossOrigin
public class MerchantNotificationController {
    private final NotificationService notificationService;

    public MerchantNotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public Result<List<NotificationViewDTO>> list(@RequestParam Long merchantId) {
        if (merchantId == null || merchantId <= 0) {
            return Result.error("商家ID无效");
        }
        return Result.success(notificationService.listForMerchant(merchantId));
    }

    @PutMapping("/{noticeId}/read")
    public Result<Void> markRead(@PathVariable Long noticeId, @RequestBody MerchantIdRequest body) {
        Long merchantId = body != null ? body.getMerchantId() : null;
        String err = notificationService.markMerchantRead(noticeId, merchantId);
        return err == null ? Result.success() : Result.error(err);
    }
}
