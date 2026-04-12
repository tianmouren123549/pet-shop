package com.gzu.petshop.service.order;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gzu.petshop.dto.admin.AdminMerchantBriefDTO;
import com.gzu.petshop.dto.admin.AdminOrderDetailDTO;
import com.gzu.petshop.dto.admin.AdminOrderLineDTO;
import com.gzu.petshop.dto.admin.AdminOrderStatusUpdateRequest;
import com.gzu.petshop.dto.admin.AdminOrderSummaryDTO;
import com.gzu.petshop.entity.Merchant;
import com.gzu.petshop.entity.OrderItem;
import com.gzu.petshop.entity.Orders;
import com.gzu.petshop.entity.Product;
import com.gzu.petshop.entity.ProductDetail;
import com.gzu.petshop.entity.User;
import com.gzu.petshop.mapper.merchant.MerchantMapper;
import com.gzu.petshop.mapper.order.OrderItemMapper;
import com.gzu.petshop.mapper.order.OrdersMapper;
import com.gzu.petshop.mapper.product.ProductDetailMapper;
import com.gzu.petshop.mapper.product.ProductMapper;
import com.gzu.petshop.mapper.user.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final UserMapper userMapper;
    private final MerchantMapper merchantMapper;
    private final ProductMapper productMapper;
    private final ProductDetailMapper productDetailMapper;

    public AdminOrderService(OrdersMapper ordersMapper,
                             OrderItemMapper orderItemMapper,
                             OrderStockService orderStockService,
                             UserMapper userMapper,
                             MerchantMapper merchantMapper,
                             ProductMapper productMapper,
                             ProductDetailMapper productDetailMapper) {
        this.ordersMapper = ordersMapper;
        this.orderItemMapper = orderItemMapper;
        this.orderStockService = orderStockService;
        this.userMapper = userMapper;
        this.merchantMapper = merchantMapper;
        this.productMapper = productMapper;
        this.productDetailMapper = productDetailMapper;
    }

    /** 供审计等场景读取当前订单状态（不存在则返回 {@code null}）。 */
    public String getOrderCurrentStatus(Long orderId) {
        if (orderId == null) {
            return null;
        }
        Orders o = ordersMapper.selectById(orderId);
        return o == null ? null : o.getStatus();
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
        Set<Long> userIds = list.stream().map(Orders::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (User u : userMapper.selectBatchIds(userIds)) {
                userMap.put(u.getUserId(), u);
            }
        }
        List<AdminOrderSummaryDTO> out = new ArrayList<>();
        for (Orders o : list) {
            AdminOrderSummaryDTO row = new AdminOrderSummaryDTO();
            row.setOrderId(o.getOrderId());
            row.setOrderNo(o.getOrderNo());
            row.setUserId(o.getUserId());
            User u = userMap.get(o.getUserId());
            if (u != null && u.getNickname() != null && !u.getNickname().isBlank()) {
                row.setUserNickname(u.getNickname());
            } else {
                row.setUserNickname(o.getUserId() != null ? "用户" + o.getUserId() : "");
            }
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

    /**
     * 管理端订单详情：用户基本信息 + 明细（含各商品所属商家店铺名）。
     */
    public AdminOrderDetailDTO getOrderDetail(Long orderId) {
        if (orderId == null) {
            return null;
        }
        Orders o = ordersMapper.selectById(orderId);
        if (o == null) {
            return null;
        }
        User buyer = o.getUserId() != null ? userMapper.selectById(o.getUserId()) : null;
        Long orderPk = o.getOrderId();
        List<OrderItem> itemRows = orderItemMapper.selectList(
                new QueryWrapper<OrderItem>().eq("order_id", orderPk));
        Set<Long> mids = itemRows.stream()
                .map(OrderItem::getMerchantId)
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .collect(Collectors.toCollection(HashSet::new));
        Map<Long, Merchant> merchantMap = new HashMap<>();
        if (!mids.isEmpty()) {
            for (Merchant m : merchantMapper.selectBatchIds(mids)) {
                merchantMap.put(m.getMerchantId(), m);
            }
        }
        List<AdminOrderLineDTO> lines = new ArrayList<>();
        for (OrderItem it : itemRows) {
            AdminOrderLineDTO line = new AdminOrderLineDTO();
            line.setProductId(it.getProductId());
            Long mid = it.getMerchantId();
            line.setMerchantId(mid);
            Merchant mer = mid != null ? merchantMap.get(mid) : null;
            if (mer != null && mer.getShopName() != null && !mer.getShopName().isBlank()) {
                line.setMerchantShopName(mer.getShopName());
            } else {
                line.setMerchantShopName(mid != null ? "商家" + mid : "");
            }
            Product p = productMapper.selectById(it.getProductId());
            line.setTitle(p != null ? p.getTitle() : "");
            ProductDetail pd = productDetailMapper.selectById(it.getProductId());
            line.setImageUrl(pd != null && pd.getImageUrl() != null && !pd.getImageUrl().isBlank() ? pd.getImageUrl() : "");
            line.setPrice(it.getItemPrice());
            int q = it.getQuantity() == null ? 0 : it.getQuantity();
            line.setQuantity(q);
            BigDecimal sub = it.getItemPrice().multiply(BigDecimal.valueOf(q)).setScale(2, RoundingMode.HALF_UP);
            line.setSubtotal(sub);
            lines.add(line);
        }
        int itemCount = itemRows.stream().mapToInt(it -> it.getQuantity() == null ? 0 : it.getQuantity()).sum();
        List<AdminMerchantBriefDTO> merchantBriefs = new ArrayList<>();
        for (Long mid : mids.stream().sorted().toList()) {
            Merchant mer = merchantMap.get(mid);
            if (mer == null) {
                continue;
            }
            AdminMerchantBriefDTO b = new AdminMerchantBriefDTO();
            b.setMerchantId(mer.getMerchantId());
            b.setShopName(mer.getShopName() != null ? mer.getShopName() : "");
            b.setContactName(mer.getContactName() != null ? mer.getContactName() : "");
            b.setPhone(mer.getPhone() != null ? mer.getPhone() : "");
            merchantBriefs.add(b);
        }
        AdminOrderDetailDTO dto = new AdminOrderDetailDTO();
        dto.setOrderId(o.getOrderId());
        dto.setOrderNo(o.getOrderNo());
        dto.setUserId(o.getUserId());
        if (buyer != null) {
            dto.setUserNickname(
                    buyer.getNickname() != null && !buyer.getNickname().isBlank()
                            ? buyer.getNickname()
                            : "用户" + o.getUserId());
            dto.setUserEmail(buyer.getEmail() != null ? buyer.getEmail() : "");
            dto.setUserPhone(buyer.getPhone() != null ? buyer.getPhone() : "");
        } else {
            dto.setUserNickname(o.getUserId() != null ? "用户" + o.getUserId() : "");
            dto.setUserEmail("");
            dto.setUserPhone("");
        }
        dto.setPayAmount(moneyStr(o.getPayAmount()));
        dto.setStatus(o.getStatus());
        dto.setStatusReason(o.getStatusReason() != null ? o.getStatusReason() : "");
        if (o.getCreatedAt() != null) {
            dto.setCreatedAt(ISO.format(o.getCreatedAt()));
        }
        if (o.getUpdatedAt() != null) {
            dto.setUpdatedAt(ISO.format(o.getUpdatedAt()));
        } else if (o.getPaidAt() != null) {
            dto.setUpdatedAt(ISO.format(o.getPaidAt()));
        } else if (o.getCreatedAt() != null) {
            dto.setUpdatedAt(ISO.format(o.getCreatedAt()));
        }
        dto.setItemCount(itemCount);
        dto.setMerchants(merchantBriefs);
        dto.setItems(lines);
        return dto;
    }

    private static String moneyStr(BigDecimal v) {
        if (v == null) {
            return "0.00";
        }
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
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
