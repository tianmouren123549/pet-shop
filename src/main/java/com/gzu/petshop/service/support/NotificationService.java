package com.gzu.petshop.service.support;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gzu.petshop.dto.admin.AdminNotifyRestockRequest;
import com.gzu.petshop.dto.common.NotificationViewDTO;
import com.gzu.petshop.dto.common.RestockSubscribeOutcome;
import com.gzu.petshop.dto.common.RestockSubscribeRequest;
import com.gzu.petshop.entity.Notification;
import com.gzu.petshop.entity.Orders;
import com.gzu.petshop.entity.Product;
import com.gzu.petshop.entity.RestockSubscription;
import com.gzu.petshop.mapper.notification.NotificationMapper;
import com.gzu.petshop.mapper.product.ProductMapper;
import com.gzu.petshop.mapper.notification.RestockSubscriptionMapper;
import com.gzu.petshop.mapper.user.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 通知与到货订阅（表 {@code notification}、{@code restock_subscription}）。
 */
@Service
public class NotificationService {
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final NotificationMapper notificationMapper;
    private final RestockSubscriptionMapper restockSubscriptionMapper;
    private final UserMapper userMapper;
    private final ProductMapper productMapper;

    public NotificationService(NotificationMapper notificationMapper,
                               RestockSubscriptionMapper restockSubscriptionMapper,
                               UserMapper userMapper,
                               ProductMapper productMapper) {
        this.notificationMapper = notificationMapper;
        this.restockSubscriptionMapper = restockSubscriptionMapper;
        this.userMapper = userMapper;
        this.productMapper = productMapper;
    }

    public List<NotificationViewDTO> listForUser(Long userId) {
        if (userId == null || userId <= 0) {
            return List.of();
        }
        List<Notification> list = notificationMapper.selectList(
                new QueryWrapper<Notification>()
                        .eq("receiver_type", "USER")
                        .eq("receiver_id", userId)
                        .orderByDesc("created_at"));
        return list.stream().map(this::toView).collect(Collectors.toList());
    }

    public List<NotificationViewDTO> listForMerchant(Long merchantId) {
        if (merchantId == null || merchantId <= 0) {
            return List.of();
        }
        List<Notification> list = notificationMapper.selectList(
                new QueryWrapper<Notification>()
                        .eq("receiver_type", "MERCHANT")
                        .eq("receiver_id", merchantId)
                        .orderByDesc("created_at"));
        return list.stream().map(this::toView).collect(Collectors.toList());
    }

    @Transactional
    public String markUserRead(Long noticeId, Long userId) {
        if (userId == null || userId <= 0) {
            return "请先登录";
        }
        Notification n = notificationMapper.selectById(noticeId);
        if (n == null || !"USER".equals(n.getReceiverType()) || !userId.equals(n.getReceiverId())) {
            return "通知不存在";
        }
        n.setReadStatus(1);
        notificationMapper.updateById(n);
        return null;
    }

    /**
     * 订单创建为「待支付」时提醒用户尽快支付（写入消息中心）。
     */
    public void notifyUserOrderPendingPayment(Orders order) {
        if (order == null || order.getUserId() == null || order.getUserId() <= 0) {
            return;
        }
        String orderNo = order.getOrderNo() != null && !order.getOrderNo().isBlank()
                ? order.getOrderNo()
                : ("订单号 " + order.getOrderId());
        String amt = formatPayAmount(order.getPayAmount());
        String content = "订单「" + orderNo + "」已创建，应付 ¥" + amt
                + "。请尽快前往「我的订单」完成支付；超时未支付将自动取消。";
        content = truncateNoticeContent(content);
        insertNotice("USER", order.getUserId(), "待支付订单", content, null, "ORDER_PENDING_PAY");
    }

    /**
     * 订单变为「已发货」时提醒用户关注物流（写入消息中心）。
     */
    public void notifyUserOrderShipped(Orders order) {
        if (order == null || order.getUserId() == null || order.getUserId() <= 0) {
            return;
        }
        String orderNo = order.getOrderNo() != null && !order.getOrderNo().isBlank()
                ? order.getOrderNo()
                : ("订单号 " + order.getOrderId());
        String logistics = order.getLogisticsNo() != null && !order.getLogisticsNo().isBlank()
                ? "物流单号：" + order.getLogisticsNo() + "。"
                : "请在订单详情查看物流信息。";
        String content = "订单「" + orderNo + "」已发货。" + logistics + "收到货后请及时确认收货。";
        content = truncateNoticeContent(content);
        insertNotice("USER", order.getUserId(), "订单已发货", content, null, "ORDER_SHIPPED");
    }

