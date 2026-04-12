package com.gzu.petshop.controller.admin;

import com.gzu.petshop.common.Result;
import com.gzu.petshop.dto.admin.AdminAuditLogPageDTO;
import com.gzu.petshop.service.audit.AdminAuditService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端操作审计查询。
 */
@RestController
@RequestMapping("/api/admin/audit-logs")
@CrossOrigin
public class AdminAuditLogController {

    private final AdminAuditService adminAuditService;

    public AdminAuditLogController(AdminAuditService adminAuditService) {
        this.adminAuditService = adminAuditService;
    }

    @GetMapping
    public Result<AdminAuditLogPageDTO> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(adminAuditService.page(page, pageSize));
    }
}
