package com.gzu.petshop.dto.user;

/**
 * 用户订单详情查询结果（区分不存在与无权限）。
 */
public class OrderDetailQueryResult {
    private UserOrderDetailDTO data;
    private String error;

    public static OrderDetailQueryResult ok(UserOrderDetailDTO data) {
        OrderDetailQueryResult r = new OrderDetailQueryResult();
        r.setData(data);
        return r;
    }

    public static OrderDetailQueryResult notFound() {
        OrderDetailQueryResult r = new OrderDetailQueryResult();
        r.setError("NOT_FOUND");
        return r;
    }

    public static OrderDetailQueryResult forbidden() {
        OrderDetailQueryResult r = new OrderDetailQueryResult();
        r.setError("FORBIDDEN");
        return r;
    }

    public UserOrderDetailDTO getData() {
        return data;
    }

    public void setData(UserOrderDetailDTO data) {
        this.data = data;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
