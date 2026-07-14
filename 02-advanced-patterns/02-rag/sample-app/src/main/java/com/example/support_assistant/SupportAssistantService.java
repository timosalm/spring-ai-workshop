package com.example.support_assistant;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

@Service
class SupportAssistantService {

    private final ChatClient chatClient;

    SupportAssistantService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    SupportResponse generateResponse(String query, String conversationId) {
        return chatClient.prompt()
                .user(u -> u
                        .text("Answer the following question with a short, well-structured explanation: {question}")
                        .param("question", query))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .entity(SupportResponse.class);
    }
}
