package com.gzu.petshop.dto.common;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 顶栏聊天红点：是否存在未读（Jackson 显式字段名，避免 record/布尔序列化差异导致前端读不到）。
 */
public class ChatUnreadBadgeDTO {

    @JsonProperty("hasUnread")
    private boolean hasUnread;

    public ChatUnreadBadgeDTO() {
    }

    public ChatUnreadBadgeDTO(boolean hasUnread) {
        this.hasUnread = hasUnread;
    }

    public boolean isHasUnread() {
        return hasUnread;
    }

    public void setHasUnread(boolean hasUnread) {
        this.hasUnread = hasUnread;
    }
}
