package com.gzu.petshop.mapper.chat;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gzu.petshop.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    /**
     * 管理端「用户/商家咨询平台」会话列表（避免 Wrapper 在部分环境下生成 SQL 异常）。
     */
    @Select(
            "SELECT * FROM chat_session WHERE session_type IN ('USER_TO_ADMIN', 'MERCHANT_TO_ADMIN') "
                    + "ORDER BY updated_at DESC")
    List<ChatSession> selectPlatformSupportSessions();
}
