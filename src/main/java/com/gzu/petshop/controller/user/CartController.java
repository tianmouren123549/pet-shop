package com.gzu.petshop.controller.user;

import com.gzu.petshop.common.Result;
import com.gzu.petshop.dto.user.CartDTO;
import com.gzu.petshop.service.CartService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cart")
@CrossOrigin
public class CartController {
    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }
    
    @GetMapping("/{userId}")
    public Result<List<CartDTO>> getCartItems(@PathVariable Long userId) {
        return Result.success(cartService.getCartItems(userId));
    }
    
    @PostMapping("/add")
    public Result<Void> addToCart(@RequestBody Map<String, Object> params) {
        if (params == null || params.get("userId") == null || params.get("productId") == null || params.get("quantity") == null) {
            return Result.error("参数无效");
        }
        try {
            Long userId = Long.valueOf(params.get("userId").toString());
            Long productId = Long.valueOf(params.get("productId").toString());
            Integer quantity = Integer.valueOf(params.get("quantity").toString());
            String err = cartService.addToCart(userId, productId, quantity);
            return err == null ? Result.success() : Result.error(err);
        } catch (NumberFormatException e) {
            return Result.error("参数格式错误");
        }
    }

    @PutMapping("/update")
    public Result<Void> updateQuantity(@RequestBody Map<String, Object> params) {
        if (params == null || params.get("cartId") == null || params.get("quantity") == null) {
            return Result.error("参数无效");
        }
        try {
            Long cartId = Long.valueOf(params.get("cartId").toString());
            Integer quantity = Integer.valueOf(params.get("quantity").toString());
            String err = cartService.updateQuantity(cartId, quantity);
            return err == null ? Result.success() : Result.error(err);
        } catch (NumberFormatException e) {
            return Result.error("参数格式错误");
        }
    }
    
    @DeleteMapping("/{cartId}")
    public Result<Void> removeFromCart(@PathVariable Long cartId) {
        cartService.removeFromCart(cartId);
        return Result.success();
    }
}
