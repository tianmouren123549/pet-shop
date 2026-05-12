package com.gzu.petshop.dto.recommend;

import lombok.Data;

/** 各会话最早一条 USER 消息（用于补全会话标题） */
@Data
public class SessionFirstUserMessageDTO {
    private Long sessionId;
    private String content;
}
