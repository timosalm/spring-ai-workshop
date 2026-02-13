package com.example.supportassistant.mock;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Mock OpenAI-compatible API controller.
 * Provides deterministic responses for workshop exercises without requiring a real API key.
 */
@Profile("mock")
@RestController
@RequestMapping("/mock/v1")
public class MockOpenAiController {

    private static final Logger logger = LoggerFactory.getLogger(MockOpenAiController.class);
    private static final String MODEL_ID = "mock-gpt-4";

    private final MockResponseRegistry responseRegistry;

    public MockOpenAiController(MockResponseRegistry responseRegistry) {
        this.responseRegistry = responseRegistry;
    }

    /**
     * List available models - OpenAI compatible endpoint.
     */
    @GetMapping("/models")
    public Map<String, Object> listModels() {
        logger.info("Mock API: Listing models");
        return Map.of(
            "object", "list",
            "data", List.of(
                Map.of(
                    "id", MODEL_ID,
                    "object", "model",
                    "created", Instant.now().getEpochSecond(),
                    "owned_by", "tanzu-workshop"
                )
            )
        );
    }

    /**
     * Chat completions endpoint - OpenAI compatible.
     * Supports both blocking and streaming modes.
     */
    @PostMapping("/chat/completions")
    public ResponseEntity<?> chatCompletions(@RequestBody ChatCompletionRequest request) {
        logger.info("Mock API: Chat completion request - stream={}", request.stream());

        // Extract the user message
        String userMessage = extractUserMessage(request.messages());
        logger.debug("Mock API: User message: {}", userMessage);

        // Check for tool definitions and tool-related prompts
        if (request.tools() != null && !request.tools().isEmpty()) {
            String response = responseRegistry.findResponse(userMessage);
            if (responseRegistry.isToolCall(userMessage)) {
                return handleToolCall(request, response);
            }
        }

        // Get the response
        String responseContent = responseRegistry.findResponse(userMessage);

        // Handle tool call response format
        if (responseContent.startsWith("TOOL_CALL:")) {
            responseContent = responseRegistry.getDefaultResponse();
        }

        if (request.stream()) {
            return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(streamResponse(responseContent));
        } else {
            return ResponseEntity.ok(createCompletionResponse(responseContent));
        }
    }

    /**
     * Embeddings endpoint - OpenAI compatible.
     * Returns deterministic embeddings based on text hash.
     */
    @PostMapping("/embeddings")
    public Map<String, Object> createEmbeddings(@RequestBody EmbeddingRequest request) {
        logger.info("Mock API: Embedding request for {} inputs", request.input().size());

        List<Map<String, Object>> data = new ArrayList<>();
        int index = 0;

        for (String input : request.input()) {
            float[] embedding = responseRegistry.generateEmbedding(input);
            List<Float> embeddingList = new ArrayList<>();
            for (float f : embedding) {
                embeddingList.add(f);
            }

            data.add(Map.of(
                "object", "embedding",
                "index", index++,
                "embedding", embeddingList
            ));
        }

        return Map.of(
            "object", "list",
            "data", data,
            "model", "mock-text-embedding-ada-002",
            "usage", Map.of(
                "prompt_tokens", request.input().stream().mapToInt(String::length).sum() / 4,
                "total_tokens", request.input().stream().mapToInt(String::length).sum() / 4
            )
        );
    }

