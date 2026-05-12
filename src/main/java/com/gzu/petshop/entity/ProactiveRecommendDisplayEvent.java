package com.gzu.petshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("proactive_recommend_display_event")
public class ProactiveRecommendDisplayEvent {
    @TableId(value = "event_id", type = IdType.AUTO)
    private Long eventId;
    @TableField("session_id")
    private Long sessionId;
    @TableField("user_id")
    private Long userId;
    @TableField("event_type")
    private String eventType;
    @TableField("message_id")
    private Long messageId;
    @TableField("product_ids_json")
    private String productIdsJson;
    @TableField("input_product_ids_json")
    private String inputProductIdsJson;
    @TableField("scores_json")
    private String scoresJson;
    @TableField("created_at")
    private LocalDateTime createdAt;
}
