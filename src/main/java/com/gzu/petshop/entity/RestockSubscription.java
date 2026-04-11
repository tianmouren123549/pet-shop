package com.gzu.petshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对应表 {@code restock_subscription}。
 */
@Data
@TableName("restock_subscription")
public class RestockSubscription {
    @TableId(value = "sub_id", type = IdType.AUTO)
    private Long subId;

    @TableField("user_id")
    private Long userId;

    @TableField("product_id")
    private Long productId;

    private Integer active;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
