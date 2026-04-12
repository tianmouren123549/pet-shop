package com.gzu.petshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("merchant")
public class Merchant {
    @TableId(value = "merchant_id", type = IdType.AUTO)
    private Long merchantId;

    private String username;

    @TableField("password_hash")
    private String passwordHash;

    @TableField("shop_name")
    private String shopName;

    @TableField("contact_name")
    private String contactName;

    private String phone;
    private String email;

    @TableField("avatar_url")
    private String avatarUrl;

    /** 每周销售额目标（元），NULL 表示未设置 */
    @TableField("sales_target_weekly")
    private BigDecimal salesTargetWeekly;

    private Integer status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;
}

