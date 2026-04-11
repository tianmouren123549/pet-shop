package com.gzu.petshop.controller.admin;

import com.gzu.petshop.common.Result;
import com.gzu.petshop.dto.admin.AdminLoginRequest;
import com.gzu.petshop.service.AdminAuthService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/auth")
@CrossOrigin
public class AdminAuthController {
    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/login")
    public Result<Object> login(@RequestBody AdminLoginRequest req) {
        return adminAuthService.login(req);
    }
}

