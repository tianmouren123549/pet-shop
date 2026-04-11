package com.gzu.petshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对应表 {@code chat_session}；业务上仅使用 {@code USER_TO_MERCHANT}；{@code USER_TO_ADMIN} 为历史数据兼容。
 */
@Data
@TableName("chat_session")
public class ChatSession {
    @TableId(value = "session_id", type = IdType.AUTO)
    private Long sessionId;

    @TableField("session_no")
    private String sessionNo;

    @TableField("user_id")
    private Long userId;

    @TableField("order_id")
    private Long orderId;

    @TableField("agent_admin_id")
    private Long agentAdminId;

    @TableField("merchant_id")
    private Long merchantId;

    @TableField("session_type")
    private String sessionType;

    /**
     * 1=进行中（对应前端 OPEN），0=已关闭
     */
    private Integer status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
