package com.gzu.petshop.controller.user;

import com.gzu.petshop.common.Result;
import com.gzu.petshop.dto.common.ChatMessageViewDTO;
import com.gzu.petshop.dto.common.ChatSendUserRequest;
import com.gzu.petshop.dto.common.ChatSessionResponseDTO;
import com.gzu.petshop.dto.common.ChatUnreadBadgeDTO;
import com.gzu.petshop.dto.common.ChatUnreadMerchantsDTO;
import com.gzu.petshop.dto.common.MerchantSessionRequest;
import com.gzu.petshop.service.support.ChatService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

/**
 * 用户端：与商家会话，以及与平台管理员会话。
 */
@RestController
@RequestMapping("/api/chat")
@CrossOrigin
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/session/merchant")
    public Result<ChatSessionResponseDTO> sessionForMerchant(@RequestBody MerchantSessionRequest body) {
        if (body == null || body.getUserId() == null || body.getUserId() <= 0) {
            return Result.error("请先登录");
        }
        if (body.getMerchantId() == null || body.getMerchantId() <= 0) {
            return Result.error("商家信息不存在");
        }
        ChatSessionResponseDTO dto = chatService.getOrCreateMerchantSession(body);
        if (dto == null) {
            return Result.error("商家不存在");
        }
        return Result.success(dto);
    }

    /**
     * 获取或创建「用户 → 平台管理员」会话。
     */
    @PostMapping("/session/admin")
    public Result<ChatSessionResponseDTO> sessionForAdmin(@RequestBody Map<String, Long> body) {
        Long userId = body != null ? body.get("userId") : null;
        if (userId == null || userId <= 0) {
            return Result.error("请先登录");
        }
        ChatSessionResponseDTO dto = chatService.getOrCreateUserAdminSession(userId);
        if (dto == null) {
            return Result.error("用户不存在");
        }
        return Result.success(dto);
    }

    @GetMapping("/session/{sessionId}/messages")
    public Result<List<ChatMessageViewDTO>> messages(@PathVariable Long sessionId, @RequestParam Long userId) {
        if (userId == null || userId <= 0) {
            return Result.error("请先登录");
        }
        return Result.success(chatService.listMessagesForUser(sessionId, userId));
    }

    @GetMapping("/unread-badge")
    public Result<ChatUnreadBadgeDTO> unreadBadge(@RequestParam Long userId) {
        if (userId == null || userId <= 0) {
            return Result.error("请先登录");
        }
        return Result.success(chatService.userChatUnreadBadge(userId));
    }

    @GetMapping("/unread-merchants")
    public Result<ChatUnreadMerchantsDTO> unreadMerchants(@RequestParam Long userId) {
        if (userId == null || userId <= 0) {
            return Result.error("请先登录");
        }
        return Result.success(chatService.unreadMerchantIdsForUser(userId));
    }

    @PostMapping("/messages")
    public Result<Void> send(@RequestBody ChatSendUserRequest body) {
        String err = chatService.sendUserMessage(body);
        return err == null ? Result.success() : Result.error(err);
    }
}
