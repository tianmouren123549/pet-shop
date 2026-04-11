package com.gzu.petshop.dto.user;

/**
 * 用户身份请求体（支付/取消/确认收货等接口与前端 {@code { userId }} 一致）。
 */
public class UserIdRequest {
    private Long userId;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
