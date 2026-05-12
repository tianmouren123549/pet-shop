package com.gzu.petshop.controller.admin;

import com.gzu.petshop.common.Result;
import com.gzu.petshop.dto.admin.AdminAccountStatusRequest;
import com.gzu.petshop.dto.admin.AdminPasswordResetRequest;
import com.gzu.petshop.dto.admin.AdminUserListItemDTO;
import com.gzu.petshop.service.account.AdminAccountService;
import com.gzu.petshop.service.account.UserProfileService;
import com.gzu.petshop.service.audit.AdminAuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@CrossOrigin
public class AdminUserAccountController {
    private final AdminAccountService adminAccountService;
    private final UserProfileService userProfileService;
    private final AdminAuditService adminAuditService;

    public AdminUserAccountController(
            AdminAccountService adminAccountService,
            UserProfileService userProfileService,
            AdminAuditService adminAuditService
    ) {
        this.adminAccountService = adminAccountService;
        this.userProfileService = userProfileService;
        this.adminAuditService = adminAuditService;
    }

    @GetMapping
    public Result<List<AdminUserListItemDTO>> list() {
        return Result.success(adminAccountService.listUsers());
    }

    @PutMapping("/{userId}/status")
    public Result<Void> updateStatus(HttpServletRequest request,
                                      @PathVariable Long userId,
                                      @RequestBody AdminAccountStatusRequest req) {
        String err = adminAccountService.updateUserStatus(userId, req != null ? req.getStatus() : null);
        if (err == null) {
            Long aid = adminAuditService.currentAdminId(request);
            Map<String, Object> detail = new LinkedHashMap<>();
            if (req != null && req.getStatus() != null) {
                detail.put("newStatus", req.getStatus());
            }
            adminAuditService.log(aid, AdminAuditService.ACTION_USER_STATUS_CHANGE,
                    AdminAuditService.TARGET_USER, userId, detail);
        }
        return err == null ? Result.success() : Result.error(err);
    }

    @PutMapping("/{userId}/password")
    public Result<Void> resetPassword(HttpServletRequest request,
                                      @PathVariable Long userId,
                                      @RequestBody AdminPasswordResetRequest req) {
        String pwd = req != null ? req.getNewPassword() : null;
        String err = adminAccountService.resetUserPassword(userId, pwd);
        if (err == null) {
            Long aid = adminAuditService.currentAdminId(request);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("passwordReset", true);
            adminAuditService.log(aid, AdminAuditService.ACTION_USER_PASSWORD_RESET,
                    AdminAuditService.TARGET_USER, userId, detail);
        }
        return err == null ? Result.success() : Result.error(err);
    }

    /**
     * 重置单个用户首页宠物偏好（置空），下次进入首页会再次弹「养猫/狗/都有」引导。
     */
    @PutMapping("/{userId}/pet-preference/reset")
    public Result<Void> resetPetPreference(HttpServletRequest request, @PathVariable Long userId) {
        String err = userProfileService.resetPetPreference(userId);
        if (err == null) {
            Long aid = adminAuditService.currentAdminId(request);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("petPreferenceReset", true);
            adminAuditService.log(aid, AdminAuditService.ACTION_USER_PET_PREFERENCE_RESET,
                    AdminAuditService.TARGET_USER, userId, detail);
        }
        return err == null ? Result.success() : Result.error(err);
    }

    /**
     * 批量重置全部用户首页宠物偏好（置空），用于统一重新触发首页引导。
     */
    @PutMapping("/pet-preference/reset-all")
    public Result<Map<String, Integer>> resetAllPetPreference(HttpServletRequest request) {
        int affected = userProfileService.resetPetPreferenceForAllUsers();
        Long aid = adminAuditService.currentAdminId(request);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("petPreferenceResetAll", true);
        detail.put("affectedRows", affected);
        adminAuditService.log(aid, AdminAuditService.ACTION_USER_PET_PREFERENCE_RESET_ALL,
                AdminAuditService.TARGET_USER, 0L, detail);
        return Result.success(Map.of("affectedRows", affected));
    }
}
