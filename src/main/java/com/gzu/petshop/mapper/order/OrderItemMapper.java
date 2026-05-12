package com.gzu.petshop.mapper.order;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gzu.petshop.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {

    /**
     * 与 {@code export_orders_from_mysql.py} 中 DISTINCT 正样本一致：有效订单下去重后的「买过几种商品」。
     */
    @Select(
            "SELECT COUNT(*) FROM ("
                    + "SELECT DISTINCT oi.product_id FROM order_item oi "
                    + "INNER JOIN orders o ON o.order_id = oi.order_id "
                    + "WHERE o.user_id = #{userId} AND o.status IN ('PAID', 'SHIPPED', 'COMPLETED')"
                    + ") t"
    )
    Integer countDistinctPurchasedProducts(@Param("userId") Long userId);
}

