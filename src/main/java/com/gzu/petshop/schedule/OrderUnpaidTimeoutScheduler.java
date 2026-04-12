package com.gzu.petshop.schedule;

import com.gzu.petshop.service.order.OrderUnpaidTimeoutService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时扫描超时未支付订单并关闭（与用户列表/详情入口的即时扫描互为补充）。
 */
@Component
public class OrderUnpaidTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderUnpaidTimeoutScheduler.class);

    private final OrderUnpaidTimeoutService orderUnpaidTimeoutService;

    public OrderUnpaidTimeoutScheduler(OrderUnpaidTimeoutService orderUnpaidTimeoutService) {
        this.orderUnpaidTimeoutService = orderUnpaidTimeoutService;
    }

    @Scheduled(fixedRate = 60_000)
    public void scanAndCancelUnpaid() {
        try {
            int n = orderUnpaidTimeoutService.cancelExpiredUnpaidOrders();
            if (n > 0) {
                log.info("超时未支付自动取消订单 {} 笔", n);
            }
        } catch (Exception e) {
            log.warn("扫描超时未支付订单失败", e);
        }
    }
}
