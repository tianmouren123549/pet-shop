package com.gzu.petshop.controller.admin;

import com.gzu.petshop.common.Result;
import com.gzu.petshop.dto.admin.AdminOrderDetailDTO;
import com.gzu.petshop.dto.admin.AdminOrderStatusUpdateRequest;
import com.gzu.petshop.dto.admin.AdminOrderSummaryDTO;
import com.gzu.petshop.service.audit.AdminAuditService;
import com.gzu.petshop.service.order.AdminOrderService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/orders")
@CrossOrigin
public class AdminOrderController {
    private final AdminOrderService adminOrderService;
    private final AdminAuditService adminAuditService;

    public AdminOrderController(AdminOrderService adminOrderService, AdminAuditService adminAuditService) {
        this.adminOrderService = adminOrderService;
        this.adminAuditService = adminAuditService;
    }

    @GetMapping
    public Result<List<AdminOrderSummaryDTO>> getOrders(@RequestParam(required = false) String status) {
        return Result.success(adminOrderService.listOrders(status));
    }

    @GetMapping("/{orderId}")
    public Result<AdminOrderDetailDTO> getOrderDetail(@PathVariable Long orderId) {
        AdminOrderDetailDTO d = adminOrderService.getOrderDetail(orderId);
        return d == null ? Result.error("订单不存在") : Result.success(d);
    }

    @PutMapping("/{orderId}/status")
    public Result<Void> updateStatus(HttpServletRequest request,
                                      @PathVariable Long orderId,
                                      @RequestBody AdminOrderStatusUpdateRequest req) {
        String from = adminOrderService.getOrderCurrentStatus(orderId);
        String err = adminOrderService.updateStatus(orderId, req);
        if (err == null) {
            Long aid = adminAuditService.currentAdminId(request);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("fromStatus", from != null ? from : "");
            if (req != null && req.getStatus() != null) {
                detail.put("toStatus", req.getStatus().trim().toUpperCase());
            }
            if (req != null && req.getStatusReason() != null && !req.getStatusReason().isBlank()) {
                detail.put("statusReason", req.getStatusReason().trim());
            }
            adminAuditService.log(aid, AdminAuditService.ACTION_ORDER_STATUS_CHANGE,
                    AdminAuditService.TARGET_ORDER, orderId, detail);
        }
        return err == null ? Result.success() : Result.error(err);
    }

    /**
     * 对已支付、待发货订单发起催发货：向订单内各商家写入站内通知（不改变订单状态）。
     */
    @PostMapping("/{orderId}/urge-shipment")
    public Result<Void> urgeShipment(HttpServletRequest request, @PathVariable Long orderId) {
        String err = adminOrderService.urgeShipment(orderId);
        if (err == null) {
            Long aid = adminAuditService.currentAdminId(request);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("orderId", orderId);
            adminAuditService.log(aid, AdminAuditService.ACTION_ORDER_URGE_SHIPMENT,
                    AdminAuditService.TARGET_ORDER, orderId, detail);
        }
        return err == null ? Result.successWithMessage("已通知商家尽快发货") : Result.error(err);
    }
}

