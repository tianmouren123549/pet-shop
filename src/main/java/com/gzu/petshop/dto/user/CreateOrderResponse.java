package com.gzu.petshop.dto.user;

/**
 * 创建订单结果：成功带 {@code orderId}，失败带 {@code error} 文案。
 */
public class CreateOrderResponse {
    private Long orderId;
    private String error;

    public static CreateOrderResponse ok(Long orderId) {
        CreateOrderResponse r = new CreateOrderResponse();
        r.setOrderId(orderId);
        return r;
    }

    public static CreateOrderResponse fail(String error) {
        CreateOrderResponse r = new CreateOrderResponse();
        r.setError(error);
        return r;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public boolean isOk() {
        return error == null && orderId != null;
    }
}
