package com.example.support_assistant;

import org.jspecify.annotations.Nullable;
import com.example.support_assistant.advisor.AskUserQuestionToolAdvisor;
import org.springaicommunity.agent.tools.AskUserQuestionTool.QuestionHandler;
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

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final SupportTicketService supportTicketService;

    @Value("classpath:/prompts/rag-prompt.st")
    private Resource ragPromptResource;

    SupportAssistantService(ChatClient chatClient, VectorStore vectorStore, SupportTicketService supportTicketService) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.supportTicketService = supportTicketService;
    }

    SupportResponse generateResponse(String query, String conversationId,
                                     @Nullable QuestionHandler questionHandler) {
        var ragSearchRequest = SearchRequest.builder().topK(4).similarityThreshold(0.4).build();

        var promptTemplate = PromptTemplate.builder().resource(ragPromptResource).build();
        var ragAdvisor = QuestionAnswerAdvisor.builder(vectorStore).searchRequest(ragSearchRequest)
                .promptTemplate(promptTemplate).build();

        var request = chatClient.prompt()
            .user(u -> u
                    .text("Answer the following question with a short, well-structured explanation: {question}")
                    .param("question", query)
            )
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
            .advisors(ragAdvisor)
            .tools(supportTicketService);

        if (questionHandler != null) {
            request.advisors(AskUserQuestionToolAdvisor.builder(questionHandler).build());
        }

        return request.call().entity(SupportResponse.class);
    }
}
