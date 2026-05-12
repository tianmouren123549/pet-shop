package com.gzu.petshop.service.audit;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzu.petshop.dto.admin.AdminAuditLogPageDTO;
import com.gzu.petshop.dto.admin.AdminAuditLogRowDTO;
import com.gzu.petshop.entity.AdminAuditLog;
import com.gzu.petshop.mapper.admin.AdminAuditLogMapper;
import com.gzu.petshop.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端操作审计：写入 {@code admin_audit_log}，供合规与追溯。
 */
@Service
public class AdminAuditService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditService.class);
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public static final String ACTION_PRODUCT_UPDATE = "PRODUCT_UPDATE";
    public static final String ACTION_PRODUCT_NOTIFY_RESTOCK = "PRODUCT_NOTIFY_RESTOCK";
    public static final String ACTION_ORDER_STATUS_CHANGE = "ORDER_STATUS_CHANGE";
    public static final String ACTION_ORDER_URGE_SHIPMENT = "ORDER_URGE_SHIPMENT";
    public static final String ACTION_USER_STATUS_CHANGE = "USER_STATUS_CHANGE";
    public static final String ACTION_USER_PASSWORD_RESET = "USER_PASSWORD_RESET";
    public static final String ACTION_USER_PET_PREFERENCE_RESET = "USER_PET_PREFERENCE_RESET";
    public static final String ACTION_USER_PET_PREFERENCE_RESET_ALL = "USER_PET_PREFERENCE_RESET_ALL";
    public static final String ACTION_MERCHANT_STATUS_CHANGE = "MERCHANT_STATUS_CHANGE";
    public static final String ACTION_MERCHANT_PASSWORD_RESET = "MERCHANT_PASSWORD_RESET";

    public static final String TARGET_PRODUCT = "product";
    public static final String TARGET_ORDER = "order";
    public static final String TARGET_USER = "user";
    public static final String TARGET_MERCHANT = "merchant";

    private final AdminAuditLogMapper adminAuditLogMapper;
    private final ObjectMapper objectMapper;

    public AdminAuditService(AdminAuditLogMapper adminAuditLogMapper, ObjectMapper objectMapper) {
        this.adminAuditLogMapper = adminAuditLogMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 从当前请求解析管理员主键（由 {@link JwtAuthFilter} 写入）。
     */
    public Long currentAdminId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object v = request.getAttribute(JwtAuthFilter.ATTR_PRINCIPAL_ID);
        if (v instanceof Long l) {
            return l > 0 ? l : null;
        }
        if (v != null) {
            try {
                long id = Long.parseLong(v.toString());
                return id > 0 ? id : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 记录一条审计日志。{@code adminId} 无效时跳过（不打断主业务）。
     */
    public void log(Long adminId, String action, String targetType, long targetId, Map<String, Object> detail) {
        if (adminId == null || adminId <= 0) {
            return;
        }
        String act = truncate(action, 64);
        String tt = truncate(targetType, 16);
        AdminAuditLog row = new AdminAuditLog();
        row.setAdminId(adminId);
        row.setAction(act);
        row.setTargetType(tt);
        row.setTargetId(targetId);
        row.setCreatedAt(LocalDateTime.now());
        if (detail == null || detail.isEmpty()) {
            row.setDetailJson(null);
        } else {
            try {
                row.setDetailJson(objectMapper.writeValueAsString(detail));
            } catch (JsonProcessingException e) {
                log.warn("audit detail serialize failed: {}", e.getMessage());
                row.setDetailJson("{\"error\":\"detail_serialize_failed\"}");
            }
        }
        try {
            adminAuditLogMapper.insert(row);
        } catch (Exception e) {
            log.error("admin audit insert failed: {}", e.getMessage());
        }
    }

    /**
     * 分页查询审计日志（时间倒序）。
     */
    public AdminAuditLogPageDTO page(int page, int pageSize) {
        int p = Math.max(1, page);
        int ps = Math.min(100, Math.max(1, pageSize));
        long total = adminAuditLogMapper.selectCount(new QueryWrapper<>());
        int offset = (p - 1) * ps;
        QueryWrapper<AdminAuditLog> qw = new QueryWrapper<>();
        qw.orderByDesc("created_at");
        qw.last("LIMIT " + offset + "," + ps);
        List<AdminAuditLog> list = adminAuditLogMapper.selectList(qw);
        AdminAuditLogPageDTO out = new AdminAuditLogPageDTO();
        out.setTotal(total);
        out.setPage(p);
        out.setPageSize(ps);
        List<AdminAuditLogRowDTO> rows = list.stream().map(this::toRow).collect(Collectors.toList());
        out.setRecords(rows);
        return out;
    }

    private AdminAuditLogRowDTO toRow(AdminAuditLog e) {
        AdminAuditLogRowDTO d = new AdminAuditLogRowDTO();
        d.setLogId(e.getLogId());
        d.setAdminId(e.getAdminId());
        d.setAction(e.getAction());
        d.setTargetType(e.getTargetType());
        d.setTargetId(e.getTargetId());
        d.setDetailJson(e.getDetailJson());
        if (e.getCreatedAt() != null) {
            d.setCreatedAt(ISO.format(e.getCreatedAt()));
        } else {
            d.setCreatedAt("");
        }
        return d;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }
}
