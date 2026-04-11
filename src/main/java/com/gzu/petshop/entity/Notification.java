package com.gzu.petshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对应表 {@code notification}。
 */
@Data
@TableName("notification")
public class Notification {
    @TableId(value = "notice_id", type = IdType.AUTO)
    private Long noticeId;

    @TableField("receiver_type")
    private String receiverType;

    @TableField("receiver_id")
    private Long receiverId;

    private String title;
    private String content;

    @TableField("product_id")
    private Long productId;

    private String reason;

    @TableField("read_status")
    private Integer readStatus;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
