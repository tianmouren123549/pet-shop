package com.gzu.petshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("user_product_recommendation")
public class UserProductRecommendation {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("product_id")
    private Long productId;

    @TableField("model_version")
    private String modelVersion;

    private BigDecimal score;

    @TableField("rank_no")
    private Integer rankNo;

    @TableField("reason_json")
    private String reasonJson;

    @TableField("generated_at")
    private LocalDateTime generatedAt;
}