    /**
     * 管理端催发货：向订单涉及的每个商家写入一条站内通知（不改变订单状态）。
     */
    public void notifyMerchantsAdminUrgeShipment(Long orderId, String orderNo, java.util.Collection<Long> merchantIds) {
        if (orderId == null || merchantIds == null || merchantIds.isEmpty()) {
            return;
        }
        String no = orderNo != null && !orderNo.isBlank() ? orderNo : ("#" + orderId);
        String content = "订单「" + no + "」已支付，客户等待发货。请及时在商家后台处理发货。";
        content = truncateNoticeContent(content);
        for (Long mid : merchantIds) {
            if (mid == null || mid <= 0) {
                continue;
            }
            insertNotice("MERCHANT", mid, "平台催发货", content, null, "ADMIN_URGE_SHIP");
        }
    }

    private static String formatPayAmount(BigDecimal pay) {
        if (pay == null) {
            return "—";
        }
        return pay.stripTrailingZeros().toPlainString();
    }

    private static String truncateNoticeContent(String content) {
        if (content == null) {
            return "";
        }
        if (content.length() <= 500) {
            return content;
        }
        return content.substring(0, 500);
    }

    @Transactional
    public String markMerchantRead(Long noticeId, Long merchantId) {
        if (merchantId == null || merchantId <= 0) {
            return "商家ID无效";
        }
        Notification n = notificationMapper.selectById(noticeId);
        if (n == null || !"MERCHANT".equals(n.getReceiverType()) || !merchantId.equals(n.getReceiverId())) {
            return "通知不存在";
        }
        n.setReadStatus(1);
        notificationMapper.updateById(n);
        return null;
    }

    /**
     * 订阅到货提醒：写订阅表并推送用户/商家通知（与前端 mock 语义一致）。
     *
     * @return 失败时 {@link RestockSubscribeOutcome#errorMessage()} 非空；成功时携带中文成功提示
     */
    @Transactional
    public RestockSubscribeOutcome subscribeRestock(RestockSubscribeRequest req) {
        if (req == null || req.getUserId() == null || req.getUserId() <= 0) {
            return RestockSubscribeOutcome.error("请先登录");
        }
        if (req.getProductId() == null || req.getProductId() <= 0) {
            return RestockSubscribeOutcome.error("商品ID无效");
        }
        if (userMapper.selectById(req.getUserId()) == null) {
            return RestockSubscribeOutcome.error("用户不存在");
        }
        Product p = productMapper.selectById(req.getProductId());
        if (p == null) {
            return RestockSubscribeOutcome.error("商品不存在");
        }
        RestockSubscription exist = restockSubscriptionMapper.selectOne(
                new QueryWrapper<RestockSubscription>()
                        .eq("user_id", req.getUserId())
                        .eq("product_id", req.getProductId())
                        .eq("active", 1));
        if (exist != null) {
            return RestockSubscribeOutcome.ok("您已订阅该商品的到货提醒，补货上架后我们将在消息中心通知您");
        }
        RestockSubscription inactive = restockSubscriptionMapper.selectOne(
                new QueryWrapper<RestockSubscription>()
                        .eq("user_id", req.getUserId())
                        .eq("product_id", req.getProductId())
                        .eq("active", 0));
        if (inactive != null) {
            inactive.setActive(1);
            inactive.setUpdatedAt(LocalDateTime.now());
            restockSubscriptionMapper.updateById(inactive);
            return RestockSubscribeOutcome.ok("到货提醒已重新开启。商品补货后，我们将第一时间通过站内消息通知您");
        }
        RestockSubscription sub = new RestockSubscription();
        sub.setUserId(req.getUserId());
        sub.setProductId(req.getProductId());
        sub.setActive(1);
        sub.setCreatedAt(LocalDateTime.now());
        sub.setUpdatedAt(LocalDateTime.now());
        restockSubscriptionMapper.insert(sub);
        insertNotice("USER", req.getUserId(),
                "到货提醒已开启",
                "已为你关注商品「" + p.getTitle() + "」，到货后将通知你。",
                p.getProductId(), "订阅");
        Long mid = p.getMerchantId();
        if (mid != null && mid > 0) {
            insertNotice("MERCHANT", mid,
                    "用户催补货",
                    "有用户关注商品「" + p.getTitle() + "」到货提醒，请尽快补货（当前库存 "
                            + (p.getStock() == null ? 0 : p.getStock()) + "）。",
                    p.getProductId(), "用户订阅");
        }
        return RestockSubscribeOutcome.ok("到货提醒订阅成功。当前商品暂无库存，补货后我们将第一时间通过消息中心提醒您");
    }

