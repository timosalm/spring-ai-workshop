package com.example.support_assistant;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
class SupportAssistantController {

    private static final String CONVERSATION_ID_HEADER = "X-Conversation-Id";

    private final SupportAssistantService service;

    SupportAssistantController(SupportAssistantService service) {
        this.service = service;
    }

    // curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Tell me about Spring AI"
    @GetMapping(path = "/api/v{version}/chat")
    ResponseEntity<SupportResponse> chat(@RequestParam String query,
                                            @RequestHeader(value = CONVERSATION_ID_HEADER, required = false) String conversationId) {
        var id = (conversationId != null) ? conversationId : UUID.randomUUID().toString();
        var response = service.generateResponse(query, id);
        return ResponseEntity.ok().header(CONVERSATION_ID_HEADER, id).body(response);
    }
}
