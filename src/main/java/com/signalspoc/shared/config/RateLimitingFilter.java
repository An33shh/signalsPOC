package com.signalspoc.shared.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    @Value("${signalspoc.rate-limit.enabled:true}")
    private boolean enabled;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    // Rate limit: 100 requests per minute for sync endpoints
    private static final int SYNC_REQUESTS_PER_MINUTE = 10;
    // Rate limit: 1000 requests per minute for read endpoints
    private static final int READ_REQUESTS_PER_MINUTE = 100;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String clientId = getClientIdentifier(request);
        String path = request.getRequestURI();

        Bucket bucket = resolveBucket(clientId, path);

        if (bucket.tryConsume(1)) {
            // Add rate limit headers
            response.addHeader("X-Rate-Limit-Remaining", String.valueOf(bucket.getAvailableTokens()));
            filterChain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded for client: {} on path: {}", clientId, path);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("""
                {
                    "error": "Too Many Requests",
                    "message": "Rate limit exceeded. Please try again later.",
                    "status": 429
                }
                """);
        }
    }

    private String getClientIdentifier(HttpServletRequest request) {
        // Use authenticated user if available, otherwise use IP
        String user = request.getRemoteUser();
        if (user != null) {
            return "user:" + user;
        }

        // Check for X-Forwarded-For header (for proxied requests)
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            return "ip:" + forwardedFor.split(",")[0].trim();
        }

        return "ip:" + request.getRemoteAddr();
    }

    private Bucket resolveBucket(String clientId, String path) {
        String bucketKey = clientId + ":" + getBucketType(path);
        return buckets.computeIfAbsent(bucketKey, k -> createBucket(path));
    }

    private String getBucketType(String path) {
        if (path.contains("/sync/") && !path.contains("/logs")) {
            return "sync";
        }
        return "read";
    }

    private Bucket createBucket(String path) {
        Bandwidth limit;

        if (path.contains("/sync/") && !path.contains("/logs")) {
            // Stricter limit for sync operations: 10 requests per minute
            limit = Bandwidth.builder()
                    .capacity(SYNC_REQUESTS_PER_MINUTE)
                    .refillGreedy(SYNC_REQUESTS_PER_MINUTE, Duration.ofMinutes(1))
                    .build();
        } else {
            // More lenient limit for read operations: 100 requests per minute
            limit = Bandwidth.builder()
                    .capacity(READ_REQUESTS_PER_MINUTE)
                    .refillGreedy(READ_REQUESTS_PER_MINUTE, Duration.ofMinutes(1))
                    .build();
        }

        return Bucket.builder().addLimit(limit).build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) {
            return true;
        }
        String path = request.getRequestURI();
        // Don't rate limit actuator, swagger, or static resources
        return path.startsWith("/actuator") ||
               path.startsWith("/swagger") ||
               path.startsWith("/api-docs") ||
               path.startsWith("/v3/api-docs");
    }
}
