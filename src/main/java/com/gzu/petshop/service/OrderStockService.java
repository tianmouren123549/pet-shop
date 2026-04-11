package com.gzu.petshop.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gzu.petshop.entity.OrderItem;
import com.gzu.petshop.entity.Product;
import com.gzu.petshop.mapper.OrderItemMapper;
import com.gzu.petshop.mapper.ProductMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单与库存联动：支付扣减、取消回滚；供用户端支付/取消与管理端改状态复用。
 */
@Service
public class OrderStockService {
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;

    public OrderStockService(OrderItemMapper orderItemMapper, ProductMapper productMapper) {
        this.orderItemMapper = orderItemMapper;
        this.productMapper = productMapper;
    }

    /**
     * 按订单明细批量调整库存。
     *
     * @param orderId   订单 ID
     * @param direction -1 扣减（支付），+1 回滚（已支付订单取消）
     * @return 错误文案，成功返回 {@code null}
     */
    public String applyStockDelta(Long orderId, int direction) {
        if (orderId == null || direction == 0) {
            return "参数无效";
        }
        List<OrderItem> items = orderItemMapper.selectList(
                new QueryWrapper<OrderItem>().eq("order_id", orderId));
        for (OrderItem it : items) {
            Product p = productMapper.selectById(it.getProductId());
            if (p == null) {
                return "商品不存在";
            }
            int qty = it.getQuantity() == null ? 0 : it.getQuantity();
            if (qty < 1) {
                continue;
            }
            int cur = p.getStock() == null ? 0 : p.getStock();
            if (direction < 0) {
                if (p.getStatus() == null || p.getStatus() != 1) {
                    return "商品「" + p.getTitle() + "」已下架";
                }
                if (cur < qty) {
                    return "商品「" + p.getTitle() + "」库存不足";
                }
                p.setStock(cur - qty);
            } else {
                p.setStock(cur + qty);
            }
            p.setUpdatedAt(LocalDateTime.now());
            productMapper.updateById(p);
        }
        return null;
    }
}
