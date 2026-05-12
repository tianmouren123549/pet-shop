package com.gzu.petshop.service.recommend;

import com.gzu.petshop.dto.recommend.ReviewBertScoreRequest;
import com.gzu.petshop.dto.recommend.ReviewBertScoreResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

/**
 * 调用独立 Python 服务，对商品评论文本做 BERT 情感推理并返回聚合分。
 */
@Service
public class ReviewBertInferenceClient {

    private static final Logger log = LoggerFactory.getLogger(ReviewBertInferenceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final boolean enabled;

    public ReviewBertInferenceClient(
            @Value("${app.recommendation.review-bert-inference-enabled:true}") boolean enabled,
            @Value("${app.recommendation.review-bert-inference-base-url:http://127.0.0.1:8766}") String baseUrl
    ) {
        this.enabled = enabled;
        String trimmed = baseUrl != null ? baseUrl.trim() : "";
        if (enabled && !trimmed.isEmpty()) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(3_000);
            factory.setReadTimeout(120_000);
            this.restTemplate = new RestTemplate(factory);
            this.baseUrl = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        } else {
            this.restTemplate = null;
            this.baseUrl = "";
        }
    }

    public boolean isEnabled() {
        return enabled && restTemplate != null && !baseUrl.isEmpty();
    }

    public Optional<ReviewBertScoreResponse> scoreReviews(ReviewBertScoreRequest body) {
        if (restTemplate == null || body == null || body.getProducts() == null || body.getProducts().isEmpty()) {
            return Optional.empty();
        }
        String url = baseUrl + "/v1/score-reviews";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<ReviewBertScoreRequest> entity = new HttpEntity<>(body, headers);
            ResponseEntity<ReviewBertScoreResponse> resp =
                    restTemplate.postForEntity(url, entity, ReviewBertScoreResponse.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                return Optional.empty();
            }
            ReviewBertScoreResponse r = resp.getBody();
            if (r.getScores() == null || r.getScores().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(r);
        } catch (RestClientException e) {
            log.warn("[recommend] 评论 BERT 推理请求失败: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
