package com.gzu.petshop.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gzu.petshop.entity.Cart;

@Mapper
public interface CartMapper extends BaseMapper<Cart> {
}

