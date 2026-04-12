package com.gzu.petshop.service.support;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gzu.petshop.dto.common.ChatMessageViewDTO;
import com.gzu.petshop.dto.common.ChatSendMerchantRequest;
import com.gzu.petshop.dto.common.ChatSendUserRequest;
import com.gzu.petshop.dto.common.ChatSessionResponseDTO;
import com.gzu.petshop.dto.common.ChatUnreadMerchantsDTO;
import com.gzu.petshop.dto.merchant.MerchantChatSessionViewDTO;
import com.gzu.petshop.dto.common.MerchantSessionRequest;
import com.gzu.petshop.entity.ChatMessage;
import com.gzu.petshop.entity.ChatSession;
import com.gzu.petshop.entity.Merchant;
import com.gzu.petshop.entity.OrderItem;
import com.gzu.petshop.entity.Orders;
import com.gzu.petshop.entity.Product;
import com.gzu.petshop.entity.ProductDetail;
import com.gzu.petshop.entity.User;
import com.gzu.petshop.mapper.chat.ChatMessageMapper;
import com.gzu.petshop.mapper.chat.ChatSessionMapper;
import com.gzu.petshop.mapper.merchant.MerchantMapper;
import com.gzu.petshop.mapper.order.OrderItemMapper;
import com.gzu.petshop.mapper.order.OrdersMapper;
import com.gzu.petshop.mapper.product.ProductDetailMapper;
import com.gzu.petshop.mapper.product.ProductMapper;
import com.gzu.petshop.mapper.user.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户与商家沟通（表 {@code chat_session}、{@code chat_message}）；仅 {@link #TYPE_USER_TO_MERCHANT} 会话。
 */
@Service
public class ChatService {
    private static final String TYPE_USER_TO_ADMIN = "USER_TO_ADMIN";
    public static final String TYPE_USER_TO_MERCHANT = "USER_TO_MERCHANT";
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final int STATUS_OPEN = 1;

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final UserMapper userMapper;
    private final MerchantMapper merchantMapper;
    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final ProductDetailMapper productDetailMapper;

    public ChatService(ChatSessionMapper chatSessionMapper,
                       ChatMessageMapper chatMessageMapper,
                       UserMapper userMapper,
                       MerchantMapper merchantMapper,
                       OrdersMapper ordersMapper,
                       OrderItemMapper orderItemMapper,
                       ProductMapper productMapper,
                       ProductDetailMapper productDetailMapper) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.userMapper = userMapper;
        this.merchantMapper = merchantMapper;
        this.ordersMapper = ordersMapper;
        this.orderItemMapper = orderItemMapper;
        this.productMapper = productMapper;
        this.productDetailMapper = productDetailMapper;
    }

    @Transactional
    public ChatSessionResponseDTO getOrCreateMerchantSession(MerchantSessionRequest req) {
        if (req == null || req.getUserId() == null || req.getUserId() <= 0) {
            return null;
        }
        Long merchantId = req.getMerchantId();
        if (merchantId == null || merchantId <= 0) {
            return null;
        }
        Merchant m = merchantMapper.selectById(merchantId);
        if (m == null) {
            return null;
        }
        ChatSession s = chatSessionMapper.selectOne(
                new QueryWrapper<ChatSession>()
                        .eq("user_id", req.getUserId())
                        .eq("merchant_id", merchantId)
                        .eq("session_type", TYPE_USER_TO_MERCHANT)
                        .eq("status", STATUS_OPEN)
                        .last("LIMIT 1"));
        if (s == null) {
            s = new ChatSession();
            s.setSessionNo(genSessionNo());
            s.setUserId(req.getUserId());
            s.setOrderId(req.getOrderId());
            s.setAgentAdminId(null);
            s.setMerchantId(merchantId);
            s.setSessionType(TYPE_USER_TO_MERCHANT);
            s.setStatus(STATUS_OPEN);
            s.setCreatedAt(LocalDateTime.now());
            s.setUpdatedAt(LocalDateTime.now());
            chatSessionMapper.insert(s);
        } else if (req.getOrderId() != null && s.getOrderId() == null) {
            s.setOrderId(req.getOrderId());
            s.setUpdatedAt(LocalDateTime.now());
            chatSessionMapper.updateById(s);
        }
        String merchantName = m.getShopName() != null && !m.getShopName().isBlank()
                ? m.getShopName()
                : (m.getUsername() != null ? m.getUsername() : ("商家" + merchantId));
        ChatSessionResponseDTO dto = toSessionResponse(s, merchantName);
        return dto;
    }

    public List<ChatMessageViewDTO> listMessages(Long sessionId) {
        if (sessionId == null) {
            return List.of();
        }
        List<ChatMessage> list = chatMessageMapper.selectList(
                new QueryWrapper<ChatMessage>()
                        .eq("session_id", sessionId)
                        .orderByAsc("created_at"));
        return list.stream().map(this::toMessageView).collect(Collectors.toList());
    }

    /**
     * 用户拉取会话消息：校验归属后将商家消息标为已读（顶栏「联系商家」红点消除）。
     */
    @Transactional
    public List<ChatMessageViewDTO> listMessagesForUser(Long sessionId, Long userId) {
        if (sessionId == null || userId == null || userId <= 0) {
            return List.of();
        }
        ChatSession s = chatSessionMapper.selectById(sessionId);
        if (s == null || !userId.equals(s.getUserId())) {
            return List.of();
        }
        markMerchantMessagesReadByUser(sessionId);
        return listMessages(sessionId);
    }

    /**
     * 商家拉取会话消息：校验权限后将用户消息标为已读（顶栏「用户咨询」红点消除）。
     */
    @Transactional
    public List<ChatMessageViewDTO> listMessagesForMerchant(Long sessionId, Long merchantId) {
        if (sessionId == null || merchantId == null || merchantId <= 0) {
            return List.of();
        }
        if (assertMerchantOwnsSession(sessionId, merchantId) != null) {
            return List.of();
        }
        markUserMessagesReadByMerchant(sessionId);
        return listMessages(sessionId);
    }

    /**
     * 用户顶栏：是否存在未读的商家回复。
     */
    public boolean userHasUnreadMerchantReplies(Long userId) {
        if (userId == null || userId <= 0) {
            return false;
        }
        return chatMessageMapper.countUnreadMerchantMessagesForUser(userId) > 0;
    }

    /**
     * 商家顶栏：是否存在未读的用户咨询消息。
     */
    public boolean merchantHasUnreadUserMessages(Long merchantId) {
        if (merchantId == null || merchantId <= 0) {
            return false;
        }
        return chatMessageMapper.countUnreadUserMessagesForMerchant(merchantId) > 0;
    }

    /**
     * 联系商家页：按店铺标注未读商家回复（与 {@link #userHasUnreadMerchantReplies} 统计口径一致，按 merchant_id 分组）。
     */
    public ChatUnreadMerchantsDTO unreadMerchantIdsForUser(Long userId) {
        if (userId == null || userId <= 0) {
            return new ChatUnreadMerchantsDTO(List.of());
        }
        List<Long> ids = chatMessageMapper.selectMerchantIdsWithUnreadForUser(userId);
        if (ids == null) {
            return new ChatUnreadMerchantsDTO(List.of());
        }
        return new ChatUnreadMerchantsDTO(ids);
    }

    private void markMerchantMessagesReadByUser(Long sessionId) {
        chatMessageMapper.update(
                null,
                new LambdaUpdateWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .eq(ChatMessage::getSenderType, "MERCHANT")
                        .set(ChatMessage::getIsReadByUser, 1)
                        .set(ChatMessage::getReadAt, LocalDateTime.now()));
    }

    private void markUserMessagesReadByMerchant(Long sessionId) {
        chatMessageMapper.update(
                null,
                new LambdaUpdateWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .eq(ChatMessage::getSenderType, "USER")
                        .set(ChatMessage::getIsReadByMerchant, 1));
    }

    @Transactional
    public String sendUserMessage(ChatSendUserRequest req) {
        if (req == null || req.getSessionId() == null || req.getUserId() == null || req.getUserId() <= 0) {
            return "参数无效";
        }
        String content = req.getContent() == null ? "" : req.getContent().trim();
        if (content.isEmpty()) {
            return "消息不能为空";
        }
        ChatSession s = chatSessionMapper.selectById(req.getSessionId());
        if (s == null || !req.getUserId().equals(s.getUserId())) {
            return "会话不存在";
        }
        LocalDateTime now = LocalDateTime.now();
        if (TYPE_USER_TO_ADMIN.equals(s.getSessionType())) {
            return "平台在线客服已关闭，请通过商家沟通：先调用 POST /api/chat/session/merchant 获取会话";
        }
        insertMessage(req.getSessionId(), "USER", req.getUserId(), content, now);
        s.setUpdatedAt(now);
        chatSessionMapper.updateById(s);
        return null;
    }

    /**
     * 校验商家是否可操作该会话（仅 {@link #TYPE_USER_TO_MERCHANT}，且 {@code merchant_id} 一致）。
     *
     * @return 错误文案，成功返回 {@code null}
     */
    public String assertMerchantOwnsSession(Long sessionId, Long merchantId) {
        if (sessionId == null || merchantId == null || merchantId <= 0) {
            return "参数无效";
        }
        ChatSession s = chatSessionMapper.selectById(sessionId);
        if (s == null) {
            return "会话不存在";
        }
        if (TYPE_USER_TO_ADMIN.equals(s.getSessionType())) {
            return "该会话为已废弃的平台客服类型，商家请使用用户与商家会话：POST /api/chat/session/merchant 获取 sessionId";
        }
        if (!TYPE_USER_TO_MERCHANT.equals(s.getSessionType())) {
            return "该会话类型不支持商家回复";
        }
        if (!merchantId.equals(s.getMerchantId())) {
            return "无权限操作该会话";
        }
        return null;
    }

    @Transactional
    public String sendMerchantMessage(ChatSendMerchantRequest req) {
        if (req == null || req.getSessionId() == null || req.getMerchantId() == null || req.getMerchantId() <= 0) {
            return "参数无效";
        }
        String content = req.getContent() == null ? "" : req.getContent().trim();
        if (content.isEmpty()) {
            return "消息不能为空";
        }
        String deny = assertMerchantOwnsSession(req.getSessionId(), req.getMerchantId());
        if (deny != null) {
            return deny;
        }
        ChatSession s = chatSessionMapper.selectById(req.getSessionId());
        LocalDateTime now = LocalDateTime.now();
        insertMessage(req.getSessionId(), "MERCHANT", req.getMerchantId(), content, now);
        s.setUpdatedAt(now);
        chatSessionMapper.updateById(s);
        return null;
    }

    public List<MerchantChatSessionViewDTO> listMerchantSessions(Long merchantId) {
        if (merchantId == null || merchantId <= 0) {
            return List.of();
        }
        List<ChatSession> list = chatSessionMapper.selectList(
                new QueryWrapper<ChatSession>()
                        .eq("merchant_id", merchantId)
                        .eq("session_type", TYPE_USER_TO_MERCHANT)
                        .orderByDesc("updated_at"));
        List<Long> unreadSids = chatMessageMapper.selectSessionIdsWithUnreadUserForMerchant(merchantId);
        Set<Long> unreadSet = new HashSet<>(unreadSids != null ? unreadSids : List.of());
        return list.stream()
                .map(s -> {
                    MerchantChatSessionViewDTO d = toMerchantSessionView(s);
                    d.setUnreadFromUser(unreadSet.contains(s.getSessionId()));
                    return d;
                })
                .collect(Collectors.toList());
    }

    private MerchantChatSessionViewDTO toMerchantSessionView(ChatSession s) {
        MerchantChatSessionViewDTO d = new MerchantChatSessionViewDTO();
        d.setSessionId(s.getSessionId());
        d.setUserId(s.getUserId());
        d.setMerchantId(s.getMerchantId());
        d.setOrderId(s.getOrderId());
        d.setSessionType(s.getSessionType());
        d.setStatus(s.getStatus() != null && s.getStatus() == STATUS_OPEN ? "OPEN" : "CLOSED");
        if (s.getCreatedAt() != null) {
            d.setCreatedAt(ISO.format(s.getCreatedAt()));
        }
        if (s.getUpdatedAt() != null) {
            d.setUpdatedAt(ISO.format(s.getUpdatedAt()));
        }
        User u = userMapper.selectById(s.getUserId());
        d.setUserNickname(u != null && u.getNickname() != null ? u.getNickname() : ("用户" + s.getUserId()));
        fillSessionOrderContext(d, s);
        return d;
    }

    /**
     * 从关联订单中解析本店商品摘要（首行标题 + 主图 + 订单号），无订单则给出说明文案。
     */
    private void fillSessionOrderContext(MerchantChatSessionViewDTO d, ChatSession s) {
        d.setOrderNo("");
        d.setProductTitle("");
        d.setProductImageUrl("");
        d.setRelatedLineCount(0);
        Long oid = s.getOrderId();
        if (oid == null || oid <= 0) {
            d.setProductTitle("咨询未关联订单");
            return;
        }
        Orders order = ordersMapper.selectById(oid);
        if (order == null || !Objects.equals(order.getUserId(), s.getUserId())) {
            d.setProductTitle("订单信息不可用");
            return;
        }
        d.setOrderNo(order.getOrderNo() != null ? order.getOrderNo() : String.valueOf(oid));
        List<OrderItem> lines = orderItemMapper.selectList(
                new QueryWrapper<OrderItem>()
                        .eq("order_id", oid)
                        .eq("merchant_id", s.getMerchantId())
                        .orderByAsc("order_item_id"));
        if (lines.isEmpty()) {
            d.setProductTitle("订单 " + d.getOrderNo());
            return;
        }
        d.setRelatedLineCount(lines.size());
        OrderItem first = lines.get(0);
        Product p = productMapper.selectById(first.getProductId());
        d.setProductTitle(p != null && p.getTitle() != null && !p.getTitle().isBlank() ? p.getTitle() : "商品");
        ProductDetail pd = productDetailMapper.selectById(first.getProductId());
        if (pd != null && pd.getImageUrl() != null && !pd.getImageUrl().isBlank()) {
            d.setProductImageUrl(pd.getImageUrl());
        }
    }

    private void insertMessage(Long sessionId, String senderType, Long senderId, String content, LocalDateTime createdAt) {
        ChatMessage m = new ChatMessage();
        m.setSessionId(sessionId);
        m.setSenderType(senderType);
        m.setSenderId(senderId);
        m.setContent(content);
        m.setAttachmentJson(null);
        if ("USER".equals(senderType)) {
            m.setIsReadByUser(1);
            m.setIsReadByMerchant(0);
        } else if ("MERCHANT".equals(senderType)) {
            m.setIsReadByUser(0);
            m.setIsReadByMerchant(1);
        } else {
            m.setIsReadByUser(0);
            m.setIsReadByMerchant(0);
        }
        m.setReadAt(null);
        m.setCreatedAt(createdAt);
        chatMessageMapper.insert(m);
    }

    private ChatSessionResponseDTO toSessionResponse(ChatSession s, String merchantName) {
        ChatSessionResponseDTO d = new ChatSessionResponseDTO();
        d.setSessionId(s.getSessionId());
        d.setSessionNo(s.getSessionNo());
        d.setUserId(s.getUserId());
        d.setMerchantId(s.getMerchantId());
        d.setOrderId(s.getOrderId());
        d.setSessionType(s.getSessionType());
        d.setStatus(s.getStatus() != null && s.getStatus() == STATUS_OPEN ? "OPEN" : "CLOSED");
        if (s.getCreatedAt() != null) {
            d.setCreatedAt(ISO.format(s.getCreatedAt()));
        }
        if (s.getUpdatedAt() != null) {
            d.setUpdatedAt(ISO.format(s.getUpdatedAt()));
        }
        d.setMerchantName(merchantName);
        return d;
    }

    private ChatMessageViewDTO toMessageView(ChatMessage m) {
        ChatMessageViewDTO d = new ChatMessageViewDTO();
        d.setMessageId(m.getMessageId());
        d.setSessionId(m.getSessionId());
        d.setSenderType(m.getSenderType());
        d.setSenderId(m.getSenderId());
        d.setContent(m.getContent());
        d.setAttachmentUrl(null);
        d.setReadStatus(m.getIsReadByUser() == null ? 0 : m.getIsReadByUser());
        if (m.getCreatedAt() != null) {
            d.setCreatedAt(ISO.format(m.getCreatedAt()));
        }
        return d;
    }

    private static String genSessionNo() {
        return "CS" + System.currentTimeMillis() + (int) (Math.random() * 900 + 100);
    }
}
