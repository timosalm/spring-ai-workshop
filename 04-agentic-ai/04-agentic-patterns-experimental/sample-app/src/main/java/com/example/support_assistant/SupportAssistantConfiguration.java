package com.example.support_assistant;

import org.springaicommunity.mcp.security.client.sync.config.McpClientOAuth2Configurer;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.core.Ordered;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@EnableWebSecurity
@Configuration
public class SupportAssistantConfiguration {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                 @Value("classpath:/prompts/system-prompt.st") Resource systemPrompt,
                                 ChatMemory chatMemory,
                                 ToolCallbackProvider tools) {
        return builder
                .defaultSystem(systemPrompt)
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(Ordered.LOWEST_PRECEDENCE),
                        MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(tools)
                .build();
    }

    @ConditionalOnMissingBean(VectorStore.class)
    @Bean
    VectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @Bean
    @Profile("mcp-security")
    SecurityFilterChain mcpSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .with(McpClientOAuth2Configurer.mcpClientOAuth2(), Customizer.withDefaults())
                .csrf(CsrfConfigurer::disable)
                .build();
    }

    @Bean
    @Profile("!mcp-security")
    SecurityFilterChain permitAllFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(CsrfConfigurer::disable)
                .build();
    }
}