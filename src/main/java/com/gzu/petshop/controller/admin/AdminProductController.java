package com.gzu.petshop.controller.admin;

import com.gzu.petshop.common.Result;
import com.gzu.petshop.dto.admin.AdminNotifyRestockRequest;
import com.gzu.petshop.dto.admin.AdminProductListItemDTO;
import com.gzu.petshop.dto.admin.AdminProductUpdateRequest;
import com.gzu.petshop.service.AdminProductService;
import com.gzu.petshop.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/products")
@CrossOrigin
public class AdminProductController {
    private final AdminProductService adminProductService;
    private final NotificationService notificationService;

    public AdminProductController(AdminProductService adminProductService,
                                  NotificationService notificationService) {
        this.adminProductService = adminProductService;
        this.notificationService = notificationService;
    }

    @GetMapping
    public Result<List<AdminProductListItemDTO>> getAllProducts() {
        return Result.success(adminProductService.listProductsForAdmin());
    }

    @PutMapping("/{productId}")
    public Result<Void> updateProduct(@PathVariable Long productId, @RequestBody AdminProductUpdateRequest req) {
        boolean ok = adminProductService.updateProduct(productId, req);
        return ok ? Result.success() : Result.error("商品不存在");
    }

    /**
     * 向商品所属商家发送补货提醒通知（写入 {@code notification}）。
     */
    @PostMapping("/{productId}/notify-restock")
    public Result<Void> notifyRestock(@PathVariable Long productId,
                                      @RequestBody(required = false) AdminNotifyRestockRequest req) {
        String err = notificationService.adminNotifyMerchantRestock(productId, req);
        return err == null ? Result.success() : Result.error(err);
    }
}

