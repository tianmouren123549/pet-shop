package com.gzu.petshop.controller.merchant;

import com.gzu.petshop.common.Result;
import com.gzu.petshop.dto.common.ChatMessageViewDTO;
import com.gzu.petshop.dto.common.ChatSendMerchantRequest;
import com.gzu.petshop.dto.common.ChatSessionResponseDTO;
import com.gzu.petshop.dto.common.ChatUnreadBadgeDTO;
import com.gzu.petshop.dto.merchant.MerchantChatSessionViewDTO;
import com.gzu.petshop.service.support.ChatService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 商家端客服会话。
 */
@RestController
@RequestMapping("/api/merchant/chat")
@CrossOrigin
public class MerchantChatController {
    private final ChatService chatService;

    public MerchantChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/sessions/{merchantId}")
    public Result<List<MerchantChatSessionViewDTO>> sessions(@PathVariable Long merchantId) {
        if (merchantId == null || merchantId <= 0) {
            return Result.error("请先登录商家账号");
        }
        return Result.success(chatService.listMerchantSessions(merchantId));
    }

    /**
     * 获取或创建「商家 → 平台管理员」会话。
     */
    @PostMapping("/session/admin")
    public Result<ChatSessionResponseDTO> sessionForAdmin(@RequestBody Map<String, Long> body) {
        Long merchantId = body != null ? body.get("merchantId") : null;
        if (merchantId == null || merchantId <= 0) {
            return Result.error("请先登录商家账号");
        }
        ChatSessionResponseDTO dto = chatService.getOrCreateMerchantAdminSession(merchantId);
        if (dto == null) {
            return Result.error("商家不存在");
        }
        return Result.success(dto);
    }

    @GetMapping("/session/{sessionId}/messages")
    public Result<List<ChatMessageViewDTO>> messages(
            @PathVariable Long sessionId, @RequestParam Long merchantId) {
        if (merchantId == null || merchantId <= 0) {
            return Result.error("请先登录商家账号");
        }
        return Result.success(chatService.listMessagesForMerchant(sessionId, merchantId));
    }

    @GetMapping("/unread-badge")
    public Result<ChatUnreadBadgeDTO> unreadBadge(@RequestParam Long merchantId) {
        if (merchantId == null || merchantId <= 0) {
            return Result.error("请先登录商家账号");
        }
        return Result.success(chatService.merchantChatUnreadBadge(merchantId));
    }

    @PostMapping("/messages")
    public Result<Void> send(@RequestBody ChatSendMerchantRequest body) {
        String err = chatService.sendMerchantMessage(body);
        return err == null ? Result.success() : Result.error(err);
    }
}
