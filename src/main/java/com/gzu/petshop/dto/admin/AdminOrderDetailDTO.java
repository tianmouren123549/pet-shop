package com.gzu.petshop.dto.admin;

import java.util.List;

/**
 * 管理端订单详情：订单信息 + 下单用户基本信息 + 本单涉及商家 + 明细（含各商品所属商家）。
 */
public class AdminOrderDetailDTO {
    private Long orderId;
    private String orderNo;
    private Long userId;
    private String userNickname;
    private String userEmail;
    private String userPhone;
    private String payAmount;
    private String status;
    private String statusReason;
    private String createdAt;
    private String updatedAt;
    private Integer itemCount;
    /** 本单涉及的去重商家（基本信息） */
    private List<AdminMerchantBriefDTO> merchants;
    private List<AdminOrderLineDTO> items;

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserNickname() {
        return userNickname;
    }

    public void setUserNickname(String userNickname) {
        this.userNickname = userNickname;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getUserPhone() {
        return userPhone;
    }

    public void setUserPhone(String userPhone) {
        this.userPhone = userPhone;
    }

    public String getPayAmount() {
        return payAmount;
    }

    public void setPayAmount(String payAmount) {
        this.payAmount = payAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public void setStatusReason(String statusReason) {
        this.statusReason = statusReason;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getItemCount() {
        return itemCount;
    }

    public void setItemCount(Integer itemCount) {
        this.itemCount = itemCount;
    }

    public List<AdminMerchantBriefDTO> getMerchants() {
        return merchants;
    }

    public void setMerchants(List<AdminMerchantBriefDTO> merchants) {
        this.merchants = merchants;
    }

    public List<AdminOrderLineDTO> getItems() {
        return items;
    }

    public void setItems(List<AdminOrderLineDTO> items) {
        this.items = items;
    }
}
