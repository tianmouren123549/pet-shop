package com.gzu.petshop.controller.admin;

import com.gzu.petshop.common.Result;
import com.gzu.petshop.dto.admin.AdminAccountStatusRequest;
import com.gzu.petshop.dto.admin.AdminMerchantListItemDTO;
import com.gzu.petshop.dto.admin.AdminPasswordResetRequest;
import com.gzu.petshop.service.account.AdminAccountService;
import com.gzu.petshop.service.audit.AdminAuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端商家账号（与 {@code /api/merchant} 商家自助接口区分，仅平台管理员可访问）。
 */
@RestController
@RequestMapping("/api/admin/merchants")
@CrossOrigin
public class AdminMerchantAccountController {
    private final AdminAccountService adminAccountService;
    private final AdminAuditService adminAuditService;

    public AdminMerchantAccountController(AdminAccountService adminAccountService, AdminAuditService adminAuditService) {
        this.adminAccountService = adminAccountService;
        this.adminAuditService = adminAuditService;
    }

    @GetMapping
    public Result<List<AdminMerchantListItemDTO>> list() {
        return Result.success(adminAccountService.listMerchants());
    }

    @PutMapping("/{merchantId}/status")
    public Result<Void> updateStatus(HttpServletRequest request,
                                     @PathVariable Long merchantId,
                                     @RequestBody AdminAccountStatusRequest req) {
        String err = adminAccountService.updateMerchantStatus(merchantId, req != null ? req.getStatus() : null);
        if (err == null) {
            Long aid = adminAuditService.currentAdminId(request);
            Map<String, Object> detail = new LinkedHashMap<>();
            if (req != null && req.getStatus() != null) {
                detail.put("newStatus", req.getStatus());
            }
            adminAuditService.log(aid, AdminAuditService.ACTION_MERCHANT_STATUS_CHANGE,
                    AdminAuditService.TARGET_MERCHANT, merchantId, detail);
        }
        return err == null ? Result.success() : Result.error(err);
    }

    @PutMapping("/{merchantId}/password")
    public Result<Void> resetPassword(HttpServletRequest request,
                                      @PathVariable Long merchantId,
                                      @RequestBody AdminPasswordResetRequest req) {
        String pwd = req != null ? req.getNewPassword() : null;
        String err = adminAccountService.resetMerchantPassword(merchantId, pwd);
        if (err == null) {
            Long aid = adminAuditService.currentAdminId(request);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("passwordReset", true);
            adminAuditService.log(aid, AdminAuditService.ACTION_MERCHANT_PASSWORD_RESET,
                    AdminAuditService.TARGET_MERCHANT, merchantId, detail);
        }
        return err == null ? Result.success() : Result.error(err);
    }
}
