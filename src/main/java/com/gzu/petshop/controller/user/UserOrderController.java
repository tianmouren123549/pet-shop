package com.gzu.petshop.controller.user;

import com.gzu.petshop.common.Result;
import com.gzu.petshop.dto.user.CreateOrderResponse;
import com.gzu.petshop.dto.user.OrderDetailQueryResult;
import com.gzu.petshop.dto.user.UserCreateFromCartRequest;
import com.gzu.petshop.dto.user.UserCreateOrderDirectRequest;
import com.gzu.petshop.dto.user.UserIdRequest;
import com.gzu.petshop.dto.user.UserOrderDetailDTO;
import com.gzu.petshop.dto.user.UserOrderSummaryDTO;
import com.gzu.petshop.service.order.UserOrderService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户端订单接口，路径与前端 {@code request.js} 中 {@code /api/orders} 一致。
 */
@RestController
@RequestMapping("/api/orders")
@CrossOrigin
public class UserOrderController {
    private final UserOrderService userOrderService;

    public UserOrderController(UserOrderService userOrderService) {
        this.userOrderService = userOrderService;
    }

    @GetMapping("/user/{userId}")
    public Result<List<UserOrderSummaryDTO>> listByUser(@PathVariable Long userId) {
        return Result.success(userOrderService.listOrders(userId));
    }

    @GetMapping("/{orderId}")
    public Result<UserOrderDetailDTO> getDetail(@PathVariable Long orderId, @RequestParam Long userId) {
        OrderDetailQueryResult r = userOrderService.loadOrderDetail(orderId, userId);
        if ("NOT_FOUND".equals(r.getError())) {
            return Result.error("订单不存在");
        }
        if ("FORBIDDEN".equals(r.getError())) {
            return Result.error("无权限查看该订单");
        }
        return Result.success(r.getData());
    }

    @PostMapping("/create-from-cart")
    public Result<Map<String, Long>> createFromCart(@RequestBody UserCreateFromCartRequest body) {
        Long userId = body != null ? body.getUserId() : null;
        Long merchantId = body != null ? body.getMerchantId() : null;
        CreateOrderResponse r = (merchantId != null && merchantId > 0)
                ? userOrderService.createFromCartForMerchant(userId, merchantId)
                : userOrderService.createFromCart(userId);
        if (!r.isOk()) {
            return Result.error(r.getError());
        }
        return Result.success(Map.of("orderId", r.getOrderId()));
    }

    @PostMapping("/create-direct")
    public Result<Map<String, Long>> createDirect(@RequestBody UserCreateOrderDirectRequest body) {
        CreateOrderResponse r = userOrderService.createDirect(body);
        if (!r.isOk()) {
            return Result.error(r.getError());
        }
        return Result.success(Map.of("orderId", r.getOrderId()));
    }

    @PostMapping("/{orderId}/pay")
    public Result<Void> pay(@PathVariable Long orderId, @RequestBody UserIdRequest body) {
        Long userId = body != null ? body.getUserId() : null;
        String err = userOrderService.payOrder(orderId, userId);
        return err == null ? Result.success() : Result.error(err);
    }

    @PostMapping("/{orderId}/cancel")
    public Result<Void> cancel(@PathVariable Long orderId, @RequestBody UserIdRequest body) {
        Long userId = body != null ? body.getUserId() : null;
        String err = userOrderService.cancelOrder(orderId, userId);
        return err == null ? Result.success() : Result.error(err);
    }

    @PostMapping("/{orderId}/confirm")
    public Result<Void> confirm(@PathVariable Long orderId, @RequestBody UserIdRequest body) {
        Long userId = body != null ? body.getUserId() : null;
        String err = userOrderService.confirmOrder(orderId, userId);
        return err == null ? Result.success() : Result.error(err);
    }
}
