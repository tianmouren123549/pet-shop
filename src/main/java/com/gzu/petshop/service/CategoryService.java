package com.gzu.petshop.service;

import com.gzu.petshop.entity.Category;
import com.gzu.petshop.mapper.CategoryMapper;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

@Service
public class CategoryService {
    private final CategoryMapper categoryMapper;

    public CategoryService(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    /**
     * 商品列表按类目筛选：与前端 mock 一致——选子类目时仅自身；选一级（parent_id=0）类目时包含 path 以 {@code categoryId/} 为前缀的子类目。
     *
     * @param categoryId 类目 ID
     * @return 参与 IN 查询的类目 ID 列表；无匹配子类目时返回空列表（表示无商品）
     */
    public List<Long> resolveCategoryIdsForProductFilter(Long categoryId) {
        if (categoryId == null || categoryId <= 0) {
            return List.of();
        }
        Category hit = categoryMapper.selectById(categoryId);
        if (hit == null) {
            return List.of();
        }
        if (hit.getParentId() != null && hit.getParentId() != 0) {
            return List.of(categoryId);
        }
        String prefix = categoryId + "/";
        List<Category> all = categoryMapper.selectList(null);
        List<Long> ids = new ArrayList<>();
        for (Category c : all) {
            String p = c.getPath();
            if (p != null && p.startsWith(prefix)) {
                ids.add(c.getCategoryId());
            }
        }
        return ids;
    }
    
    public List<Category> getAllCategories() {
        return categoryMapper.selectList(null);
    }
    
    public List<Category> getRootCategories() {
        return categoryMapper.selectList(new QueryWrapper<Category>().eq("parent_id", 0L));
    }
}
