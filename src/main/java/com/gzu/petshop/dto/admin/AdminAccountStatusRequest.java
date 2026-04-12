package com.gzu.petshop.dto.admin;

/**
 * 启用/禁用账号：status 为 1 或 0。
 */
public class AdminAccountStatusRequest {
    private Integer status;

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
