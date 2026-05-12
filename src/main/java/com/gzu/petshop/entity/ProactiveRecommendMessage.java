package com.gzu.petshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("proactive_recommend_message")
public class ProactiveRecommendMessage {
    @TableId(value = "message_id", type = IdType.AUTO)
    private Long messageId;
    @TableField("session_id")
    private Long sessionId;
    private String role;
    private String content;
    @TableField("payload_json")
    private String payloadJson;
    @TableField("created_at")
    private LocalDateTime createdAt;
}
