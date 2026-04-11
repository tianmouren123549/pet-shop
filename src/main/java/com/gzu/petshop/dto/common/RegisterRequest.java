package com.gzu.petshop.dto.common;

/**
 * 用户注册：邮箱必填且唯一；手机选填。
 */
public class RegisterRequest {
    private String nickname;
    /** 登录邮箱，必填 */
    private String email;
    /** 选填联系方式 */
    private String phone;
    private String password;

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
