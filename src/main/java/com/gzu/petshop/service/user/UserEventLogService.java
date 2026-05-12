package com.gzu.petshop.service.user;

import com.gzu.petshop.dto.user.RecentViewEventRow;
import com.gzu.petshop.entity.UserEventLog;
import com.gzu.petshop.mapper.user.UserEventLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class UserEventLogService {

    private static final Logger log = LoggerFactory.getLogger(UserEventLogService.class);

    private static final AtomicBoolean LOGGED_INSERT_FAILURE = new AtomicBoolean();

    private static final int DEFAULT_EVENT_LOOKBACK_DAYS = 14;
    /** 从库中拉取的最大条数（按时间倒序），略大以便后面截取「最近一段」行为。 */
    private static final int QUERY_FETCH_LIMIT = 72;
    /**
     * 仅用最靠前（最新）的若干条统计类目兴趣，避免演示时「很久以前刷过狗粮 + 刚才刷猫粮」被平均掉，
     * 导致推荐条看起来完全不动。
     */
    private static final int CATEGORY_INTEREST_RECENT_WINDOW = 18;
    /** 参与「刚看过略降权」的商品数（较类目窗口略宽）。 */
    private static final int RECENT_PRODUCT_WINDOW = 28;
    /** 触发在线重算时用于统计浏览序列的最大拉取条数（按时间倒序）。 */
    private static final int TRIGGER_COUNT_FETCH_LIMIT = 600;

    private final UserEventLogMapper userEventLogMapper;

    public UserEventLogService(UserEventLogMapper userEventLogMapper) {
        this.userEventLogMapper = userEventLogMapper;
    }

    /**
     * 记录用户行为；失败静默（不影响主流程）。
     */
    public void tryRecord(Long userId, String eventType, Long productId, String sessionId) {
        if (userId == null || userId <= 0 || eventType == null || eventType.isBlank()) {
            return;
        }
        String t = eventType.trim().toLowerCase(Locale.ROOT);
        if (!t.equals("view") && !t.equals("click") && !t.equals("add_cart") && !t.equals("buy")) {
            return;
        }
        UserEventLog row = new UserEventLog();
        row.setUserId(userId);
        row.setEventType(t);
        row.setProductId(productId);
        row.setSessionId(sessionId);
        row.setEventTime(LocalDateTime.now());
        try {
            userEventLogMapper.insert(row);
        } catch (Exception e) {
            if (LOGGED_INSERT_FAILURE.compareAndSet(false, true)) {
                log.warn("user_event_log 写入失败（浏览行为无法参与推荐重排；同类错误不再重复打日志）: {}", e.toString());
            }
        }
    }

    /**
     * 近期浏览/点击/加购/购买带来的类目兴趣（出现次数越多权重越大），以及最近浏览过的商品（用于轻降权避免刷屏）。
     */
    public BehaviorSignals loadBehaviorSignals(Long userId) {
        if (userId == null || userId <= 0) {
            return BehaviorSignals.empty();
        }
        LocalDateTime since = LocalDateTime.now().minusDays(DEFAULT_EVENT_LOOKBACK_DAYS);
        List<RecentViewEventRow> rows;
        try {
            rows = userEventLogMapper.selectRecentViewLikeEvents(userId, since, QUERY_FETCH_LIMIT);
        } catch (Exception e) {
            return BehaviorSignals.empty();
        }
        Map<Long, Integer> categoryHits = new HashMap<>();
        Set<Long> recentProducts = new LinkedHashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            RecentViewEventRow r = rows.get(i);
            if (i < RECENT_PRODUCT_WINDOW && r.getProductId() != null) {
                recentProducts.add(r.getProductId());
            }
            if (i < CATEGORY_INTEREST_RECENT_WINDOW && r.getCategoryId() != null) {
                categoryHits.merge(r.getCategoryId(), 1, Integer::sum);
            }
        }
        return new BehaviorSignals(categoryHits, recentProducts);
    }

    /**
     * 统计最近 N 天内 view/click 的去重商品数，用于「达到阈值才触发在线重算」。
     */
    public int countDistinctViewedProducts(Long userId, int lookbackDays) {
        if (userId == null || userId <= 0 || lookbackDays <= 0) {
            return 0;
        }
        LocalDateTime since = LocalDateTime.now().minusDays(lookbackDays);
        try {
            Integer n = userEventLogMapper.countDistinctViewLikeProducts(userId, since);
            return n == null ? 0 : Math.max(0, n);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 统计最近 N 天内浏览/点击序列中「去除连续重复商品」后的计数。
     * 示例：A,A,B,A,C,C => 4（A,B,A,C）。
     */
    public int countViewLikeEventsCollapseConsecutiveRepeat(Long userId, int lookbackDays) {
        if (userId == null || userId <= 0 || lookbackDays <= 0) {
            return 0;
        }
        LocalDateTime since = LocalDateTime.now().minusDays(lookbackDays);
        List<Long> ids;
        try {
            ids = userEventLogMapper.selectRecentViewLikeProductIds(userId, since, TRIGGER_COUNT_FETCH_LIMIT);
        } catch (Exception e) {
            return 0;
        }
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int count = 0;
        Long last = null;
        for (Long pid : ids) {
            if (pid == null) {
                continue;
            }
            if (last == null || !last.equals(pid)) {
                count++;
                last = pid;
            }
        }
        return count;
    }

    public static final class BehaviorSignals {
        private final Map<Long, Integer> categoryHits;
        private final Set<Long> recentProductIds;

        private BehaviorSignals(Map<Long, Integer> categoryHits, Set<Long> recentProductIds) {
            this.categoryHits = categoryHits;
            this.recentProductIds = recentProductIds;
        }

        public static BehaviorSignals empty() {
            return new BehaviorSignals(Map.of(), Set.of());
        }

        public boolean isEmpty() {
            return categoryHits.isEmpty() && recentProductIds.isEmpty();
        }

        public Map<Long, Integer> getCategoryHits() {
            return categoryHits;
        }

        public Set<Long> getRecentProductIds() {
            return recentProductIds;
        }
    }
}
