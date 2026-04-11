package com.gzu.petshop.controller.user;

import com.gzu.petshop.common.Result;
import com.gzu.petshop.dto.common.ChatMessageViewDTO;
import com.gzu.petshop.dto.common.ChatSendUserRequest;
import com.gzu.petshop.dto.common.ChatSessionResponseDTO;
import com.gzu.petshop.dto.common.MerchantSessionRequest;
import com.gzu.petshop.service.ChatService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户端与商家的会话与消息（无平台客服会话）。
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

    @GetMapping("/session/{sessionId}/messages")
    public Result<List<ChatMessageViewDTO>> messages(@PathVariable Long sessionId) {
        return Result.success(chatService.listMessages(sessionId));
    }

    @PostMapping("/messages")
    public Result<Void> send(@RequestBody ChatSendUserRequest body) {
        String err = chatService.sendUserMessage(body);
        return err == null ? Result.success() : Result.error(err);
    }
}
