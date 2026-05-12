package com.gzu.petshop.service.user;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gzu.petshop.dto.user.FrequentProductItemDTO;
import com.gzu.petshop.entity.OrderItem;
import com.gzu.petshop.entity.Orders;
import com.gzu.petshop.entity.Product;
import com.gzu.petshop.entity.ProductDetail;
import com.gzu.petshop.entity.UserProductFrequency;
import com.gzu.petshop.mapper.order.OrderItemMapper;
import com.gzu.petshop.mapper.order.OrdersMapper;
import com.gzu.petshop.mapper.product.ProductDetailMapper;
import com.gzu.petshop.mapper.product.ProductMapper;
import com.gzu.petshop.mapper.user.UserProductFrequencyMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 常购统计：确认收货后累加；列表按购买件数排序。
 */
@Service
public class UserProductFrequencyService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final UserProductFrequencyMapper frequencyMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrdersMapper ordersMapper;
    private final ProductMapper productMapper;
    private final ProductDetailMapper productDetailMapper;

    public UserProductFrequencyService(
            UserProductFrequencyMapper frequencyMapper,
            OrderItemMapper orderItemMapper,
            OrdersMapper ordersMapper,
            ProductMapper productMapper,
            ProductDetailMapper productDetailMapper) {
        this.frequencyMapper = frequencyMapper;
        this.orderItemMapper = orderItemMapper;
        this.ordersMapper = ordersMapper;
        this.productMapper = productMapper;
        this.productDetailMapper = productDetailMapper;
    }

    /**
     * 订单确认收货后，按明细累加常购件数。
     */
    @Transactional
    public void incrementAfterOrderCompleted(Long orderId) {
        if (orderId == null || orderId <= 0) {
            return;
        }
        Orders o = ordersMapper.selectById(orderId);
        if (o == null) {
            return;
        }
        Long userId = o.getUserId();
        List<OrderItem> lines = orderItemMapper.selectList(new QueryWrapper<OrderItem>().eq("order_id", orderId));
        if (lines == null || lines.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (OrderItem it : lines) {
            Long pid = it.getProductId();
            int qty = it.getQuantity() == null ? 0 : it.getQuantity();
            if (pid == null || pid <= 0 || qty <= 0) {
                continue;
            }
            UserProductFrequency row = frequencyMapper.selectOne(
                    new QueryWrapper<UserProductFrequency>()
                            .eq("user_id", userId)
                            .eq("product_id", pid));
            if (row == null) {
                UserProductFrequency n = new UserProductFrequency();
                n.setUserId(userId);
                n.setProductId(pid);
                n.setBuyCount(qty);
                n.setLastBoughtAt(now);
                frequencyMapper.insert(n);
            } else {
                row.setBuyCount((row.getBuyCount() == null ? 0 : row.getBuyCount()) + qty);
                row.setLastBoughtAt(now);
                frequencyMapper.updateById(row);
            }
        }
    }

    public List<FrequentProductItemDTO> topByUser(Long userId, int limit) {
        if (userId == null || userId <= 0) {
            return List.of();
        }
        int lim = Math.min(50, Math.max(1, limit));
        List<UserProductFrequency> rows = frequencyMapper.selectList(
                new QueryWrapper<UserProductFrequency>()
                        .eq("user_id", userId)
                        .orderByDesc("buy_count")
                        .orderByDesc("last_bought_at")
                        .last("LIMIT " + lim));
        List<FrequentProductItemDTO> out = new ArrayList<>();
        for (UserProductFrequency r : rows) {
            Product p = productMapper.selectById(r.getProductId());
            if (p == null || p.getStatus() == null || p.getStatus() != 1) {
                continue;
            }
            ProductDetail d = productDetailMapper.selectById(r.getProductId());
            FrequentProductItemDTO dto = new FrequentProductItemDTO();
            dto.setProductId(p.getProductId());
            dto.setTitle(p.getTitle());
            dto.setImageUrl(d != null && d.getImageUrl() != null ? d.getImageUrl() : "");
            dto.setBuyCount(r.getBuyCount());
            dto.setPrice(p.getPrice());
            if (r.getLastBoughtAt() != null) {
                dto.setLastBoughtAt(ISO.format(r.getLastBoughtAt()));
            }
            out.add(dto);
        }
        out.sort(Comparator.comparing(FrequentProductItemDTO::getBuyCount).reversed());
        return out;
    }
}
