package com.gzu.petshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对应表 {@code chat_message}。
 */
@Data
@TableName("chat_message")
public class ChatMessage {
    @TableId(value = "message_id", type = IdType.AUTO)
    private Long messageId;

    @TableField("session_id")
    private Long sessionId;

    @TableField("sender_type")
    private String senderType;

    @TableField("sender_id")
    private Long senderId;

    private String content;

    @TableField("attachment_json")
    private String attachmentJson;

    @TableField("is_read_by_user")
    private Integer isReadByUser;

    @TableField("is_read_by_merchant")
    private Integer isReadByMerchant;

    @TableField("is_read_by_admin")
    private Integer isReadByAdmin;

    @TableField("read_at")
    private LocalDateTime readAt;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
