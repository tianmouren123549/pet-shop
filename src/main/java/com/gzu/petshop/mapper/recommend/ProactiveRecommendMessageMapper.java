package com.gzu.petshop.mapper.recommend;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gzu.petshop.dto.recommend.SessionFirstUserMessageDTO;
import com.gzu.petshop.entity.ProactiveRecommendMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProactiveRecommendMessageMapper extends BaseMapper<ProactiveRecommendMessage> {

    @Select(
            "<script>"
                    + "SELECT m.session_id AS sessionId, m.content AS content FROM proactive_recommend_message m "
                    + "INNER JOIN ( SELECT session_id, MIN(message_id) AS min_id FROM proactive_recommend_message "
                    + "WHERE role = 'USER' AND session_id IN "
                    + "<foreach collection='sessionIds' item='sid' open='(' separator=',' close=')'>#{sid}</foreach> "
                    + "GROUP BY session_id ) t ON t.session_id = m.session_id AND t.min_id = m.message_id "
                    + "WHERE m.role = 'USER'"
                    + "</script>")
    List<SessionFirstUserMessageDTO> selectFirstUserMessageBySessionIds(
            @Param("sessionIds") List<Long> sessionIds);
}
