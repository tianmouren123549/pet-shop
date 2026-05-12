package com.gzu.petshop.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 与 {@link JwtAuthFilter#ATTR_PRINCIPAL_ID} 配合，校验「当前登录用户 id」与路径上的 userId 一致。
 */
public final class UserPrincipalUtil {

    private UserPrincipalUtil() {
    }

    public static Long currentUserId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object p = request.getAttribute(JwtAuthFilter.ATTR_PRINCIPAL_ID);
        if (p instanceof Long l) {
            return l;
        }
        if (p != null) {
            try {
                return Long.valueOf(p.toString());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * @return null 表示通过；非 null 为错误文案
     */
    public static String requireSameUser(HttpServletRequest request, Long pathUserId) {
        if (pathUserId == null || pathUserId <= 0) {
            return "参数无效";
        }
        Long login = currentUserId(request);
        if (login == null || login <= 0) {
            return "请先登录";
        }
        if (!login.equals(pathUserId)) {
            return "禁止操作其他用户数据";
        }
        return null;
    }
}
