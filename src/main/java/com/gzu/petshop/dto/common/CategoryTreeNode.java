package com.gzu.petshop.dto.common;

import java.util.ArrayList;
import java.util.List;

/**
 * 类目树节点，供前端导航、下拉分组等使用。
 */
public class CategoryTreeNode {
    private Long categoryId;
    private Long parentId;
    private String name;
    private String path;
    private List<CategoryTreeNode> children = new ArrayList<>();

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public List<CategoryTreeNode> getChildren() {
        return children;
    }

    public void setChildren(List<CategoryTreeNode> children) {
        this.children = children != null ? children : new ArrayList<>();
    }
}
