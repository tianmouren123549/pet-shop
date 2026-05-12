package com.gzu.petshop.controller.admin;

import com.gzu.petshop.common.Result;
import com.gzu.petshop.dto.admin.AdminChatSendRequest;
import com.gzu.petshop.dto.admin.AdminSupportSessionDTO;
import com.gzu.petshop.dto.common.ChatMessageViewDTO;
import com.gzu.petshop.service.audit.AdminAuditService;
import com.gzu.petshop.service.support.ChatService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 平台管理员处理「用户/商家联系平台」会话。
 */
@RestController
@RequestMapping("/api/admin/support")
@CrossOrigin
public class AdminSupportController {

    private final ChatService chatService;
    private final AdminAuditService adminAuditService;

    public AdminSupportController(ChatService chatService, AdminAuditService adminAuditService) {
        this.chatService = chatService;
        this.adminAuditService = adminAuditService;
    }

    @GetMapping("/sessions")
    public Result<List<AdminSupportSessionDTO>> listSessions() {
        return Result.success(chatService.listAdminSupportSessions());
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<ChatMessageViewDTO>> messages(@PathVariable Long sessionId) {
        return Result.success(chatService.listMessagesForAdmin(sessionId));
    }

    @PostMapping("/messages")
    public Result<Void> send(HttpServletRequest request, @RequestBody AdminChatSendRequest body) {
        Long aid = adminAuditService.currentAdminId(request);
        if (aid == null || aid <= 0) {
            return Result.error("请先登录管理端");
        }
        if (body == null || body.getSessionId() == null) {
            return Result.error("会话无效");
        }
        String err = chatService.sendAdminSupportMessage(body.getSessionId(), aid, body.getContent());
        return err == null ? Result.success() : Result.error(err);
    }

    @GetMapping("/unread-badge")
    public Result<Map<String, Long>> unreadBadge() {
        return Result.success(Map.of("unread", chatService.countUnreadAdminInbox()));
    }
}
