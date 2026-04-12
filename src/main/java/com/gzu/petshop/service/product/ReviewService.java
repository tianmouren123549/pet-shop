package com.gzu.petshop.service.product;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gzu.petshop.dto.common.ReviewViewDTO;
import com.gzu.petshop.entity.Review;
import com.gzu.petshop.entity.User;
import com.gzu.petshop.mapper.product.ReviewMapper;
import com.gzu.petshop.mapper.user.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ReviewService {
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ReviewMapper reviewMapper;
    private final UserMapper userMapper;

    public ReviewService(ReviewMapper reviewMapper, UserMapper userMapper) {
        this.reviewMapper = reviewMapper;
        this.userMapper = userMapper;
    }

    /**
     * 商品下可见评价，附带用户昵称（表 {@code user.nickname}）。
     */
    public List<ReviewViewDTO> getProductReviews(Long productId) {
        List<Review> list = reviewMapper.selectList(
                new QueryWrapper<Review>()
                        .eq("product_id", productId)
                        .eq("status", 1)
                        .orderByDesc("created_at"));
        if (list.isEmpty()) {
            return List.of();
        }
        Set<Long> uids = list.stream().map(Review::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, User> userMap = new HashMap<>();
        if (!uids.isEmpty()) {
            for (User u : userMapper.selectBatchIds(uids)) {
                userMap.put(u.getUserId(), u);
            }
        }
        return list.stream().map(r -> toView(r, userMap.get(r.getUserId()))).collect(Collectors.toList());
    }

    private ReviewViewDTO toView(Review r, User u) {
        ReviewViewDTO d = new ReviewViewDTO();
        d.setReviewId(r.getReviewId());
        d.setUserId(r.getUserId());
        d.setProductId(r.getProductId());
        d.setRating(r.getRating());
        d.setContent(r.getContent());
        d.setGoldenRetrieverScore(r.getGoldenRetrieverScore());
        if (r.getCreatedAt() != null) {
            d.setCreatedAt(ISO.format(r.getCreatedAt()));
        }
        if (u != null && u.getNickname() != null && !u.getNickname().isBlank()) {
            d.setUserNickname(u.getNickname());
        } else {
            d.setUserNickname("");
        }
        return d;
    }

    /**
     * 追加一条评价；同一用户对同一商品可多次评价（追评、订单页与详情页重复提交等场景）。
     */
    @Transactional
    public Review addReview(Long userId, Long productId, Integer rating, String content) {
        Review review = new Review();
        review.setUserId(userId);
        review.setProductId(productId);
        review.setRating(rating);
        review.setContent(content);
        review.setStatus(1);
        review.setGoldenRetrieverScore(computeGoldenRetrieverScore(content));
        review.setCreatedAt(LocalDateTime.now());
        reviewMapper.insert(review);
        return review;
    }

    /**
     * 与前端 mock 一致：文案含「金毛」则偏高相关分，否则低分（演示用规则，可替换为模型分）。
     */
    private static BigDecimal computeGoldenRetrieverScore(String content) {
        if (content != null && content.contains("金毛")) {
            return new BigDecimal("0.9000");
        }
        return new BigDecimal("0.1000");
    }
}
