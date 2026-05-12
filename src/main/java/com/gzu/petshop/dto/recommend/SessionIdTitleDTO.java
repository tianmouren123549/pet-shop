package com.gzu.petshop.dto.recommend;

import lombok.Data;

/** 仅用于按会话 id 批量读取 proactive_recommend_session.title（列不存在时由调用方捕获降级） */
@Data
public class SessionIdTitleDTO {
    private Long sessionId;
    private String title;
}
