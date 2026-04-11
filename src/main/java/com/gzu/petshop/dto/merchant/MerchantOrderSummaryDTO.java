package com.gzu.petshop.dto.merchant;

/**
 * 商家端订单列表行，字段与前端 {@code MerchantOrdersView} / mock {@code toOrderSummary} 对齐。
 */
public class MerchantOrderSummaryDTO {
    private Long orderId;
    private String orderNo;
    private Long userId;
    /** 实付金额，两位小数字符串 */
    private String payAmount;
    private String status;
    /** 创建时间，ISO-8601 本地时间字符串，便于 {@code new Date(...)} 解析 */
    private String createdAt;
    /** 订单内全部明细行数量之和（与 mock 一致：全订单件数，非仅本商家） */
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
