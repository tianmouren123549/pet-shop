package com.gzu.petshop.mapper.order;

import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gzu.petshop.entity.Orders;

@Mapper
public interface OrdersMapper extends BaseMapper<Orders> {
}

