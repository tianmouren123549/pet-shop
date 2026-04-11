package com.gzu.petshop.dto.admin;

import java.math.BigDecimal;

public class AdminProductUpdateRequest {
    private BigDecimal price;
    private Integer stock;
    private Integer status;

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}

