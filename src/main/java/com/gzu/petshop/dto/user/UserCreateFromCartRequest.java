package com.gzu.petshop.dto.user;

/**
 * 从购物车下单请求体：与前端 {@code { userId, merchantId? }} 一致。
 * {@code merchantId} 有值时仅结算该商家下的购物车行，其余行保留。
 */
public class UserCreateFromCartRequest {
    private Long userId;
    /** 可选；指定时只结算该 {@code product.merchant_id} 对应的行 */
    private Long merchantId;
    /** 收货人；可空，默认用户昵称 */
    private String receiverName;
    /** 联系电话；可空，默认用户资料手机号 */
    private String receiverPhone;
    /** 省市区 */
    private String receiverRegion;
    /** 详细地址 */
    private String receiverAddress;
    /** 选用地址簿：传入后优先于手写收件信息 */
    private Long addressId;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName;
    }

    public String getReceiverPhone() {
        return receiverPhone;
    }

    public void setReceiverPhone(String receiverPhone) {
        this.receiverPhone = receiverPhone;
    }

    public String getReceiverRegion() {
        return receiverRegion;
    }

    public void setReceiverRegion(String receiverRegion) {
        this.receiverRegion = receiverRegion;
    }

    public String getReceiverAddress() {
        return receiverAddress;
    }

    public void setReceiverAddress(String receiverAddress) {
        this.receiverAddress = receiverAddress;
    }

    public Long getAddressId() {
        return addressId;
    }

    public void setAddressId(Long addressId) {
        this.addressId = addressId;
    }
}
