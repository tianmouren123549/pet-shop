package com.gzu.petshop.mapper.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gzu.petshop.dto.user.RecentViewEventRow;
import com.gzu.petshop.entity.UserEventLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserEventLogMapper extends BaseMapper<UserEventLog> {

    @Select("SELECT e.product_id AS productId, p.category_id AS categoryId "
            + "FROM user_event_log e "
            + "LEFT JOIN product p ON p.product_id = e.product_id "
            + "WHERE e.user_id = #{userId} "
            + "AND e.event_type IN ('view','click','add_cart','buy') "
            + "AND e.event_time >= #{since} "
            + "ORDER BY e.event_time DESC "
            + "LIMIT #{limit}")
    List<RecentViewEventRow> selectRecentViewLikeEvents(
            @Param("userId") Long userId,
            @Param("since") LocalDateTime since,
            @Param("limit") int limit
    );

    @Select("SELECT COUNT(DISTINCT e.product_id) "
            + "FROM user_event_log e "
            + "WHERE e.user_id = #{userId} "
            + "AND e.product_id IS NOT NULL "
            + "AND e.event_type IN ('view','click','add_cart','buy') "
            + "AND e.event_time >= #{since}")
    Integer countDistinctViewLikeProducts(
            @Param("userId") Long userId,
            @Param("since") LocalDateTime since
    );

    @Select("SELECT e.product_id "
            + "FROM user_event_log e "
            + "WHERE e.user_id = #{userId} "
            + "AND e.product_id IS NOT NULL "
            + "AND e.event_type IN ('view','click','add_cart','buy') "
            + "AND e.event_time >= #{since} "
            + "ORDER BY e.event_time DESC "
            + "LIMIT #{limit}")
    List<Long> selectRecentViewLikeProductIds(
            @Param("userId") Long userId,
            @Param("since") LocalDateTime since,
            @Param("limit") int limit
    );
}
