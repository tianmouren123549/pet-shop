package com.gzu.petshop.controller.admin;

import com.gzu.petshop.common.Result;
import com.gzu.petshop.dto.admin.AdminNotifyRestockRequest;
import com.gzu.petshop.dto.admin.AdminProductListItemDTO;
import com.gzu.petshop.dto.admin.AdminProductUpdateRequest;
import com.gzu.petshop.service.audit.AdminAuditService;
import com.gzu.petshop.service.product.AdminProductService;
import com.gzu.petshop.service.support.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/products")
@CrossOrigin
public class AdminProductController {
    private final AdminProductService adminProductService;
    private final NotificationService notificationService;
    private final AdminAuditService adminAuditService;

    public AdminProductController(AdminProductService adminProductService,
                                  NotificationService notificationService,
                                  AdminAuditService adminAuditService) {
        this.adminProductService = adminProductService;
        this.notificationService = notificationService;
        this.adminAuditService = adminAuditService;
    }

    @GetMapping
    public Result<List<AdminProductListItemDTO>> getAllProducts() {
        return Result.success(adminProductService.listProductsForAdmin());
    }

    @PutMapping("/{productId}")
    public Result<Void> updateProduct(HttpServletRequest request,
                                      @PathVariable Long productId,
                                      @RequestBody AdminProductUpdateRequest req) {
        boolean ok = adminProductService.updateProduct(productId, req);
        if (ok) {
            Long aid = adminAuditService.currentAdminId(request);
            Map<String, Object> detail = new LinkedHashMap<>();
            if (req != null) {
                if (req.getPrice() != null) {
                    detail.put("price", req.getPrice().toPlainString());
                }
                if (req.getStock() != null) {
                    detail.put("stock", req.getStock());
                }
                if (req.getStatus() != null) {
                    detail.put("status", req.getStatus());
                }
            }
            adminAuditService.log(aid, AdminAuditService.ACTION_PRODUCT_UPDATE,
                    AdminAuditService.TARGET_PRODUCT, productId, detail);
        }
        return ok ? Result.success() : Result.error("商品不存在");
    }

    /**
     * 向商品所属商家发送补货提醒通知（写入 {@code notification}）。
     */
    @PostMapping("/{productId}/notify-restock")
    public Result<Void> notifyRestock(HttpServletRequest request,
                                     @PathVariable Long productId,
                                     @RequestBody(required = false) AdminNotifyRestockRequest req) {
        String err = notificationService.adminNotifyMerchantRestock(productId, req);
        if (err == null) {
            Long aid = adminAuditService.currentAdminId(request);
            Map<String, Object> detail = new LinkedHashMap<>();
            if (req != null) {
                if (req.getReason() != null && !req.getReason().isBlank()) {
                    detail.put("reason", req.getReason().trim());
                }
                if (req.getNote() != null && !req.getNote().isBlank()) {
                    detail.put("note", req.getNote().trim());
                }
            }
            adminAuditService.log(aid, AdminAuditService.ACTION_PRODUCT_NOTIFY_RESTOCK,
                    AdminAuditService.TARGET_PRODUCT, productId, detail);
        }
        return err == null ? Result.success() : Result.error(err);
    }
}

