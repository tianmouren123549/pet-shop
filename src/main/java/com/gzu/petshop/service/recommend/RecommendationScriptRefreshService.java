package com.gzu.petshop.service.recommend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 异步执行已配置的 PowerShell 离线推荐刷新脚本（启动 / 定时 / 每次拉推荐接口 等入口共用）。
 */
@Service
public class RecommendationScriptRefreshService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationScriptRefreshService.class);

    private final Object scriptGate = new Object();
    private volatile boolean scriptRunning;
    private volatile long lastApiTriggeredStartMillis;

    @Value("${app.demo.refresh-recommendations-script:}")
    private String refreshScriptPath;

    /** 演示：每次请求 {@code GET /api/recommendations/user/...} 时是否尝试异步重跑脚本（默认关）。 */
    @Value("${app.demo.refresh-recommendations-on-each-api-call:false}")
    private boolean refreshOnEachRecommendationApiCall;

    /**
     * 由「拉推荐接口」触发时的最小间隔（毫秒），避免演示时连续刷新页面压垮本机。
     */
    @Value("${app.demo.refresh-recommendations-min-interval-ms:180000}")
    private long minIntervalMsForApiTrigger;

    /**
     * 若配置了脚本路径且文件存在，则在后台线程中执行（不阻塞调用方）。
     * 用于启动、定时任务等：不应用「接口节流」间隔，但仍与其它入口互斥（同一时间只跑一个脚本）。
     */
    public void submitAsyncIfConfigured() {
        startScriptAsync(TriggerKind.STARTUP_OR_SCHEDULED);
    }

    /**
     * 在每次调用推荐列表接口时触发（受 {@link #refreshOnEachRecommendationApiCall} 与节流、互斥约束）。
     * <p>本次 HTTP 仍立即读当前库；脚本跑完后用户再次打开首页才会看到新离线结果。</p>
     */
    public void notifyRecommendationApiCalled() {
        if (!refreshOnEachRecommendationApiCall) {
            return;
        }
        startScriptAsync(TriggerKind.API_REQUEST);
    }

    private void startScriptAsync(TriggerKind kind) {
        synchronized (scriptGate) {
            if (scriptRunning) {
                log.debug("Recommendation refresh script already running; skip ({})", kind);
                return;
            }
            long now = System.currentTimeMillis();
            if (kind == TriggerKind.API_REQUEST) {
                if (now - lastApiTriggeredStartMillis < minIntervalMsForApiTrigger) {
                    log.debug(
                            "Recommendation refresh throttled for api-call (min-interval-ms={})",
                            minIntervalMsForApiTrigger
                    );
                    return;
                }
            }
            Optional<Path> script = resolveScriptPath();
            if (script.isEmpty()) {
                return;
            }
            if (kind == TriggerKind.API_REQUEST) {
                lastApiTriggeredStartMillis = now;
            }
            scriptRunning = true;
            Path path = script.get();
            Thread t = new Thread(
                    () -> {
                        try {
                            runScript(path);
                        } finally {
                            synchronized (scriptGate) {
                                scriptRunning = false;
                            }
                        }
                    },
                    "recommendation-refresh-script"
            );
            t.setDaemon(true);
            t.start();
        }
    }

    private Optional<Path> resolveScriptPath() {
        if (refreshScriptPath == null || refreshScriptPath.isBlank()) {
            log.debug("Recommendation script path empty; skip");
            return Optional.empty();
        }
        Path script = Paths.get(refreshScriptPath.trim());
        if (!Files.isRegularFile(script)) {
            log.warn("Recommendation refresh script not found: {}", script.toAbsolutePath());
            return Optional.empty();
        }
        return Optional.of(script);
    }

    private void runScript(Path script) {
        try {
            log.info("Starting recommendation refresh script: {}", script.toAbsolutePath());
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-File",
                    script.toAbsolutePath().toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            Thread reader = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(p.getInputStream(), Charset.defaultCharset()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        log.info("[recommendation-refresh] {}", line);
                    }
                } catch (IOException ignored) {
                    // process ended
                }
            }, "recommendation-refresh-log");
            reader.setDaemon(true);
            reader.start();
            boolean finished = p.waitFor(30, TimeUnit.MINUTES);
            if (!finished) {
                p.destroyForcibly();
                log.error("Recommendation refresh timed out after 30 minutes");
                return;
            }
            int code = p.exitValue();
            if (code != 0) {
                log.warn("Recommendation refresh exited with code {}", code);
            } else {
                log.info("Recommendation refresh finished successfully");
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Recommendation refresh failed", e);
        }
    }

    private enum TriggerKind {
        STARTUP_OR_SCHEDULED,
        API_REQUEST
    }
}
