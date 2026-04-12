package com.gzu.petshop.service.product;

import com.gzu.petshop.dto.common.CategoryTreeNode;
import com.gzu.petshop.entity.Category;
import com.gzu.petshop.mapper.product.CategoryMapper;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CategoryService {
    private final CategoryMapper categoryMapper;

    public CategoryService(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    /**
     * 商品列表按类目筛选：选子类目时仅自身；选一级（parent_id=0）类目时包含
     * 该类目自身（直接挂在根下的商品）以及 path 以 {@code categoryId/} 为前缀的所有子类目（如狗粮下的成犬粮、幼犬粮）。
     *
     * @param categoryId 类目 ID
     * @return 参与 IN 查询的类目 ID 列表
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
        ids.add(categoryId);
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

    /**
     * 全类目树（parent_id=0 为根），子节点按 category_id 升序。
     */
    public List<CategoryTreeNode> getCategoryTree() {
        List<Category> all = categoryMapper.selectList(null);
        all.sort(Comparator.comparing(Category::getCategoryId));
        Map<Long, CategoryTreeNode> byId = new HashMap<>();
        for (Category c : all) {
            CategoryTreeNode n = new CategoryTreeNode();
            n.setCategoryId(c.getCategoryId());
            n.setParentId(c.getParentId());
            n.setName(c.getName());
            n.setPath(c.getPath());
            n.setChildren(new ArrayList<>());
            byId.put(c.getCategoryId(), n);
        }
        List<CategoryTreeNode> roots = new ArrayList<>();
        for (Category c : all) {
            CategoryTreeNode n = byId.get(c.getCategoryId());
            Long pid = c.getParentId();
            if (pid == null || pid == 0L) {
                roots.add(n);
            } else {
                CategoryTreeNode p = byId.get(pid);
                if (p != null) {
                    p.getChildren().add(n);
                } else {
                    roots.add(n);
                }
            }
        }
        sortTree(roots);
        return roots;
    }

    private void sortTree(List<CategoryTreeNode> nodes) {
        nodes.sort(Comparator.comparing(CategoryTreeNode::getCategoryId));
        for (CategoryTreeNode n : nodes) {
            sortTree(n.getChildren());
        }
    }
}
