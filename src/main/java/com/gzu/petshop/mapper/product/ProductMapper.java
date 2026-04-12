package com.gzu.petshop.mapper.product;

import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gzu.petshop.entity.Product;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}

