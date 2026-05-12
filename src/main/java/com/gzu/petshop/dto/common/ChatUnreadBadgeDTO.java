package com.gzu.petshop.dto.common;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 顶栏聊天红点：区分「对端（用户↔商家）」与「平台管理员」两条通道。
 * {@link #hasUnread} 为二者之或，便于旧前端或概览仍用单一布尔。
 */
public class ChatUnreadBadgeDTO {

    @JsonProperty("hasUnread")
    private boolean hasUnread;

    /** 用户：商家未读回复；商家：用户未读咨询（均为 USER_TO_MERCHANT 会话）。 */
    @JsonProperty("hasUnreadPeer")
    private boolean hasUnreadPeer;

    /** 用户：平台管理员未读回复（USER_TO_ADMIN）；商家：平台未读回复（MERCHANT_TO_ADMIN）。 */
    @JsonProperty("hasUnreadPlatform")
    private boolean hasUnreadPlatform;

    public ChatUnreadBadgeDTO() {
    }

    public ChatUnreadBadgeDTO(boolean hasUnreadPeer, boolean hasUnreadPlatform) {
        this.hasUnreadPeer = hasUnreadPeer;
        this.hasUnreadPlatform = hasUnreadPlatform;
        this.hasUnread = hasUnreadPeer || hasUnreadPlatform;
    }

    public boolean isHasUnread() {
        return hasUnread;
    }

    public void setHasUnread(boolean hasUnread) {
        this.hasUnread = hasUnread;
    }

    public boolean isHasUnreadPeer() {
        return hasUnreadPeer;
    }

    public void setHasUnreadPeer(boolean hasUnreadPeer) {
        this.hasUnreadPeer = hasUnreadPeer;
    }

    public boolean isHasUnreadPlatform() {
        return hasUnreadPlatform;
    }

    public void setHasUnreadPlatform(boolean hasUnreadPlatform) {
        this.hasUnreadPlatform = hasUnreadPlatform;
    }
}
