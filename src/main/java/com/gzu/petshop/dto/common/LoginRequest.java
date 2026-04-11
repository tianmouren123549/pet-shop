package com.gzu.petshop.dto.common;

/**
 * 用户登录：使用邮箱 + 密码（与库表 {@code user.email} 一致）。
 */
public class LoginRequest {
    private String email;
    private String password;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
