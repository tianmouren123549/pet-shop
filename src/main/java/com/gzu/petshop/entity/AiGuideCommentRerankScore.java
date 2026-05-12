package com.gzu.petshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ai_guide_comment_rerank_score")
public class AiGuideCommentRerankScore {
    @TableId(value = "score_id", type = IdType.AUTO)
    private Long scoreId;
    @TableField("run_id")
    private Long runId;
    @TableField("product_id")
    private Long productId;
    @TableField("input_position")
    private Integer inputPosition;
    @TableField("rank_no")
    private Integer rankNo;
    @TableField("fusion_score")
    private BigDecimal fusionScore;
    @TableField("bert_score")
    private BigDecimal bertScore;
    @TableField("xgb_norm")
    private BigDecimal xgbNorm;
    @TableField("avg_review_rating")
    private BigDecimal avgReviewRating;
    @TableField("review_count")
    private Integer reviewCount;
    @TableField("fusion_top_badge")
    private Boolean fusionTopBadge;
    @TableField("model_version")
    private String modelVersion;
    @TableField("created_at")
    private LocalDateTime createdAt;
}
