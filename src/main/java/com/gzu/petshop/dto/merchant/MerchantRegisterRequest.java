package com.gzu.petshop.dto.merchant;

/**
 * 商家自助注册请求体（与表 {@code merchant}、前端登录页字段一致）。
 */
public class MerchantRegisterRequest {
    /** 登录账号，唯一 */
    private String username;
    private String password;
    private String shopName;
    private String contactName;
    private String phone;
    private String email;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getShopName() {
        return shopName;
    }

    public void setShopName(String shopName) {
        this.shopName = shopName;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
