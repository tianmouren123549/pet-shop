package com.gzu.petshop.dto.admin;

/**
 * 管理端重置密码（bcrypt 入库）。
 */
public class AdminPasswordResetRequest {
    private String newPassword;

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
