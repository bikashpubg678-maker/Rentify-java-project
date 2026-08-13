package carrental.chat;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Thin HTTP plumbing for the public AI chat endpoint.
 *
 * <p>All business logic lives in {@link ChatService} / {@link
 * OpenRouterClient}; this class only handles rate limiting, language
 * normalization, and translating exceptions into HTTP responses.
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final RateLimiter rateLimiter;

    public ChatController(ChatService chatService, RateLimiter rateLimiter) {
        this.chatService = chatService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody Map<String, Object> payload,
                                  HttpServletRequest request) {
        String ip = OpenRouterClient.clientIp(request);
        if (!rateLimiter.tryAcquire(ip)) {
            String lang = OpenRouterClient.normalizeLanguage(payload.get("language"));
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of(
                            "error", "Too many requests. Please wait a minute and try again.",
                            "language", lang));
        }

        String language = OpenRouterClient.normalizeLanguage(payload.get("language"));

        try {
            ChatService.Reply reply = chatService.handle(payload, language);
            return ResponseEntity.ok()
                    .header("X-Chat-Model", reply.model())
                    .header("X-Chat-Language", reply.language())
                    .header("X-Chat-Latency-Ms", String.valueOf(reply.latencyMs()))
                    .body(reply.body());

        } catch (OpenRouterClient.MissingApiKeyException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header("X-Chat-Error", "missing-api-key")
                    .body(Map.of("error", e.getMessage(), "language", language));

        } catch (OpenRouterClient.AuthRejectedException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header("X-Chat-Error", "openrouter-auth")
                    .header("X-Chat-Model", e.model)
                    .body(Map.of(
                            "error", "OpenRouter rejected the API key. " +
                                    "Set OPENROUTER_API_KEY in the server environment.",
                            "language", language));

        } catch (OpenRouterClient.AllModelsFailedException e) {
            log.error("All OpenRouter models failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .header("X-Chat-Error", "all-models-failed")
                    .body(Map.of(
                            "error", "All AI models failed. Last error: " + e.getMessage(),
                            "language", language));

        } catch (Exception e) {
            log.error("chat unhandled error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Failed to process chat request: " + e.getMessage(),
                            "language", language));
        }
    }
}