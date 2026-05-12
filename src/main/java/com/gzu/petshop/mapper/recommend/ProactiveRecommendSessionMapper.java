package com.gzu.petshop.mapper.recommend;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gzu.petshop.dto.recommend.SessionIdTitleDTO;
import com.gzu.petshop.entity.ProactiveRecommendSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ProactiveRecommendSessionMapper extends BaseMapper<ProactiveRecommendSession> {

    @Update("UPDATE proactive_recommend_session SET title = #{title} WHERE session_id = #{sessionId}")
    int updateSessionTitle(@Param("sessionId") Long sessionId, @Param("title") String title);

    @Select(
            "<script>"
                    + "SELECT session_id AS sessionId, title AS title FROM proactive_recommend_session "
                    + "WHERE session_id IN "
                    + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
                    + "</script>")
    List<SessionIdTitleDTO> selectTitlesBySessionIds(@Param("ids") List<Long> ids);
}
