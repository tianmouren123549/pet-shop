package com.gzu.petshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("order_item")
public class OrderItem {
    @TableId(value = "order_item_id", type = IdType.AUTO)
    private Long orderItemId;
    
    private Long orderId;
    private Long productId;
    /** 下单时商家快照，与 {@code product.merchant_id} 一致 */
    private Long merchantId;
    private Integer quantity;
    private BigDecimal itemPrice;
}
