package com.gzu.petshop.dto.admin;

/**
 * 管理端「用户/商家咨询平台」会话列表项。
 */
public class AdminSupportSessionDTO {
    private Long sessionId;
    private String sessionType;
    private String counterpartyTitle;
    private String counterpartySub;
    private String updatedAt;
    private boolean unreadFromCounterparty;

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionType() {
        return sessionType;
    }

    public void setSessionType(String sessionType) {
        this.sessionType = sessionType;
    }

    public String getCounterpartyTitle() {
        return counterpartyTitle;
    }

    public void setCounterpartyTitle(String counterpartyTitle) {
        this.counterpartyTitle = counterpartyTitle;
    }

    public String getCounterpartySub() {
        return counterpartySub;
    }

    public void setCounterpartySub(String counterpartySub) {
        this.counterpartySub = counterpartySub;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isUnreadFromCounterparty() {
        return unreadFromCounterparty;
    }

    public void setUnreadFromCounterparty(boolean unreadFromCounterparty) {
        this.unreadFromCounterparty = unreadFromCounterparty;
    }
}
