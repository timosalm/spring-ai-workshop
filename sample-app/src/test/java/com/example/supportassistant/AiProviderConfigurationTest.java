package com.example.supportassistant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to validate AI provider configuration.
 * Runs with the active profile (mock or openai) to verify connectivity.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class AiProviderConfigurationTest {

    @Autowired
    private ChatClient chatClient;

    @Value("${spring.profiles.active}")
    private String activeProfile;

    @Test
    void aiProviderIsConfiguredAndResponding() {
        // Send a simple test prompt
        String response = chatClient.prompt()
                .user("What is Tanzu Spring?")
                .call()
                .content();

        // Verify we got a response
        assertThat(response)
                .isNotNull()
                .isNotBlank();

        System.out.println("=================================================");
        System.out.println("AI Provider Configuration Test - SUCCESS");
        System.out.println("Active Profile: " + activeProfile);
        System.out.println("Response received: " + response.substring(0, Math.min(100, response.length())) + "...");
        System.out.println("=================================================");
    }

    @Test
    void chatClientBeanIsAvailable() {
        assertThat(chatClient).isNotNull();
        System.out.println("ChatClient bean is properly configured.");
    }
}
