package com.gzu.petshop.service.product;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzu.petshop.dto.common.ProductDTO;
import com.gzu.petshop.entity.Product;
import com.gzu.petshop.entity.ProductDetail;
import com.gzu.petshop.mapper.product.BrandMapper;
import com.gzu.petshop.mapper.product.CategoryMapper;
import com.gzu.petshop.mapper.product.ProductDetailMapper;
import com.gzu.petshop.mapper.product.ProductMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProductService {
    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final BrandMapper brandMapper;
    private final ProductDetailMapper productDetailMapper;
    private final CategoryService categoryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProductService(ProductMapper productMapper,
                           CategoryMapper categoryMapper,
                           BrandMapper brandMapper,
                           ProductDetailMapper productDetailMapper,
                           CategoryService categoryService) {
        this.productMapper = productMapper;
        this.categoryMapper = categoryMapper;
        this.brandMapper = brandMapper;
        this.productDetailMapper = productDetailMapper;
        this.categoryService = categoryService;
    }
    
    public List<ProductDTO> getAllProducts() {
        return productMapper.selectList(new QueryWrapper<Product>().eq("status", 1)).stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }
    
    public List<ProductDTO> getProductsByCategory(Long categoryId) {
        List<Long> catIds = categoryService.resolveCategoryIdsForProductFilter(categoryId);
        if (catIds.isEmpty()) {
            return List.of();
        }
        return productMapper.selectList(
                    new QueryWrapper<Product>()
                            .in("category_id", catIds)
                            .eq("status", 1)
                ).stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }
    
    /**
     * 用户端详情：下架商品视为不存在（与前端 mock / 列表仅展示上架一致）。
     */
    public ProductDTO getProductById(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null || product.getStatus() == null || product.getStatus() != 1) {
            return null;
        }
        return convertToDTO(product);
    }
    
    private ProductDTO convertToDTO(Product product) {
        ProductDTO dto = new ProductDTO();
        dto.setProductId(product.getProductId());
        dto.setMerchantId(product.getMerchantId());
        dto.setTitle(product.getTitle());
        dto.setCategoryId(product.getCategoryId());
        dto.setBrandId(product.getBrandId());
        dto.setPrice(product.getPrice());
        dto.setStock(product.getStock());
        dto.setStatus(product.getStatus());

        var category = categoryMapper.selectById(product.getCategoryId());
        if (category != null) {
            dto.setCategoryName(category.getName());
        }

        if (product.getBrandId() != null) {
            var brand = brandMapper.selectById(product.getBrandId());
            if (brand != null) {
                dto.setBrandName(brand.getName());
            }
        }

        // 加载商品详情
        ProductDetail detail = productDetailMapper.selectById(product.getProductId());
        if (detail != null) {
            ProductDTO.ProductDetailDTO detailDTO = new ProductDTO.ProductDetailDTO();
            detailDTO.setDescription(detail.getDescription());
            detailDTO.setImageUrl(detail.getImageUrl() != null ? detail.getImageUrl() : "");

            // 解析 JSON 规格参数
            if (detail.getSpecJson() != null && !detail.getSpecJson().isEmpty()) {
                try {
                    Map<String, Object> specMap = objectMapper.readValue(
                            detail.getSpecJson(),
                            new TypeReference<Map<String, Object>>() {}
                    );
                    detailDTO.setSpecJson(specMap);
                } catch (Exception e) {
                    // JSON 解析失败时忽略
                }
            }

            dto.setDetail(detailDTO);
        }

        return dto;
    }
}
