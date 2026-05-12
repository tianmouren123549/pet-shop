package com.gzu.petshop.controller.merchant;

import com.gzu.petshop.common.Result;
import com.gzu.petshop.dto.merchant.MerchantOrderDetailDTO;
import com.gzu.petshop.dto.merchant.MerchantOrderStatusUpdateRequest;
import com.gzu.petshop.dto.merchant.MerchantOrderSummaryDTO;
import com.gzu.petshop.dto.merchant.MerchantOrderTodoBadgesDTO;
import com.gzu.petshop.service.order.MerchantOrderService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商家端订单接口，路径与前端 {@code /api/merchant/orders} 一致。
 */
@RestController
@RequestMapping("/api/merchant/orders")
@CrossOrigin
public class MerchantOrderController {
    private final MerchantOrderService merchantOrderService;

    public MerchantOrderController(MerchantOrderService merchantOrderService) {
        this.merchantOrderService = merchantOrderService;
    }

    @GetMapping
    public Result<List<MerchantOrderSummaryDTO>> getOrders(
            @RequestParam Long merchantId,
            @RequestParam(required = false) String status) {
        if (merchantId == null || merchantId <= 0) {
            return Result.error("商家ID无效");
        }
        return Result.success(merchantOrderService.listOrders(merchantId, status));
    }

    /**
     * 订单详情（仅本店明细）；路径使用数字约束，避免与 {@code /todo-badges} 冲突。
     */
    @GetMapping("/{orderId:\\d+}")
    public Result<MerchantOrderDetailDTO> getOrderDetail(
            @PathVariable Long orderId,
            @RequestParam Long merchantId) {
        if (merchantId == null || merchantId <= 0) {
            return Result.error("商家ID无效");
        }
        MerchantOrderDetailDTO d = merchantOrderService.getOrderDetail(merchantId, orderId);
        return d == null ? Result.error("订单不存在或无权查看") : Result.success(d);
    }

    @GetMapping("/todo-badges")
    public Result<MerchantOrderTodoBadgesDTO> todoBadges(@RequestParam Long merchantId) {
        if (merchantId == null || merchantId <= 0) {
            return Result.error("商家ID无效");
        }
        return Result.success(merchantOrderService.todoBadges(merchantId));
    }

    @PutMapping("/{orderId}/status")
    public Result<Void> updateStatus(@PathVariable Long orderId, @RequestBody MerchantOrderStatusUpdateRequest req) {
        String err = merchantOrderService.updateOrderStatus(orderId, req);
        return err == null ? Result.success() : Result.error(err);
    }
}
