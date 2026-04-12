package com.gzu.petshop.controller.merchant;

import com.gzu.petshop.common.Result;
import com.gzu.petshop.dto.common.ChatMessageViewDTO;
import com.gzu.petshop.dto.common.ChatSendMerchantRequest;
import com.gzu.petshop.dto.common.ChatUnreadBadgeDTO;
import com.gzu.petshop.dto.merchant.MerchantChatSessionViewDTO;
import com.gzu.petshop.service.support.ChatService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
        return Result.success(new ChatUnreadBadgeDTO(chatService.merchantHasUnreadUserMessages(merchantId)));
    }

    @PostMapping("/messages")
    public Result<Void> send(@RequestBody ChatSendMerchantRequest body) {
        String err = chatService.sendMerchantMessage(body);
        return err == null ? Result.success() : Result.error(err);
    }
}
