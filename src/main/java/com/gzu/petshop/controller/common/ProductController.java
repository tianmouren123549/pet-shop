package com.gzu.petshop.controller.common;

import com.gzu.petshop.common.Result;
import com.gzu.petshop.dto.common.ProductDTO;
import com.gzu.petshop.service.ProductService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/products")
@CrossOrigin
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }
    
    @GetMapping
    public Result<List<ProductDTO>> getAllProducts() {
        return Result.success(productService.getAllProducts());
    }
    
    @GetMapping("/{id}")
    public Result<ProductDTO> getProduct(@PathVariable Long id) {
        ProductDTO dto = productService.getProductById(id);
        if (dto == null) {
            return Result.error("商品不存在");
        }
        return Result.success(dto);
    }
    
    @GetMapping("/category/{categoryId}")
    public Result<List<ProductDTO>> getProductsByCategory(@PathVariable Long categoryId) {
        return Result.success(productService.getProductsByCategory(categoryId));
    }
}
