package com.example.support_assistant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class ChatResponseTest {

    @Autowired
    private ChatClient chatClient;

    @Test
    void responseIsNotEmpty() {
        String response = chatClient.prompt()
                .user("What is Spring Boot?")
                .call()
                .content();

        assertThat(response)
                .isNotNull()
                .isNotBlank();
    }

    @Test
    void responseContainsRelevantConcepts() {
        String response = chatClient.prompt()
                .user("What is Spring Boot?")
                .call()
                .content();

        assertThat(response.toLowerCase())
                .satisfiesAnyOf(
                        r -> assertThat(r).contains("framework"),
                        r -> assertThat(r).contains("java"),
                        r -> assertThat(r).contains("application"),
                        r -> assertThat(r).contains("spring")
                );
    }
}
