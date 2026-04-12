package com.gzu.petshop.service.order;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gzu.petshop.entity.Orders;
import com.gzu.petshop.mapper.order.OrdersMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 待支付订单超时关闭：创建超过指定时间仍为 {@code CREATED} 的订单置为 {@code CANCELLED}。
 * 未支付前未扣库存，故自动取消无需回滚库存。
 */
@Service
public class OrderUnpaidTimeoutService {

    /** 未支付宽限期（小时） */
    public static final int UNPAID_CANCEL_AFTER_HOURS = 1;

    private final OrdersMapper ordersMapper;

    public OrderUnpaidTimeoutService(OrdersMapper ordersMapper) {
        this.ordersMapper = ordersMapper;
    }

    /**
     * 批量关闭已超时的待支付订单。
     *
     * @return 本次更新的行数
     */
    @Transactional
    public int cancelExpiredUnpaidOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusHours(UNPAID_CANCEL_AFTER_HOURS);
        LambdaUpdateWrapper<Orders> uw = new LambdaUpdateWrapper<>();
        uw.eq(Orders::getStatus, "CREATED")
                .lt(Orders::getCreatedAt, deadline)
                .set(Orders::getStatus, "CANCELLED")
                .set(Orders::getStatusReason, "超时未支付自动取消")
                .set(Orders::getUpdatedAt, LocalDateTime.now());
        return ordersMapper.update(null, uw);
    }
}
