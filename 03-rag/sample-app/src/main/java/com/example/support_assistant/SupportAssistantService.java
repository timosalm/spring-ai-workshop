package com.example.support_assistant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
class SupportAssistantService {

    private static final Logger log = LoggerFactory.getLogger(SupportAssistantService.class);
    
    private final ChatClient chatClient;

    SupportAssistantService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    SupportResponse generateResponse(String query) {
        return chatClient.prompt()
                .user(u -> u
                        .text("Answer the following question with a short, well-structured explanation: {question}")
                        .param("question", query)
                )
                .call()
                .entity(SupportResponse.class);
    }

}
