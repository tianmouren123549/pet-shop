package com.gzu.petshop.service;

import com.gzu.petshop.dto.user.CartDTO;
import com.gzu.petshop.entity.Cart;
import com.gzu.petshop.entity.Product;
import com.gzu.petshop.entity.ProductDetail;
import com.gzu.petshop.mapper.CartMapper;
import com.gzu.petshop.mapper.ProductDetailMapper;
import com.gzu.petshop.mapper.ProductMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CartService {
    private final CartMapper cartMapper;
    private final ProductMapper productMapper;
    private final ProductDetailMapper productDetailMapper;

    public CartService(CartMapper cartMapper, ProductMapper productMapper, ProductDetailMapper productDetailMapper) {
        this.cartMapper = cartMapper;
        this.productMapper = productMapper;
        this.productDetailMapper = productDetailMapper;
    }
    
    public List<CartDTO> getCartItems(Long userId) {
        return cartMapper.selectList(new QueryWrapper<Cart>().eq("user_id", userId)).stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }
    
    /**
     * @return 错误文案，成功返回 {@code null}
     */
    @Transactional
    public String addToCart(Long userId, Long productId, Integer quantity) {
        if (userId == null || userId <= 0 || productId == null || productId <= 0) {
            return "参数无效";
        }
        int q = quantity == null ? 0 : quantity;
        if (q < 1) {
            return "数量无效";
        }
        Product p = productMapper.selectById(productId);
        if (p == null) {
            return "商品不存在";
        }
        if (p.getStatus() == null || p.getStatus() != 1) {
            return "商品已下架";
        }
        Cart existingCart = cartMapper.selectOne(
                new QueryWrapper<Cart>()
                        .eq("user_id", userId)
                        .eq("product_id", productId)
        );
        int existingQty = existingCart != null && existingCart.getQuantity() != null ? existingCart.getQuantity() : 0;
        int stock = p.getStock() == null ? 0 : p.getStock();
        if (existingQty + q > stock) {
            return "库存不足";
        }

        if (existingCart != null) {
            Cart cart = existingCart;
            cart.setQuantity(cart.getQuantity() + q);
            cart.setUpdatedAt(LocalDateTime.now());
            cartMapper.updateById(cart);
        } else {
            Cart cart = new Cart();
            cart.setUserId(userId);
            cart.setProductId(productId);
            cart.setQuantity(q);
            cart.setUpdatedAt(LocalDateTime.now());
            cartMapper.insert(cart);
        }
        return null;
    }
    
    /**
     * @return 错误文案，成功返回 {@code null}
     */
    @Transactional
    public String updateQuantity(Long cartId, Integer quantity) {
        if (quantity == null) {
            return "参数无效";
        }
        if (quantity <= 0) {
            cartMapper.deleteById(cartId);
            return null;
        }

        Cart cart = cartMapper.selectById(cartId);
        if (cart == null) {
            return "购物车项不存在";
        }
        Product p = productMapper.selectById(cart.getProductId());
        if (p == null) {
            return "商品不存在";
        }
        if (p.getStatus() == null || p.getStatus() != 1) {
            return "商品已下架";
        }
        int stock = p.getStock() == null ? 0 : p.getStock();
        if (quantity > stock) {
            return "库存不足";
        }
        cart.setQuantity(quantity);
        cart.setUpdatedAt(LocalDateTime.now());
        cartMapper.updateById(cart);
        return null;
    }
    
    @Transactional
    public void removeFromCart(Long cartId) {
        cartMapper.deleteById(cartId);
    }
    
    private CartDTO convertToDTO(Cart cart) {
        CartDTO dto = new CartDTO();
        dto.setCartId(cart.getCartId());
        dto.setProductId(cart.getProductId());
        dto.setQuantity(cart.getQuantity());
        
        Product product = productMapper.selectById(cart.getProductId());
        if (product != null) {
            dto.setTitle(product.getTitle());
            dto.setPrice(product.getPrice());
            dto.setSubtotal(product.getPrice().multiply(java.math.BigDecimal.valueOf(cart.getQuantity())));
            ProductDetail pd = productDetailMapper.selectById(product.getProductId());
            if (pd != null && pd.getImageUrl() != null && !pd.getImageUrl().isBlank()) {
                dto.setImageUrl(pd.getImageUrl());
            } else {
                dto.setImageUrl("");
            }
        }
        
        return dto;
    }
}
