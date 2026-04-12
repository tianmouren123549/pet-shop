package com.gzu.petshop.dto.admin;

/**
 * 管理端订单列表行：与前端 {@code AdminOrdersView} 及 mock {@code toOrderSummary} 字段一致。
 */
public class AdminOrderSummaryDTO {
    private Long orderId;
    private String orderNo;
    private Long userId;
    /** 用户昵称（展示用；无则前端可回退「用户」+ userId） */
    private String userNickname;
    /** 实付金额，两位小数字符串 */
    private String payAmount;
    private String status;
    /** ISO-8601 本地时间，便于 {@code new Date(...)} */
    private String createdAt;
    /** 全订单商品件数合计 */
    private Integer itemCount;

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

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getItemCount() {
        return itemCount;
    }

    public void setItemCount(Integer itemCount) {
        this.itemCount = itemCount;
    }
}
