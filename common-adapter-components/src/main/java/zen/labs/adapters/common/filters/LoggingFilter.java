package zen.labs.adapters.common.filters;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

@Component
@Slf4j
public class LoggingFilter implements Filter {

    private static final int MAX_PAYLOAD_LENGTH = 1000;
    private static final int CACHE_LIMIT = 1024; // cache the first 1K of the request

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }
        // Wrap request and response to cache content
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(httpRequest, CACHE_LIMIT);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(httpResponse);

        Instant startTime = Instant.now();

        try {
            logRequest(requestWrapper);
            chain.doFilter(requestWrapper, responseWrapper);
        } finally {
            Duration duration = Duration.between(startTime, Instant.now());
            logResponse(requestWrapper, responseWrapper, duration);

            // IMPORTANT: Copy cached response content to actual response
            responseWrapper.copyBodyToResponse();
        }
    }

    private void logRequest(ContentCachingRequestWrapper request) {
        RequestInfo requestInfo = RequestInfo.from(request);
        String payload = new String(request.getContentAsByteArray(), StandardCharsets.UTF_8);
        payload = payload.length() > MAX_PAYLOAD_LENGTH ? payload.substring(0, MAX_PAYLOAD_LENGTH) + "..." : payload;

        log.info(
                "Request: {} {}{} - Payload: {}",
                requestInfo.method,
                requestInfo.uri,
                requestInfo.queryString != null ? "?" + requestInfo.queryString : "",
                payload);
    }

    private void logResponse(
            ContentCachingRequestWrapper request, ContentCachingResponseWrapper response, Duration duration) {
        RequestInfo requestInfo = RequestInfo.from(request);
        String payload = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
        payload = payload.length() > MAX_PAYLOAD_LENGTH ? payload.substring(0, MAX_PAYLOAD_LENGTH) + "..." : payload;

        log.info(
                "Response: {} {}{} - Payload: {} - Duration: {}ms",
                requestInfo.method,
                requestInfo.uri,
                requestInfo.queryString != null ? "?" + requestInfo.queryString : "",
                payload,
                duration.toMillis());
    }

    private record RequestInfo(String method, String uri, String queryString) {
        public static RequestInfo from(ContentCachingRequestWrapper request) {
            String method = request.getMethod();
            String uri = request.getRequestURI();
            String queryString = request.getQueryString();
            return new RequestInfo(method, uri, queryString);
        }
    }
}
