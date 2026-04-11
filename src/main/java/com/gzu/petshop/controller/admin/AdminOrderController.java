package com.gzu.petshop.controller.admin;

import com.gzu.petshop.common.Result;
import com.gzu.petshop.dto.admin.AdminOrderStatusUpdateRequest;
import com.gzu.petshop.dto.admin.AdminOrderSummaryDTO;
import com.gzu.petshop.service.AdminOrderService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/orders")
@CrossOrigin
public class AdminOrderController {
    private final AdminOrderService adminOrderService;

    public AdminOrderController(AdminOrderService adminOrderService) {
        this.adminOrderService = adminOrderService;
    }

    @GetMapping
    public Result<List<AdminOrderSummaryDTO>> getOrders(@RequestParam(required = false) String status) {
        return Result.success(adminOrderService.listOrders(status));
    }

    @PutMapping("/{orderId}/status")
    public Result<Void> updateStatus(@PathVariable Long orderId, @RequestBody AdminOrderStatusUpdateRequest req) {
        String err = adminOrderService.updateStatus(orderId, req);
        return err == null ? Result.success() : Result.error(err);
    }
}

