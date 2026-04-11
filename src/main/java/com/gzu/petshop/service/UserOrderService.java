package com.gzu.petshop.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gzu.petshop.dto.user.CreateOrderResponse;
import com.gzu.petshop.dto.user.OrderDetailQueryResult;
import com.gzu.petshop.dto.user.UserCreateOrderDirectRequest;
import com.gzu.petshop.dto.user.UserOrderDetailDTO;
import com.gzu.petshop.dto.user.UserOrderLineDTO;
import com.gzu.petshop.dto.user.UserOrderSummaryDTO;
import com.gzu.petshop.entity.Cart;
import com.gzu.petshop.entity.OrderItem;
import com.gzu.petshop.entity.Orders;
import com.gzu.petshop.entity.Product;
import com.gzu.petshop.entity.ProductDetail;
import com.gzu.petshop.entity.User;
import com.gzu.petshop.mapper.CartMapper;
import com.gzu.petshop.mapper.OrderItemMapper;
import com.gzu.petshop.mapper.OrdersMapper;
import com.gzu.petshop.mapper.ProductDetailMapper;
import com.gzu.petshop.mapper.ProductMapper;
import com.gzu.petshop.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
 * 用户端订单：列表、详情、购物车/立即购买下单、支付、取消、确认收货；规则对齐前端 mock。
 */
@Service
public class UserOrderService {
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final ProductDetailMapper productDetailMapper;
    private final CartMapper cartMapper;
    private final UserMapper userMapper;
    private final OrderStockService orderStockService;

    public UserOrderService(OrdersMapper ordersMapper,
                            OrderItemMapper orderItemMapper,
                            ProductMapper productMapper,
                            ProductDetailMapper productDetailMapper,
                            CartMapper cartMapper,
                            UserMapper userMapper,
                            OrderStockService orderStockService) {
        this.ordersMapper = ordersMapper;
        this.orderItemMapper = orderItemMapper;
        this.productMapper = productMapper;
        this.productDetailMapper = productDetailMapper;
        this.cartMapper = cartMapper;
        this.userMapper = userMapper;
        this.orderStockService = orderStockService;
    }

    /**
     * 当前用户的订单摘要列表，按创建时间倒序。
     */
    public List<UserOrderSummaryDTO> listOrders(Long userId) {
        if (userId == null || userId <= 0) {
            return List.of();
        }
        List<Orders> list = ordersMapper.selectList(
                new QueryWrapper<Orders>().eq("user_id", userId).orderByDesc("created_at"));
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> ids = list.stream().map(Orders::getOrderId).collect(Collectors.toList());
        Map<Long, Integer> counts = sumItemQuantitiesByOrder(ids);
        List<UserOrderSummaryDTO> out = new ArrayList<>();
        for (Orders o : list) {
            out.add(toSummary(o, counts.getOrDefault(o.getOrderId(), 0)));
        }
        return out;
    }

    /**
     * 订单详情（含明细）；区分不存在与无权限。
     */
    public OrderDetailQueryResult loadOrderDetail(Long orderId, Long userId) {
        if (userId == null || userId <= 0 || orderId == null) {
            return OrderDetailQueryResult.notFound();
        }
        Orders o = ordersMapper.selectById(orderId);
        if (o == null) {
            return OrderDetailQueryResult.notFound();
        }
        if (!userId.equals(o.getUserId())) {
            return OrderDetailQueryResult.forbidden();
        }
        return OrderDetailQueryResult.ok(buildDetailDto(o));
    }

    private UserOrderDetailDTO buildDetailDto(Orders o) {
        Long orderId = o.getOrderId();
        List<OrderItem> items = orderItemMapper.selectList(
                new QueryWrapper<OrderItem>().eq("order_id", orderId));
        List<UserOrderLineDTO> lines = new ArrayList<>();
        for (OrderItem it : items) {
            UserOrderLineDTO row = new UserOrderLineDTO();
            row.setProductId(it.getProductId());
            row.setMerchantId(it.getMerchantId());
            row.setQuantity(it.getQuantity());
            row.setPrice(it.getItemPrice());
            int q = it.getQuantity() == null ? 0 : it.getQuantity();
            BigDecimal sub = it.getItemPrice().multiply(BigDecimal.valueOf(q)).setScale(2, RoundingMode.HALF_UP);
            row.setSubtotal(sub);
            Product p = productMapper.selectById(it.getProductId());
            row.setTitle(p != null ? p.getTitle() : "");
            ProductDetail pd = productDetailMapper.selectById(it.getProductId());
            row.setImageUrl(pd != null && pd.getImageUrl() != null && !pd.getImageUrl().isBlank() ? pd.getImageUrl() : "");
            lines.add(row);
        }
        int itemCount = items.stream().mapToInt(it -> it.getQuantity() == null ? 0 : it.getQuantity()).sum();
        UserOrderDetailDTO dto = new UserOrderDetailDTO();
        dto.setOrderId(o.getOrderId());
        dto.setOrderNo(o.getOrderNo());
        dto.setUserId(o.getUserId());
        dto.setPayAmount(moneyStr(o.getPayAmount()));
        dto.setStatus(o.getStatus());
        dto.setCreatedAt(o.getCreatedAt() != null ? ISO.format(o.getCreatedAt()) : null);
        dto.setUpdatedAt(formatOrderUpdatedAt(o));
        dto.setItemCount(itemCount);
        dto.setItems(lines);
        return dto;
    }

