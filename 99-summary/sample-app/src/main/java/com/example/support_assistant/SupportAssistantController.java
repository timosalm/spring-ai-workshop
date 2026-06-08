package com.example.support_assistant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
class SupportAssistantController {

    private final SupportAssistantService service;

    SupportAssistantController(SupportAssistantService service) {
        this.service = service;
    }

    // curl -G "http://localhost:8080/api/1.0/chat" -H "X-Conversation-Id: abc-123" --data-urlencode "query=Tell me about Spring AI"
    @GetMapping(path = "/api/{version}/chat")
    SupportResponse chat(@RequestParam String query,
                         @RequestHeader(value = "X-Conversation-Id", required = false) String conversationId) {
        return service.generateResponse(query, conversationId != null ? conversationId : UUID.randomUUID().toString());
    }
}
