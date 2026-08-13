package carrental.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Thin HTTP wrapper around OpenRouter's {@code /chat/completions} endpoint
 * with automatic model fallback and HTML stripping.
 *
 * <p>Kept as a Spring bean so a single {@link RestTemplate} is reused
 * across requests (avoiding per-call TCP/TLS handshake overhead).
 */
@Component
public class OpenRouterClient {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterClient.class);

    private static final String OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String SITE_URL = "https://rentify-ifs4.onrender.com";
    private static final String SITE_NAME = "Rentify";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${OPENROUTER_API_KEY:}")
    private String apiKey;

    private final RestTemplate restTemplate;

    public OpenRouterClient() {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        rf.setReadTimeout((int) Duration.ofSeconds(15).toMillis());
        this.restTemplate = new RestTemplate(rf);
    }

    /**
     * Result returned to the controller — includes the response body plus
     * metadata (model actually used, latency, status code).
     */
    public record Result(Map<String, Object> body, String model, long latencyMs,
                         HttpStatusCode status) { }

    /** Thrown when no model returned a usable response. */
    public static class AllModelsFailedException extends RuntimeException {
        public AllModelsFailedException(String msg) { super(msg); }
    }

    /** Thrown when the caller forgot to set OPENROUTER_API_KEY. */
    public static class MissingApiKeyException extends RuntimeException {
        public MissingApiKeyException() { super("OPENROUTER_API_KEY is not configured on the server."); }
    }

    /** Thrown when OpenRouter rejected the key (401/403). */
    public static class AuthRejectedException extends RuntimeException {
        public final String model;
        public AuthRejectedException(String model) {
            super("OpenRouter rejected the API key.");
            this.model = model;
        }
    }

    /**
     * Sends {@code payload} (already containing the system message and the
     * client conversation) to OpenRouter, falling back across {@link
     * PromptTemplate#FALLBACK_MODELS} on transport / 4xx / 5xx errors.
     */
    public Result chat(Map<String, Object> payload) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new MissingApiKeyException();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("HTTP-Referer", SITE_URL);
        headers.set("X-Title", SITE_NAME);
        headers.setBearerAuth(apiKey.trim());

        long startMs = System.currentTimeMillis();
        Throwable last = null;

        for (String model : PromptTemplate.FALLBACK_MODELS) {
            try {
                payload.put("model", model);
                HttpEntity<Map<String, Object>> req = new HttpEntity<>(payload, headers);
                ResponseEntity<String> response = restTemplate.exchange(
                        OPENROUTER_URL, HttpMethod.POST, req, String.class);

                String body = response.getBody();
                if (body != null && body.trim().startsWith("<!DOCTYPE")) {
                    log.warn("OpenRouter returned HTML for model={}", model);
                    last = new RuntimeException("HTML response from OpenRouter");
                    continue;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> json = MAPPER.readValue(body, Map.class);
                if (json.get("error") != null) {
                    log.warn("OpenRouter error for model={}: {}", model, json.get("error"));
                    last = new RuntimeException(String.valueOf(json.get("error")));
                    continue;
                }

                stripHtmlFromResponse(json);
                long elapsed = System.currentTimeMillis() - startMs;
                return new Result(json, model, elapsed, response.getStatusCode());

            } catch (HttpStatusCodeException e) {
                last = e;
                HttpStatus code = HttpStatus.resolve(e.getStatusCode().value());
                log.warn("chat http error model={} status={} body={}",
                        model, code, truncate(e.getResponseBodyAsString(), 300));
                if (code == HttpStatus.UNAUTHORIZED || code == HttpStatus.FORBIDDEN) {
                    throw new AuthRejectedException(model);
                }
                // 402/404/429/5xx -> try next model
            } catch (Exception e) {
                last = e;
                log.warn("chat transport error model={}: {}", model, e.toString());
            }
        }

        throw new AllModelsFailedException(last != null ? last.getMessage() : "unknown");
    }

    /** Recursively strips raw HTML tags from the AI response content. */
    @SuppressWarnings("unchecked")
    private static void stripHtmlFromResponse(Map<String, Object> response) {
        try {
            var choices = (java.util.List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) return;
            for (Map<String, Object> choice : choices) {
                Map<String, Object> message = (Map<String, Object>) choice.get("message");
                if (message == null) continue;
                String content = (String) message.get("content");
                if (content == null) continue;
                message.put("content", content.replaceAll("<[^>]*>", ""));
            }
        } catch (Exception ignored) {
            // safety net — never throw from stripping
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    /** Normalize language tag ("bn", "bangla", "বাংলা" -> "bn"; everything else -> "en"). */
    public static String normalizeLanguage(Object raw) {
        if (raw == null) return "en";
        String s = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        if (s.equals("bn") || s.equals("bangla") || s.equals("bengali") || s.equals("বাংলা")) {
            return "bn";
        }
        return "en";
    }

    /** Best-effort client IP for rate limiting (honors X-Forwarded-For). */
    public static String clientIp(jakarta.servlet.http.HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return req.getRemoteAddr() == null ? "unknown" : req.getRemoteAddr();
    }

    /** Suppress unused warning on LinkedHashMap import used by Jackson. */
    @SuppressWarnings("unused")
    private static final Class<?> PROOF = LinkedHashMap.class;
}