    private String extractUserMessage(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        // Find the last user message
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if ("user".equals(msg.role())) {
                return msg.content() != null ? msg.content() : "";
            }
        }
        return messages.get(messages.size() - 1).content();
    }

    private ResponseEntity<?> handleToolCall(ChatCompletionRequest request, String response) {
        String toolName = responseRegistry.getToolName(response);

        // Find the matching tool
        Map<String, Object> tool = findTool(request.tools(), toolName);

        if (tool != null) {
            return ResponseEntity.ok(createToolCallResponse(toolName, "{}"));
        }

        // Fall back to regular response if tool not found
        return ResponseEntity.ok(createCompletionResponse(responseRegistry.findResponse("")));
    }

    private Map<String, Object> findTool(List<Map<String, Object>> tools, String toolName) {
        if (tools == null) return null;
        for (Map<String, Object> tool : tools) {
            @SuppressWarnings("unchecked")
            Map<String, Object> function = (Map<String, Object>) tool.get("function");
            if (function != null && toolName.equals(function.get("name"))) {
                return tool;
            }
        }
        return null;
    }

    private Map<String, Object> createCompletionResponse(String content) {
        String completionId = "chatcmpl-mock-" + UUID.randomUUID().toString().substring(0, 8);

        return Map.of(
            "id", completionId,
            "object", "chat.completion",
            "created", Instant.now().getEpochSecond(),
            "model", MODEL_ID,
            "choices", List.of(
                Map.of(
                    "index", 0,
                    "message", Map.of(
                        "role", "assistant",
                        "content", content
                    ),
                    "finish_reason", "stop"
                )
            ),
            "usage", Map.of(
                "prompt_tokens", 50,
                "completion_tokens", content.length() / 4,
                "total_tokens", 50 + content.length() / 4
            )
        );
    }

    private Map<String, Object> createToolCallResponse(String toolName, String arguments) {
        String completionId = "chatcmpl-mock-" + UUID.randomUUID().toString().substring(0, 8);
        String toolCallId = "call_mock_" + UUID.randomUUID().toString().substring(0, 8);

        return Map.of(
            "id", completionId,
            "object", "chat.completion",
            "created", Instant.now().getEpochSecond(),
            "model", MODEL_ID,
            "choices", List.of(
                Map.of(
                    "index", 0,
                    "message", Map.of(
                        "role", "assistant",
                        "content", (Object) null,
                        "tool_calls", List.of(
                            Map.of(
                                "id", toolCallId,
                                "type", "function",
                                "function", Map.of(
                                    "name", toolName,
                                    "arguments", arguments
                                )
                            )
                        )
                    ),
                    "finish_reason", "tool_calls"
                )
            ),
            "usage", Map.of(
                "prompt_tokens", 50,
                "completion_tokens", 25,
                "total_tokens", 75
            )
        );
    }

    private Flux<String> streamResponse(String content) {
        // Split content into chunks for streaming simulation
        String[] words = content.split("(?<=\\s)");

        return Flux.fromArray(words)
            .delayElements(Duration.ofMillis(50))
            .map(this::createStreamChunk)
            .concatWith(Flux.just(createStreamDoneChunk()));
    }

    private String createStreamChunk(String content) {
        String chunkId = "chatcmpl-mock-" + UUID.randomUUID().toString().substring(0, 8);
        return String.format(
            "{\"id\":\"%s\",\"object\":\"chat.completion.chunk\",\"created\":%d,\"model\":\"%s\"," +
            "\"choices\":[{\"index\":0,\"delta\":{\"content\":\"%s\"},\"finish_reason\":null}]}\n\n",
            chunkId, Instant.now().getEpochSecond(), MODEL_ID, escapeJson(content)
        );
    }

    private String createStreamDoneChunk() {
        return "[DONE]\n\n";
    }

    private String escapeJson(String text) {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    // Request/Response DTOs
    public record ChatCompletionRequest(
        String model,
        List<Message> messages,
        @JsonProperty("stream") Boolean stream,
        List<Map<String, Object>> tools,
        @JsonProperty("tool_choice") Object toolChoice
    ) {
        public Boolean stream() {
            return stream != null && stream;
        }
    }

    public record Message(
        String role,
        String content,
        @JsonProperty("tool_calls") List<Map<String, Object>> toolCalls,
        @JsonProperty("tool_call_id") String toolCallId
    ) {}

    public record EmbeddingRequest(
        String model,
        List<String> input
    ) {}
}
