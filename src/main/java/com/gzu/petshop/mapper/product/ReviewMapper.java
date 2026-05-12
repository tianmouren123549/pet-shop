package com.gzu.petshop.mapper.product;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gzu.petshop.dto.recommend.ProductReviewStatsDTO;
import com.gzu.petshop.entity.Review;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ReviewMapper extends BaseMapper<Review> {

    /**
     * 按商品聚合有效评论：均分与条数，供推荐排序「评论强推」加权。
     */
    @Select("<script>"
            + "SELECT product_id AS productId, AVG(rating) AS avgRating, COUNT(*) AS reviewCount "
            + "FROM review WHERE IFNULL(status, 1) = 1 AND rating IS NOT NULL "
            + "AND product_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "GROUP BY product_id"
            + "</script>")
    List<ProductReviewStatsDTO> selectStatsByProductIds(@Param("ids") List<Long> ids);
}

