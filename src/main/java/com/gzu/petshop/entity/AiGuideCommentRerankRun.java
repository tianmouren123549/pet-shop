package com.gzu.petshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ai_guide_comment_rerank_run")
public class AiGuideCommentRerankRun {
    @TableId(value = "run_id", type = IdType.AUTO)
    private Long runId;
    @TableField("user_id")
    private Long userId;
    @TableField("session_id")
    private Long sessionId;
    @TableField("product_count")
    private Integer productCount;
    @TableField("weight_xgb")
    private BigDecimal weightXgb;
    @TableField("weight_bert")
    private BigDecimal weightBert;
    @TableField("bert_model_version")
    private String bertModelVersion;
    @TableField("input_product_ids_json")
    private String inputProductIdsJson;
    @TableField("created_at")
    private LocalDateTime createdAt;
}
