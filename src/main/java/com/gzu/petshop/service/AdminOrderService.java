package com.gzu.petshop.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gzu.petshop.dto.admin.AdminOrderStatusUpdateRequest;
import com.gzu.petshop.dto.admin.AdminOrderSummaryDTO;
import com.gzu.petshop.entity.OrderItem;
import com.gzu.petshop.entity.Orders;
import com.gzu.petshop.mapper.OrderItemMapper;
import com.gzu.petshop.mapper.OrdersMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理端订单：列表摘要（含件数）、状态流转；与前端 mock 一致，在「待支付→已支付」扣库存、「已支付→已取消」回滚库存。
 */
@Service
public class AdminOrderService {
    private static final Set<String> ALLOWED_STATUS = Set.of("CREATED", "PAID", "SHIPPED", "COMPLETED", "CANCELLED");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStockService orderStockService;

    public AdminOrderService(OrdersMapper ordersMapper,
                             OrderItemMapper orderItemMapper,
                             OrderStockService orderStockService) {
        this.ordersMapper = ordersMapper;
        this.orderItemMapper = orderItemMapper;
        this.orderStockService = orderStockService;
    }

    public List<AdminOrderSummaryDTO> listOrders(String status) {
        QueryWrapper<Orders> qw = new QueryWrapper<>();
        if (status != null && !status.isBlank()) {
            qw.eq("status", status.trim().toUpperCase());
        }
        qw.orderByDesc("created_at");
        List<Orders> list = ordersMapper.selectList(qw);
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> ids = list.stream().map(Orders::getOrderId).toList();
        Map<Long, Integer> itemCountByOrder = sumItemQuantitiesByOrder(ids);
        List<AdminOrderSummaryDTO> out = new ArrayList<>();
        for (Orders o : list) {
            AdminOrderSummaryDTO row = new AdminOrderSummaryDTO();
            row.setOrderId(o.getOrderId());
            row.setOrderNo(o.getOrderNo());
            row.setUserId(o.getUserId());
            if (o.getPayAmount() != null) {
                row.setPayAmount(o.getPayAmount().setScale(2, RoundingMode.HALF_UP).toPlainString());
            } else {
                row.setPayAmount("0.00");
            }
            row.setStatus(o.getStatus());
            if (o.getCreatedAt() != null) {
                row.setCreatedAt(ISO.format(o.getCreatedAt()));
            }
            row.setItemCount(itemCountByOrder.getOrDefault(o.getOrderId(), 0));
            out.add(row);
        }
        return out;
    }

    private Map<Long, Integer> sumItemQuantitiesByOrder(List<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        List<OrderItem> all = orderItemMapper.selectList(new QueryWrapper<OrderItem>().in("order_id", orderIds));
        Map<Long, Integer> map = new HashMap<>();
        for (OrderItem it : all) {
            int q = it.getQuantity() == null ? 0 : it.getQuantity();
            map.merge(it.getOrderId(), q, Integer::sum);
        }
        return map;
    }

    @Transactional
    public String updateStatus(Long orderId, AdminOrderStatusUpdateRequest req) {
        Orders order = ordersMapper.selectById(orderId);
        if (order == null) {
            return "订单不存在";
        }
        if (req == null || req.getStatus() == null || req.getStatus().isBlank()) {
            return "目标状态不能为空";
        }
        String to = req.getStatus().trim().toUpperCase();
        if (!ALLOWED_STATUS.contains(to)) {
            return "不支持的订单状态";
        }
        String from = normalize(order.getStatus());
        if (!isAllowedTransition(from, to)) {
            return "订单状态流转不合法";
        }

        if ("CREATED".equals(from) && "PAID".equals(to)) {
            String stockErr = orderStockService.applyStockDelta(orderId, -1);
            if (stockErr != null) {
                return stockErr;
            }
            order.setPaidAt(LocalDateTime.now());
        } else if ("PAID".equals(from) && "CANCELLED".equals(to)) {
            String stockErr = orderStockService.applyStockDelta(orderId, 1);
            if (stockErr != null) {
                return stockErr;
            }
        }

        order.setStatus(to);
        order.setStatusReason(req.getStatusReason());
        order.setUpdatedAt(LocalDateTime.now());
        ordersMapper.updateById(order);
        return null;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }

    private boolean isAllowedTransition(String from, String to) {
        if (from.equals(to)) {
            return true;
        }
        return switch (from) {
            case "CREATED" -> "PAID".equals(to) || "CANCELLED".equals(to);
            case "PAID" -> "SHIPPED".equals(to) || "CANCELLED".equals(to);
            case "SHIPPED" -> "COMPLETED".equals(to);
            default -> false;
        };
    }
}
