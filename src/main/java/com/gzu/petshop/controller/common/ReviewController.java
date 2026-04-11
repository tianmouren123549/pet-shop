package com.gzu.petshop.controller.common;

import com.gzu.petshop.common.Result;
import com.gzu.petshop.dto.common.ReviewViewDTO;
import com.gzu.petshop.entity.Review;
import com.gzu.petshop.service.ReviewService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
@CrossOrigin
public class ReviewController {
    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }
    
    @GetMapping("/product/{productId}")
    public Result<List<ReviewViewDTO>> getProductReviews(@PathVariable Long productId) {
        return Result.success(reviewService.getProductReviews(productId));
    }
    
    @PostMapping
    public Result<Review> addReview(@RequestBody Map<String, Object> params) {
        if (params == null || params.get("userId") == null || params.get("productId") == null
                || params.get("rating") == null || params.get("content") == null) {
            return Result.error("参数无效");
        }
        try {
            Long userId = Long.valueOf(params.get("userId").toString());
            Long productId = Long.valueOf(params.get("productId").toString());
            Integer rating = Integer.valueOf(params.get("rating").toString());
            String content = params.get("content").toString().trim();
            if (content.isEmpty()) {
                return Result.error("评价内容不能为空");
            }
            Review r = reviewService.addReview(userId, productId, rating, content);
            return r == null ? Result.error("您已评价过该商品") : Result.success(r);
        } catch (NumberFormatException e) {
            return Result.error("参数格式错误");
        }
    }
}