    /**
     * 管理员催商家补货：写入一条 {@code MERCHANT} 通知（与前端 mock 语义一致）。
     *
     * @return 错误文案，成功返回 {@code null}
     */
    @Transactional
    public String adminNotifyMerchantRestock(Long productId, AdminNotifyRestockRequest req) {
        if (productId == null || productId <= 0) {
            return "商品不存在";
        }
        Product p = productMapper.selectById(productId);
        if (p == null) {
            return "商品不存在";
        }
        if (p.getMerchantId() == null || p.getMerchantId() <= 0) {
            return "商品未绑定商家";
        }
        String reason = req != null && req.getReason() != null ? req.getReason().trim() : "";
        if (reason.isEmpty()) {
            int stock = p.getStock() == null ? 0 : p.getStock();
            reason = stock <= 0 ? "售罄" : "库存紧张";
        }
        String note = req != null && req.getNote() != null ? req.getNote().trim() : "";
        int stock = p.getStock() == null ? 0 : p.getStock();
        String base = "商品「" + p.getTitle() + "」" + reason + "（库存 " + stock + "）";
        String content = note.isEmpty() ? base : base + " " + note;
        content = truncateNoticeContent(content);
        insertNotice("MERCHANT", p.getMerchantId(), "补货提醒", content, productId, reason);
        return null;
    }

    private void insertNotice(String receiverType, Long receiverId, String title, String content,
                              Long productId, String reason) {
        Notification n = new Notification();
        n.setReceiverType(receiverType);
        n.setReceiverId(receiverId);
        n.setTitle(title);
        n.setContent(content);
        n.setProductId(productId);
        n.setReason(reason);
        n.setReadStatus(0);
        n.setCreatedAt(LocalDateTime.now());
        notificationMapper.insert(n);
    }

    private NotificationViewDTO toView(Notification n) {
        NotificationViewDTO d = new NotificationViewDTO();
        d.setNoticeId(n.getNoticeId());
        d.setReceiverType(n.getReceiverType());
        d.setReceiverId(n.getReceiverId());
        d.setTitle(n.getTitle());
        d.setContent(n.getContent());
        d.setProductId(n.getProductId());
        d.setReason(n.getReason());
        d.setReadStatus(n.getReadStatus());
        if (n.getCreatedAt() != null) {
            d.setCreatedAt(ISO.format(n.getCreatedAt()));
        }
        return d;
    }

    /**
     * 商家将库存从 0（或以下）补到正数后：通知所有有效订阅用户并失效订阅（与前端 mock 一致）。
     *
     * @param productId 商品 ID
     * @param newStock  补货后的库存
     */
    @Transactional
    public void notifySubscribersProductRestocked(Long productId, int newStock) {
        if (productId == null || productId <= 0) {
            return;
        }
        Product p = productMapper.selectById(productId);
        if (p == null) {
            return;
        }
        List<RestockSubscription> subs = restockSubscriptionMapper.selectList(
                new QueryWrapper<RestockSubscription>()
                        .eq("product_id", productId)
                        .eq("active", 1));
        if (subs.isEmpty()) {
            return;
        }
        String title = p.getTitle() == null ? "" : p.getTitle();
        for (RestockSubscription s : subs) {
            insertNotice("USER", s.getUserId(),
                    "到货通知",
                    "你关注的商品「" + title + "」已补货（当前库存 " + newStock + "）",
                    productId,
                    "到货");
        }
        LocalDateTime now = LocalDateTime.now();
        for (RestockSubscription s : subs) {
            s.setActive(0);
            s.setUpdatedAt(now);
            restockSubscriptionMapper.updateById(s);
        }
    }
}
