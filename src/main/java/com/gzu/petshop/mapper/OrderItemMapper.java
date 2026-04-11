package com.gzu.petshop.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gzu.petshop.entity.OrderItem;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {
}

