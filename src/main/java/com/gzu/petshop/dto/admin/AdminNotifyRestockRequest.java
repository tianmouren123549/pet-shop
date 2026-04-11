package com.gzu.petshop.dto.admin;

/**
 * 管理端向商家发送补货提醒（与前端 {@code adminNotifyRestock(pid, { reason, note })} 一致）。
 */
public class AdminNotifyRestockRequest {
    private String reason;
    private String note;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
