package com.gzu.petshop.dto.merchant;

public class MerchantProfileUpdateRequest {
    private String shopName;
    private String contactName;
    private String phone;
    private String email;
    private String avatarUrl;
    /**
     * 每周销售额目标（元）。非 null 时解析：空串表示清空目标；否则须为非负数字字符串。
     */
    private String salesTargetWeekly;

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

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getSalesTargetWeekly() {
        return salesTargetWeekly;
    }

    public void setSalesTargetWeekly(String salesTargetWeekly) {
        this.salesTargetWeekly = salesTargetWeekly;
    }
}

