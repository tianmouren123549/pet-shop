package com.gzu.petshop.service.order;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gzu.petshop.dto.merchant.MerchantOrderStatusUpdateRequest;
import com.gzu.petshop.dto.merchant.MerchantOrderSummaryDTO;
import com.gzu.petshop.dto.merchant.MerchantOrderTodoBadgesDTO;
import com.gzu.petshop.entity.OrderItem;
import com.gzu.petshop.entity.Orders;
import com.gzu.petshop.mapper.order.OrderItemMapper;
import com.gzu.petshop.mapper.order.OrdersMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 商家订单：仅展示含本商家明细的订单；发货规则与前端 mock 一致。
 */
@Service
public class MerchantOrderService {
    private static final DateTimeFormatter ISO_LOCAL = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;

    public MerchantOrderService(OrdersMapper ordersMapper, OrderItemMapper orderItemMapper) {
        this.ordersMapper = ordersMapper;
        this.orderItemMapper = orderItemMapper;
    }

    /**
     * 列出当前商家相关订单（至少一条 {@code order_item.merchant_id} 命中），可按订单主状态筛选。
     *
     * @param merchantId 商家 ID
     * @param status     可选，与 {@code orders.status} 精确匹配
     * @return 摘要列表，按创建时间倒序
     */
    public List<MerchantOrderSummaryDTO> listOrders(Long merchantId, String status) {
        if (merchantId == null || merchantId <= 0) {
            return List.of();
        }
        List<OrderItem> mine = orderItemMapper.selectList(
                new QueryWrapper<OrderItem>().eq("merchant_id", merchantId));
        Set<Long> orderIds = mine.stream().map(OrderItem::getOrderId).collect(Collectors.toCollection(LinkedHashSet::new));
        if (orderIds.isEmpty()) {
            return List.of();
        }
        QueryWrapper<Orders> oq = new QueryWrapper<Orders>().in("order_id", orderIds).orderByDesc("created_at");
        if (status != null && !status.isBlank()) {
            oq.eq("status", status.trim().toUpperCase());
        }
        List<Orders> orders = ordersMapper.selectList(oq);
        Map<Long, Integer> itemCountByOrder = buildItemCountMap(new ArrayList<>(orderIds));
        List<MerchantOrderSummaryDTO> out = new ArrayList<>();
        for (Orders o : orders) {
            MerchantOrderSummaryDTO row = new MerchantOrderSummaryDTO();
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
                row.setCreatedAt(ISO_LOCAL.format(o.getCreatedAt()));
            }
            row.setItemCount(itemCountByOrder.getOrDefault(o.getOrderId(), 0));
            out.add(row);
        }
        return out;
    }

    /**
     * 是否存在待发货订单，供顶栏与筛选橙点使用（与 {@link #listOrders} 商家范围一致；待支付不提示）。
     */
    public MerchantOrderTodoBadgesDTO todoBadges(Long merchantId) {
        if (merchantId == null || merchantId <= 0) {
            return new MerchantOrderTodoBadgesDTO(false);
        }
        List<OrderItem> mine = orderItemMapper.selectList(
                new QueryWrapper<OrderItem>().eq("merchant_id", merchantId));
        Set<Long> orderIds = mine.stream().map(OrderItem::getOrderId).collect(Collectors.toCollection(LinkedHashSet::new));
        if (orderIds.isEmpty()) {
            return new MerchantOrderTodoBadgesDTO(false);
        }
        long pendingShip = ordersMapper.selectCount(
                new QueryWrapper<Orders>().in("order_id", orderIds).eq("status", "PAID"));
        return new MerchantOrderTodoBadgesDTO(pendingShip > 0);
    }

    private Map<Long, Integer> buildItemCountMap(List<Long> orderIds) {
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

    /**
     * 商家仅允许：已支付 → 已发货，且订单内全部明细须属于该商家（与 mock 一致）。
     *
     * @param orderId 订单 ID
     * @param req     含 {@code merchantId}、{@code status}
     * @return 错误文案；成功返回 {@code null}
     */
    @Transactional
    public String updateOrderStatus(Long orderId, MerchantOrderStatusUpdateRequest req) {
        Long merchantId = req != null ? req.getMerchantId() : null;
        if (merchantId == null || merchantId <= 0) {
            return "商家ID无效";
        }
        String nextRaw = req.getStatus();
        if (nextRaw == null || nextRaw.isBlank()) {
            return "目标状态不能为空";
        }
        String next = nextRaw.trim().toUpperCase();
        Orders order = ordersMapper.selectById(orderId);
        if (order == null) {
            return "订单不存在";
        }
        List<OrderItem> items = orderItemMapper.selectList(
                new QueryWrapper<OrderItem>().eq("order_id", orderId));
        if (items.isEmpty()) {
            return "订单明细不存在";
        }
        boolean own = items.stream().anyMatch(it -> merchantId.equals(it.getMerchantId()));
        if (!own) {
            return "无权限操作该订单";
        }
        if ("SHIPPED".equals(next)) {
            boolean allMine = items.stream().allMatch(it -> merchantId.equals(it.getMerchantId()));
            if (!allMine) {
                return "该订单包含其他商家商品，请由平台拆单后处理";
            }
            if (!"PAID".equals(order.getStatus())) {
                return "当前状态无法发货";
            }
            order.setStatus("SHIPPED");
            order.setUpdatedAt(LocalDateTime.now());
            ordersMapper.updateById(order);
            return null;
        }
        if ("CANCELLED".equals(next)) {
            return "商家端不支持取消订单";
        }
        if ("COMPLETED".equals(next)) {
            return "完成状态由用户确认收货触发";
        }
        return "不支持的状态操作";
    }
}
