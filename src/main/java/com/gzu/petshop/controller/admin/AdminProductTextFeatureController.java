package com.gzu.petshop.controller.admin;

import com.gzu.petshop.common.Result;
import com.gzu.petshop.entity.ProductTextFeature;
import com.gzu.petshop.service.product.ProductTextFeatureService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 读取 {@code product_text_feature}（由离线脚本 {@code sync_product_text_feature_mysql.py} 写入）。
 */
@RestController
@RequestMapping("/api/admin/product-text-features")
@CrossOrigin
public class AdminProductTextFeatureController {

    private final ProductTextFeatureService productTextFeatureService;

    public AdminProductTextFeatureController(ProductTextFeatureService productTextFeatureService) {
        this.productTextFeatureService = productTextFeatureService;
    }

    @GetMapping
    public Result<List<ProductTextFeature>> list(@RequestParam(required = false) Long productId) {
        if (productId != null && productId > 0) {
            return Result.success(productTextFeatureService.listByProductId(productId));
        }
        return Result.success(productTextFeatureService.listRecent());
    }
}
