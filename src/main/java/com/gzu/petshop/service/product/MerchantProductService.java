package com.gzu.petshop.service.product;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzu.petshop.dto.merchant.MerchantProductCreateRequest;
import com.gzu.petshop.dto.merchant.MerchantProductDTO;
import com.gzu.petshop.dto.merchant.MerchantProductUpdateRequest;
import com.gzu.petshop.entity.Category;
import com.gzu.petshop.entity.Merchant;
import com.gzu.petshop.entity.Product;
import com.gzu.petshop.entity.ProductDetail;
import com.gzu.petshop.mapper.product.BrandMapper;
import com.gzu.petshop.mapper.product.CategoryMapper;
import com.gzu.petshop.mapper.merchant.MerchantMapper;
import com.gzu.petshop.mapper.product.ProductDetailMapper;
import com.gzu.petshop.mapper.product.ProductMapper;
import com.gzu.petshop.service.support.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MerchantProductService {
    private final ProductMapper productMapper;
    private final ProductDetailMapper productDetailMapper;
    private final CategoryMapper categoryMapper;
    private final BrandMapper brandMapper;
    private final MerchantMapper merchantMapper;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MerchantProductService(ProductMapper productMapper,
                                  ProductDetailMapper productDetailMapper,
                                  CategoryMapper categoryMapper,
                                  BrandMapper brandMapper,
                                  MerchantMapper merchantMapper,
                                  NotificationService notificationService) {
        this.productMapper = productMapper;
        this.productDetailMapper = productDetailMapper;
        this.categoryMapper = categoryMapper;
        this.brandMapper = brandMapper;
        this.merchantMapper = merchantMapper;
        this.notificationService = notificationService;
    }

    public List<MerchantProductDTO> getProducts(Long merchantId) {
        return productMapper.selectList(
                        new QueryWrapper<Product>()
                                .eq("merchant_id", merchantId)
                                .orderByDesc("created_at")
                ).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public MerchantProductDTO getProduct(Long merchantId, Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            return null;
        }
        if (!merchantId.equals(product.getMerchantId())) {
            return new MerchantProductDTO();
        }
        return toDTO(product);
    }

    @Transactional
    public String updateProduct(Long pathProductId, MerchantProductUpdateRequest req) {
        if (req == null || req.getMerchantId() == null) {
            return "商家ID无效";
        }
        Product product = productMapper.selectById(pathProductId);
        if (product == null) {
            return "商品不存在";
        }
        if (!req.getMerchantId().equals(product.getMerchantId())) {
            return "无权限操作该商品";
        }
        int prevStock = product.getStock() == null ? 0 : product.getStock();
        if (req.getStatus() != null) {
            if (req.getStatus() != 0 && req.getStatus() != 1) return "商品状态无效";
            product.setStatus(req.getStatus());
        }
        if (req.getStock() != null) {
            if (req.getStock() < 0) return "库存必须为非负整数";
            product.setStock(req.getStock());
        }
        if (req.getPrice() != null) {
            if (req.getPrice().compareTo(BigDecimal.ZERO) < 0) return "价格必须为非负数";
            product.setPrice(req.getPrice());
        }
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.updateById(product);
        int nextStock = product.getStock() == null ? 0 : product.getStock();
        if (prevStock <= 0 && nextStock > 0) {
            notificationService.notifySubscribersProductRestocked(product.getProductId(), nextStock);
        }
        return null;
    }

    @Transactional
    public Long createProduct(MerchantProductCreateRequest req) {
        if (req == null || req.getMerchantId() == null || req.getMerchantId() <= 0) return null;
        Merchant merchant = merchantMapper.selectById(req.getMerchantId());
        if (merchant == null) return null;
        if (req.getTitle() == null || req.getTitle().trim().isEmpty()) return null;
        if (req.getCategoryId() == null || req.getCategoryId() <= 0) return null;
        if (req.getPrice() == null || req.getPrice().compareTo(BigDecimal.ZERO) < 0) return null;
        if (req.getStock() == null || req.getStock() < 0) return null;

        Category category = categoryMapper.selectById(req.getCategoryId());
        if (category == null) return null;

        Product product = new Product();
        product.setMerchantId(req.getMerchantId());
        product.setTitle(req.getTitle().trim());
        product.setCategoryId(req.getCategoryId());
        product.setBrandId(null);
        product.setPrice(req.getPrice());
        product.setStock(req.getStock());
        product.setStatus(1);
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.insert(product);

        ProductDetail detail = new ProductDetail();
        detail.setProductId(product.getProductId());
        detail.setDescription(req.getDescription() == null ? "" : req.getDescription());
        detail.setSpecJson("{}");
        detail.setUpdatedAt(LocalDateTime.now());
        productDetailMapper.insert(detail);
        return product.getProductId();
    }

    @Transactional
    public String updateProductContent(Long productId, String description, String specJson, String imageUrl) {
        Product product = productMapper.selectById(productId);
        if (product == null) return "商品不存在";

        ProductDetail detail = productDetailMapper.selectById(productId);
        if (detail == null) {
            detail = new ProductDetail();
            detail.setProductId(productId);
            detail.setDescription(description == null ? "" : description);
            detail.setSpecJson(normalizeSpecJson(specJson));
            applyImageUrl(detail, imageUrl);
            detail.setUpdatedAt(LocalDateTime.now());
            productDetailMapper.insert(detail);
            return null;
        }
        detail.setDescription(description == null ? "" : description);
        detail.setSpecJson(normalizeSpecJson(specJson));
        applyImageUrl(detail, imageUrl);
        detail.setUpdatedAt(LocalDateTime.now());
        productDetailMapper.updateById(detail);
        return null;
    }

    /** 仅当 {@code imageUrl} 非 null 时更新（multipart 不传则保留原值） */
    private void applyImageUrl(ProductDetail detail, String imageUrl) {
        if (imageUrl == null) {
            return;
        }
        String t = imageUrl.trim();
        detail.setImageUrl(t.isEmpty() ? null : t);
    }

    private String normalizeSpecJson(String specJson) {
        if (specJson == null || specJson.isBlank()) return "{}";
        try {
            objectMapper.readTree(specJson);
            return specJson;
        } catch (Exception e) {
            return "{}";
        }
    }

    private MerchantProductDTO toDTO(Product product) {
        MerchantProductDTO dto = new MerchantProductDTO();
        dto.setProductId(product.getProductId());
        dto.setTitle(product.getTitle());
        dto.setCategoryId(product.getCategoryId());
        dto.setBrandId(product.getBrandId());
        dto.setPrice(product.getPrice());
        dto.setStock(product.getStock());
        dto.setStatus(product.getStatus());
        dto.setMerchantId(product.getMerchantId());

        Category category = categoryMapper.selectById(product.getCategoryId());
        if (category != null) dto.setCategoryName(category.getName());

        if (product.getBrandId() != null) {
            var brand = brandMapper.selectById(product.getBrandId());
            if (brand != null) dto.setBrandName(brand.getName());
        }

        Merchant merchant = merchantMapper.selectById(product.getMerchantId());
        if (merchant != null) {
            dto.setMerchantShopName(merchant.getShopName());
            dto.setMerchantContactName(merchant.getContactName());
        }

        ProductDetail detail = productDetailMapper.selectById(product.getProductId());
        if (detail != null) {
            MerchantProductDTO.ProductDetailDTO detailDTO = new MerchantProductDTO.ProductDetailDTO();
            detailDTO.setDescription(detail.getDescription());
            detailDTO.setImageUrl(detail.getImageUrl() != null ? detail.getImageUrl() : "");
            try {
                if (detail.getSpecJson() != null && !detail.getSpecJson().isBlank()) {
                    Map<String, Object> spec = objectMapper.readValue(
                            detail.getSpecJson(),
                            new TypeReference<Map<String, Object>>() {}
                    );
                    detailDTO.setSpecJson(spec);
                }
            } catch (Exception ignored) {
                // ignore invalid json
            }
            dto.setDetail(detailDTO);
        }

        return dto;
    }
}

