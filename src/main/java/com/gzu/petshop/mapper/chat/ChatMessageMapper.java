package com.gzu.petshop.mapper.chat;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gzu.petshop.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /**
     * 用户侧未读：商家发送且用户尚未打开会话消除红点。
     */
    @Select("SELECT COUNT(1) FROM chat_message m INNER JOIN chat_session s ON m.session_id = s.session_id "
            + "WHERE s.user_id = #{userId} AND s.session_type = 'USER_TO_MERCHANT' "
            + "AND m.sender_type = 'MERCHANT' AND m.is_read_by_user = 0")
    long countUnreadMerchantMessagesForUser(@Param("userId") Long userId);

    /**
     * 商家侧未读：用户发送且商家尚未打开该会话消息列表。
     */
    @Select("SELECT COUNT(1) FROM chat_message m INNER JOIN chat_session s ON m.session_id = s.session_id "
            + "WHERE s.merchant_id = #{merchantId} AND s.session_type = 'USER_TO_MERCHANT' "
            + "AND m.sender_type = 'USER' AND IFNULL(m.is_read_by_merchant, 0) = 0")
    long countUnreadUserMessagesForMerchant(@Param("merchantId") Long merchantId);

    /**
     * 用户联系商家列表：存在未读商家消息的店铺 ID（去重）。
     */
    @Select("SELECT DISTINCT s.merchant_id FROM chat_message m INNER JOIN chat_session s ON m.session_id = s.session_id "
            + "WHERE s.user_id = #{userId} AND s.session_type = 'USER_TO_MERCHANT' "
            + "AND m.sender_type = 'MERCHANT' AND m.is_read_by_user = 0 "
            + "AND s.merchant_id IS NOT NULL")
    List<Long> selectMerchantIdsWithUnreadForUser(@Param("userId") Long userId);

    /**
     * 商家端：存在未读用户消息的会话 ID（去重）。
     */
    @Select("SELECT DISTINCT m.session_id FROM chat_message m INNER JOIN chat_session s ON m.session_id = s.session_id "
            + "WHERE s.merchant_id = #{merchantId} AND s.session_type = 'USER_TO_MERCHANT' "
            + "AND m.sender_type = 'USER' AND IFNULL(m.is_read_by_merchant, 0) = 0")
    List<Long> selectSessionIdsWithUnreadUserForMerchant(@Param("merchantId") Long merchantId);
}
