package com.example.support_assistant;

import com.example.support_assistant.tools.AskUserQuestionHandler;
import org.springaicommunity.agent.tools.AskUserQuestionTool;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
class SupportAssistantController {

    private static final String CONVERSATION_ID_HEADER = "X-Conversation-Id";

    private final SupportAssistantService service;

    private final Map<String, AskUserQuestionHandler> questionHandlers = new ConcurrentHashMap<>();

    SupportAssistantController(SupportAssistantService service) {
        this.service = service;
    }

    // curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Tell me about Spring AI" \
    //   --data-urlencode "humanInTheLoop=true" -H "X-Conversation-Id: 1234"
    @GetMapping(path = "/api/v{version}/chat")
    ResponseEntity<SupportResponse> chat(@RequestParam String query,
                                         @RequestParam(defaultValue = "false") boolean humanInTheLoop,
                                         @RequestHeader(value = CONVERSATION_ID_HEADER, required = false) String conversationId) {
        var id = (conversationId != null) ? conversationId : UUID.randomUUID().toString();

        var questionHandler = (humanInTheLoop && conversationId != null)
                ? questionHandlers.computeIfAbsent(conversationId, key -> new AskUserQuestionHandler())
                : null;
        var response = service.generateResponse(query, id, questionHandler);
        return ResponseEntity.ok().header(CONVERSATION_ID_HEADER, id).body(response);
    }

    // curl "http://localhost:8080/api/v1/chat/questions" -H "X-Conversation-Id: 1234"
    @GetMapping(path = "/api/v{version}/chat/questions")
    List<AskUserQuestionTool.Question> questions(@RequestHeader(CONVERSATION_ID_HEADER) String conversationId) {
        return handlerFor(conversationId).pendingQuestions();
    }

    // curl "http://localhost:8080/api/v1/chat/answers" -H "X-Conversation-Id: 1234" -H "Content-Type: application/json" \
    //   -d '{"Which Spring Boot version do you use?": "Spring Boot 4"}'
    @PostMapping(path = "/api/v{version}/chat/answers")
    void answers(@RequestHeader(CONVERSATION_ID_HEADER) String conversationId,
                 @RequestBody Map<String, String> answers) {
        handlerFor(conversationId).answer(answers);
    }

    private AskUserQuestionHandler handlerFor(String conversationId) {
        var questionHandler = questionHandlers.get(conversationId);
        if (questionHandler == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No human in the loop conversation with id " + conversationId);
        }
        return questionHandler;
    }
}
