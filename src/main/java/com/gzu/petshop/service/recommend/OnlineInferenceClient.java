package com.gzu.petshop.service.recommend;

import com.gzu.petshop.dto.recommend.OnlineScoreRequest;
import com.gzu.petshop.dto.recommend.OnlineScoreResponse;
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

import java.util.List;
import java.util.Optional;

/**
 * 调用 XGB 在线推理服务（FastAPI），失败时由 {@link RecommendationService} 回退离线表。
 */
@Service
public class OnlineInferenceClient {

    private static final Logger log = LoggerFactory.getLogger(OnlineInferenceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public OnlineInferenceClient(
            @Value("${app.recommendation.online-inference-enabled:false}") boolean enabled,
            @Value("${app.recommendation.online-inference-base-url:}") String baseUrl
    ) {
        String trimmed = baseUrl != null ? baseUrl.trim() : "";
        if (enabled && !trimmed.isEmpty()) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(2_000);
            factory.setReadTimeout(12_000);
            this.restTemplate = new RestTemplate(factory);
            this.baseUrl = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        } else {
            this.restTemplate = null;
            this.baseUrl = "";
        }
    }

    /**
     * @param userOrderCnt 与训练导出一致：已支付/发货/完成订单中去重后的商品种数
     */
    public Optional<OnlineScoreResponse> score(int userOrderCnt, List<Long> productIds) {
        if (restTemplate == null || productIds == null || productIds.isEmpty()) {
            return Optional.empty();
        }
        String url = baseUrl + "/v1/score";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            OnlineScoreRequest body = new OnlineScoreRequest(userOrderCnt, productIds);
            HttpEntity<OnlineScoreRequest> entity = new HttpEntity<>(body, headers);
            ResponseEntity<OnlineScoreResponse> resp =
                    restTemplate.postForEntity(url, entity, OnlineScoreResponse.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                return Optional.empty();
            }
            OnlineScoreResponse r = resp.getBody();
            if (r.getScores() == null || r.getScores().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(r);
        } catch (RestClientException e) {
            log.warn("[recommend] 在线推理请求失败，将回退离线推荐: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
