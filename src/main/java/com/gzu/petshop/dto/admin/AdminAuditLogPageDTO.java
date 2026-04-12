package com.gzu.petshop.dto.admin;

import java.util.List;

/**
 * 审计日志分页结果。
 */
public class AdminAuditLogPageDTO {
    private long total;
    private int page;
    private int pageSize;
    private List<AdminAuditLogRowDTO> records;

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public List<AdminAuditLogRowDTO> getRecords() {
        return records;
    }

    public void setRecords(List<AdminAuditLogRowDTO> records) {
        this.records = records;
    }
}
