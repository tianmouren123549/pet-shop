package com.gzu.petshop.service.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gzu.petshop.common.Result;
import com.gzu.petshop.dto.admin.AdminLoginRequest;
import com.gzu.petshop.entity.AdminUser;
import com.gzu.petshop.mapper.admin.AdminUserMapper;
import com.gzu.petshop.security.JwtService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AdminAuthService {
    private final AdminUserMapper adminUserMapper;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AdminAuthService(AdminUserMapper adminUserMapper, JwtService jwtService) {
        this.adminUserMapper = adminUserMapper;
        this.jwtService = jwtService;
    }

    @Transactional
    public Result<Object> login(AdminLoginRequest req) {
        if (req == null || req.getUsername() == null || req.getUsername().isBlank()) {
            return Result.error("管理员账号不能为空");
        }
        if (req.getPassword() == null || req.getPassword().isBlank()) {
            return Result.error("管理员密码不能为空");
        }

        AdminUser admin = adminUserMapper.selectOne(
                new QueryWrapper<AdminUser>().eq("username", req.getUsername().trim())
        );
        if (admin == null || admin.getStatus() == null || admin.getStatus() == 0) {
            return Result.error("管理员账号不存在或已禁用");
        }

        String storedHash = admin.getPasswordHash();
        String rawPassword = req.getPassword();
        boolean ok;
        try {
            ok = storedHash != null && storedHash.startsWith("$2")
                    ? passwordEncoder.matches(rawPassword, storedHash)
                    : storedHash != null && storedHash.equals(rawPassword);
        } catch (Exception e) {
            ok = storedHash != null && storedHash.equals(rawPassword);
        }

        if (!ok) {
            return Result.error("管理员账号或密码错误");
        }

        admin.setLastLoginAt(LocalDateTime.now());
        adminUserMapper.updateById(admin);
        String jwtRole = admin.getRole() != null && !admin.getRole().isBlank() ? admin.getRole().trim() : "OPERATOR";
        AdminLoginResult dto = new AdminLoginResult(admin.getAdminId(), admin.getUsername(), admin.getRole());
        dto.setToken(jwtService.createAccessToken(jwtRole, admin.getAdminId()));
        return Result.success(dto);
    }

    public static class AdminLoginResult {
        private Long adminId;
        private String username;
        private String role;
        /** JWT 访问令牌（claim role 与库中管理员角色一致：SUPER / OPERATOR） */
        private String token;

        public AdminLoginResult(Long adminId, String username, String role) {
            this.adminId = adminId;
            this.username = username;
            this.role = role;
        }

        public Long getAdminId() {
            return adminId;
        }

        public String getUsername() {
            return username;
        }

        public String getRole() {
            return role;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }
}

