package com.gzu.petshop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gzu.petshop.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
