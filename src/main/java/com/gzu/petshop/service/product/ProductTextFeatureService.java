package com.gzu.petshop.service.product;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gzu.petshop.entity.ProductTextFeature;
import com.gzu.petshop.mapper.product.ProductTextFeatureMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductTextFeatureService {

    private static final int LIST_CAP = 500;

    private final ProductTextFeatureMapper productTextFeatureMapper;

    public ProductTextFeatureService(ProductTextFeatureMapper productTextFeatureMapper) {
        this.productTextFeatureMapper = productTextFeatureMapper;
    }

    public List<ProductTextFeature> listByProductId(Long productId) {
        if (productId == null || productId <= 0) {
            return List.of();
        }
        return productTextFeatureMapper.selectList(
                new LambdaQueryWrapper<ProductTextFeature>()
                        .eq(ProductTextFeature::getProductId, productId)
                        .orderByDesc(ProductTextFeature::getUpdatedAt)
        );
    }

    /** 最近更新的若干行，便于管理端总览（数据量可控）。 */
    public List<ProductTextFeature> listRecent() {
        return productTextFeatureMapper.selectList(
                new LambdaQueryWrapper<ProductTextFeature>()
                        .orderByDesc(ProductTextFeature::getUpdatedAt)
                        .last("LIMIT " + LIST_CAP)
        );
    }
}
