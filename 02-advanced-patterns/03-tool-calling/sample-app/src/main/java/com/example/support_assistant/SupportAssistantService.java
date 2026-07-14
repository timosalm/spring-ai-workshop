package com.example.support_assistant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
class SupportAssistantService {

    private static final Logger log = LoggerFactory.getLogger(SupportAssistantService.class);
    
    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    @Value("classpath:/prompts/rag-prompt.st")
    private Resource ragPromptResource;

    SupportAssistantService(ChatClient chatClient, VectorStore vectorStore) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
    }

    SupportResponse generateResponse(String query, String conversationId) {
        var ragSearchRequest = SearchRequest.builder().topK(4).similarityThreshold(0.4).build();

        var promptTemplate = PromptTemplate.builder().resource(ragPromptResource).build();
        var ragAdvisor = QuestionAnswerAdvisor.builder(vectorStore).searchRequest(ragSearchRequest)
                .promptTemplate(promptTemplate).build();
    
        return chatClient.prompt()
            .user(u -> u
                    .text("Answer the following question with a short, well-structured explanation: {question}")
                    .param("question", query)
            )
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
            .advisors(ragAdvisor)
            .call()
            .entity(SupportResponse.class);
    }
}
