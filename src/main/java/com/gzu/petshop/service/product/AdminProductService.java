package com.gzu.petshop.service.product;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gzu.petshop.dto.admin.AdminProductListItemDTO;
import com.gzu.petshop.dto.admin.AdminProductUpdateRequest;
import com.gzu.petshop.entity.Category;
import com.gzu.petshop.entity.Merchant;
import com.gzu.petshop.entity.Product;
import com.gzu.petshop.entity.ProductDetail;
import com.gzu.petshop.mapper.merchant.MerchantMapper;
import com.gzu.petshop.mapper.product.CategoryMapper;
import com.gzu.petshop.mapper.product.ProductDetailMapper;
import com.gzu.petshop.mapper.product.ProductMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminProductService {
    private final ProductMapper productMapper;
    private final MerchantMapper merchantMapper;
    private final ProductDetailMapper productDetailMapper;
    private final CategoryMapper categoryMapper;

    public AdminProductService(ProductMapper productMapper,
                               MerchantMapper merchantMapper,
                               ProductDetailMapper productDetailMapper,
                               CategoryMapper categoryMapper) {
        this.productMapper = productMapper;
        this.merchantMapper = merchantMapper;
        this.productDetailMapper = productDetailMapper;
        this.categoryMapper = categoryMapper;
    }

    /**
     * 管理端商品列表：附带商家店铺名、联系人（对齐前端 Mock 与 AdminProductsView）。
     */
    public List<AdminProductListItemDTO> listProductsForAdmin() {
        List<Product> products = productMapper.selectList(new QueryWrapper<Product>().orderByDesc("created_at"));
        Set<Long> mids = products.stream()
                .map(Product::getMerchantId)
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .collect(Collectors.toSet());
        Map<Long, Merchant> merchantMap = new HashMap<>();
        if (!mids.isEmpty()) {
            List<Merchant> merchants = merchantMapper.selectBatchIds(mids);
            for (Merchant m : merchants) {
                merchantMap.put(m.getMerchantId(), m);
            }
        }

        Set<Long> productIds = products.stream()
                .map(Product::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, ProductDetail> detailByProductId = new HashMap<>();
        if (!productIds.isEmpty()) {
            List<ProductDetail> details = productDetailMapper.selectBatchIds(productIds);
            if (details != null) {
                for (ProductDetail pd : details) {
                    detailByProductId.put(pd.getProductId(), pd);
                }
            }
        }

        Set<Long> catIds = products.stream()
                .map(Product::getCategoryId)
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .collect(Collectors.toSet());
        Map<Long, String> categoryNameById = new HashMap<>();
        if (!catIds.isEmpty()) {
            List<Category> cats = categoryMapper.selectBatchIds(catIds);
            if (cats != null) {
                for (Category c : cats) {
                    categoryNameById.put(c.getCategoryId(), c.getName() != null ? c.getName() : "");
                }
            }
        }

        return products.stream()
                .map(p -> toAdminListItem(p,
                        merchantMap.get(p.getMerchantId()),
                        detailByProductId.get(p.getProductId()),
                        categoryNameById.getOrDefault(p.getCategoryId(), "")))
                .collect(Collectors.toList());
    }

    private static AdminProductListItemDTO toAdminListItem(Product p,
                                                           Merchant m,
                                                           ProductDetail detail,
                                                           String categoryName) {
        AdminProductListItemDTO d = new AdminProductListItemDTO();
        d.setProductId(p.getProductId());
        d.setMerchantId(p.getMerchantId());
        d.setTitle(p.getTitle());
        d.setCategoryId(p.getCategoryId());
        d.setCategoryName(categoryName != null ? categoryName : "");
        if (detail != null && detail.getImageUrl() != null) {
            d.setImageUrl(detail.getImageUrl());
        } else {
            d.setImageUrl("");
        }
        d.setBrandId(p.getBrandId());
        d.setPrice(p.getPrice());
        d.setStock(p.getStock());
        d.setStatus(p.getStatus());
        d.setCreatedAt(p.getCreatedAt());
        d.setUpdatedAt(p.getUpdatedAt());
        if (m != null) {
            d.setMerchantShopName(m.getShopName());
            d.setMerchantContactName(m.getContactName());
        } else {
            d.setMerchantShopName("");
            d.setMerchantContactName("");
        }
        return d;
    }

    @Transactional
    public boolean updateProduct(Long productId, AdminProductUpdateRequest req) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            return false;
        }
        if (req.getPrice() != null) {
            product.setPrice(req.getPrice());
        }
        if (req.getStock() != null) {
            product.setStock(req.getStock());
        }
        if (req.getStatus() != null) {
            product.setStatus(req.getStatus());
        }
        product.setUpdatedAt(LocalDateTime.now());
        return productMapper.updateById(product) > 0;
    }
}

