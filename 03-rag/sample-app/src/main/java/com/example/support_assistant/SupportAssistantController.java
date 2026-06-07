package com.example.support_assistant;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SupportAssistantController {

    private final SupportAssistantService service;

    SupportAssistantController(SupportAssistantService service) {
        this.service = service;
    }

    // curl -G "http://localhost:8080/api/1.0/chat" --data-urlencode "query=Tell me about Spring AI"
    @GetMapping(path = "/api/{version}/chat")
    SupportResponse chat(@RequestParam String query) {
        return service.generateResponse(query);
    }
}
