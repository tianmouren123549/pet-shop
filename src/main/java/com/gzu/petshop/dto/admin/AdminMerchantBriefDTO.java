package com.gzu.petshop.dto.admin;

/**
 * 管理端展示的商家基本信息（订单涉及店铺快照）。
 */
public class AdminMerchantBriefDTO {
    private Long merchantId;
    private String shopName;
    private String contactName;
    private String phone;

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
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
}
