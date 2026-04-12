package com.gzu.petshop.controller.common;

import com.gzu.petshop.common.Result;
import com.gzu.petshop.dto.common.CategoryTreeNode;
import com.gzu.petshop.entity.Category;
import com.gzu.petshop.service.product.CategoryService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/categories")
@CrossOrigin
public class CategoryController {
    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }
    
    @GetMapping
    public Result<List<Category>> getAllCategories() {
        return Result.success(categoryService.getAllCategories());
    }

    @GetMapping("/tree")
    public Result<List<CategoryTreeNode>> getCategoryTree() {
        return Result.success(categoryService.getCategoryTree());
    }
    
    @GetMapping("/root")
    public Result<List<Category>> getRootCategories() {
        return Result.success(categoryService.getRootCategories());
    }
}
