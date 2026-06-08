package com.example.support_assistant;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.toolsearch.ToolSearchToolCallingAdvisor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.toolsearch.ToolIndex;
import org.springframework.ai.tool.toolsearch.index.vectorstore.VectorToolIndex;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class SupportAssistantConfiguration {

    @Bean
    ToolIndex toolIndex(VectorStore vectorStore) {
        return new VectorToolIndex(vectorStore);
    }

    @Bean
    ChatClient chatClient(ChatClient.Builder builder, ToolCallbackProvider tools, ToolIndex toolIndex) {
        var toolSearchAdvisor = ToolSearchToolCallingAdvisor.builder()
                .toolIndex(toolIndex).maxResults(5).build();

        return builder.defaultSystem("You are a Spring and AI expert.").defaultTools(tools)
                .defaultAdvisors(toolSearchAdvisor).build();
    }

    @ConditionalOnMissingBean(VectorStore.class)
    @Bean
    VectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