    /**
     * 订单详情「更新时间」：优先 {@code orders.updated_at}，兼容未迁移库时用 {@code paid_at}/{@code created_at}。
     */
    private String formatOrderUpdatedAt(Orders o) {
        if (o.getUpdatedAt() != null) {
            return ISO.format(o.getUpdatedAt());
        }
        if (o.getPaidAt() != null) {
            return ISO.format(o.getPaidAt());
        }
        if (o.getCreatedAt() != null) {
            return ISO.format(o.getCreatedAt());
        }
        return null;
    }

    /**
     * 从购物车下单：校验上架/库存/单商家，写入订单与明细并清空购物车。
     */
    @Transactional
    public CreateOrderResponse createFromCart(Long userId) {
        if (userId == null || userId <= 0) {
            return CreateOrderResponse.fail("请先登录");
        }
        if (userMapper.selectById(userId) == null) {
            return CreateOrderResponse.fail("用户不存在");
        }
        List<Cart> cartRows = cartMapper.selectList(new QueryWrapper<Cart>().eq("user_id", userId));
        if (cartRows.isEmpty()) {
            return CreateOrderResponse.fail("购物车为空");
        }
        List<PreparedLine> prepared = new ArrayList<>();
        for (Cart c : cartRows) {
            Product p = productMapper.selectById(c.getProductId());
            if (p == null) {
                return CreateOrderResponse.fail("商品不存在");
            }
            if (p.getStatus() == null || p.getStatus() != 1) {
                return CreateOrderResponse.fail("商品「" + p.getTitle() + "」已下架");
            }
            int qty = c.getQuantity() == null ? 0 : c.getQuantity();
            if (qty < 1) {
                return CreateOrderResponse.fail("商品数量无效");
            }
            if (p.getStock() == null || p.getStock() < qty) {
                return CreateOrderResponse.fail("商品「" + p.getTitle() + "」库存不足");
            }
            PreparedLine pl = new PreparedLine();
            pl.product = p;
            pl.quantity = qty;
            prepared.add(pl);
        }
        Set<Long> mids = prepared.stream()
                .map(pl -> pl.product.getMerchantId())
                .filter(mid -> mid != null && mid > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (mids.size() > 1) {
            return CreateOrderResponse.fail("暂不支持跨店合并结算，请按商家分开下单");
        }
        BigDecimal pay = computePayAmount(prepared);
        Orders order = insertOrder(userId, pay, "CREATED");
        insertOrderItems(order.getOrderId(), prepared);
        cartMapper.delete(new QueryWrapper<Cart>().eq("user_id", userId));
        return CreateOrderResponse.ok(order.getOrderId());
    }

    /**
     * 立即购买：单 SKU 下单。
     */
    @Transactional
    public CreateOrderResponse createDirect(UserCreateOrderDirectRequest req) {
        if (req == null || req.getUserId() == null || req.getUserId() <= 0) {
            return CreateOrderResponse.fail("请先登录");
        }
        if (userMapper.selectById(req.getUserId()) == null) {
            return CreateOrderResponse.fail("用户不存在");
        }
        Long productId = req.getProductId();
        if (productId == null || productId <= 0) {
            return CreateOrderResponse.fail("商品不存在");
        }
        int qty = req.getQuantity() == null ? 1 : Math.max(1, req.getQuantity());
        Product p = productMapper.selectById(productId);
        if (p == null) {
            return CreateOrderResponse.fail("商品不存在");
        }
        if (p.getStatus() == null || p.getStatus() != 1) {
            return CreateOrderResponse.fail("商品已下架");
        }
        if (p.getStock() == null || p.getStock() < qty) {
            return CreateOrderResponse.fail("库存不足");
        }
        PreparedLine pl = new PreparedLine();
        pl.product = p;
        pl.quantity = qty;
        List<PreparedLine> prepared = List.of(pl);
        BigDecimal pay = computePayAmount(prepared);
        Orders order = insertOrder(req.getUserId(), pay, "CREATED");
        insertOrderItems(order.getOrderId(), prepared);
        return CreateOrderResponse.ok(order.getOrderId());
    }

    /**
     * 待支付 → 已支付，并扣减库存。
     */
    @Transactional
    public String payOrder(Long orderId, Long userId) {
        if (userId == null || userId <= 0) {
            return "请先登录";
        }
        Orders order = ordersMapper.selectById(orderId);
        if (order == null) {
            return "订单不存在";
        }
        if (!userId.equals(order.getUserId())) {
            return "无权限操作该订单";
        }
        if (!"CREATED".equals(normalizeStatus(order.getStatus()))) {
            return "当前状态无法支付";
        }
        String stockErr = orderStockService.applyStockDelta(order.getOrderId(), -1);
        if (stockErr != null) {
            return stockErr;
        }
        order.setStatus("PAID");
        LocalDateTime now = LocalDateTime.now();
        order.setPaidAt(now);
        order.setUpdatedAt(now);
        ordersMapper.updateById(order);
        return null;
    }

    /**
     * 取消订单：待支付直接取消；已支付则回滚库存后取消。
     */
    @Transactional
    public String cancelOrder(Long orderId, Long userId) {
        if (userId == null || userId <= 0) {
            return "请先登录";
        }
        Orders order = ordersMapper.selectById(orderId);
        if (order == null) {
            return "订单不存在";
        }
        if (!userId.equals(order.getUserId())) {
            return "无权限操作该订单";
        }
        String st = normalizeStatus(order.getStatus());
        if (!"CREATED".equals(st) && !"PAID".equals(st)) {
            return "当前状态无法取消";
        }
        if ("PAID".equals(st)) {
            String stockErr = orderStockService.applyStockDelta(order.getOrderId(), 1);
            if (stockErr != null) {
                return stockErr;
            }
        }
        order.setStatus("CANCELLED");
        order.setUpdatedAt(LocalDateTime.now());
        ordersMapper.updateById(order);
        return null;
    }

    /**
     * 已发货 → 已完成（确认收货）。
     */
    @Transactional
    public String confirmOrder(Long orderId, Long userId) {
        if (userId == null || userId <= 0) {
            return "请先登录";
        }
        Orders order = ordersMapper.selectById(orderId);
        if (order == null) {
            return "订单不存在";
        }
        if (!userId.equals(order.getUserId())) {
            return "无权限操作该订单";
        }
        if (!"SHIPPED".equals(normalizeStatus(order.getStatus()))) {
            return "当前状态无法确认收货";
        }
        order.setStatus("COMPLETED");
        order.setUpdatedAt(LocalDateTime.now());
        ordersMapper.updateById(order);
        return null;
    }

    private Orders insertOrder(Long userId, BigDecimal payAmount, String status) {
        Orders o = new Orders();
        o.setOrderNo(genOrderNo());
        o.setUserId(userId);
        o.setTotalAmount(payAmount);
        o.setPayAmount(payAmount);
        o.setStatus(status);
        o.setStatusReason(null);
        LocalDateTime now = LocalDateTime.now();
        o.setCreatedAt(now);
        o.setPaidAt(null);
        o.setUpdatedAt(now);
        ordersMapper.insert(o);
        return o;
    }

    private void insertOrderItems(Long orderId, List<PreparedLine> prepared) {
        for (PreparedLine pl : prepared) {
            Product p = pl.product;
            OrderItem it = new OrderItem();
            it.setOrderId(orderId);
            it.setProductId(p.getProductId());
            it.setMerchantId(p.getMerchantId());
            it.setQuantity(pl.quantity);
            it.setItemPrice(p.getPrice());
            orderItemMapper.insert(it);
        }
    }

    private static BigDecimal computePayAmount(List<PreparedLine> prepared) {
        BigDecimal sum = BigDecimal.ZERO;
        for (PreparedLine pl : prepared) {
            BigDecimal line = pl.product.getPrice().multiply(BigDecimal.valueOf(pl.quantity));
            sum = sum.add(line);
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 与 {@code orders.status} 比较前统一处理，避免库中空格、大小写导致「状态不允许」误判。
     */
    private static String normalizeStatus(String s) {
        if (s == null) {
            return "";
        }
        return s.trim().toUpperCase();
    }

    private static String genOrderNo() {
        return "ORD" + System.currentTimeMillis() + (int) (Math.random() * 900 + 100);
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

    private UserOrderSummaryDTO toSummary(Orders o, int itemCount) {
        UserOrderSummaryDTO dto = new UserOrderSummaryDTO();
        dto.setOrderId(o.getOrderId());
        dto.setOrderNo(o.getOrderNo());
        dto.setUserId(o.getUserId());
        dto.setPayAmount(moneyStr(o.getPayAmount()));
        dto.setStatus(o.getStatus());
        if (o.getCreatedAt() != null) {
            dto.setCreatedAt(ISO.format(o.getCreatedAt()));
        }
        dto.setItemCount(itemCount);
        return dto;
    }

    private static String moneyStr(BigDecimal v) {
        if (v == null) {
            return "0.00";
        }
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static final class PreparedLine {
        private Product product;
        private int quantity;
    }
}
