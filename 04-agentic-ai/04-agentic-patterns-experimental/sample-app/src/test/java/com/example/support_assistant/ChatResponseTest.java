package com.example.support_assistant;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ai.chat.client.ChatClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class ChatResponseTest {

    @Autowired
    private ChatClient chatClient;

    @Test
    void responseIsNotEmpty() {
        String response = chatClient.prompt()
                .user("Tell me about Spring AI")
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, UUID.randomUUID().toString()))
                .call()
                .content();

        assertThat(response)
                .isNotNull()
                .isNotBlank();
    }

    @Test
    void responseContainsRelevantConcepts() {
        String response = chatClient.prompt()
                .user("Tell me about Spring AI")
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, UUID.randomUUID().toString()))
                .call()
                .content();

        assertThat(response.toLowerCase())
                .satisfiesAnyOf(
                        r -> assertThat(r).contains("spring"),
                        r -> assertThat(r).contains("java"),
                        r -> assertThat(r).contains("ai"),
                        r -> assertThat(r).contains("abstraction")
                );
    }
}