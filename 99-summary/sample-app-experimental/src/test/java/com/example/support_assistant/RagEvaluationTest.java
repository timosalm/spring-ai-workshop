package com.example.support_assistant;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class RagEvaluationTest {

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Autowired
    private VectorStore vectorStore;

    @Test
    void ragResponseIsRelevantToRetrievedContext() {
        var question = "What are the key features of VMware Tanzu Spring?";

        var chatResponse = chatClient.prompt()
                .user(question)
                .advisors(QuestionAnswerAdvisor.builder(vectorStore).build())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, UUID.randomUUID().toString()))
                .call()
                .chatResponse();

        var evaluationRequest = new EvaluationRequest(
                question,
                chatResponse.getMetadata().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS),
                chatResponse.getResult().getOutput().getText()
        );
        var evaluatorChatClientBuilder = chatClientBuilder.defaultOptions(ChatOptions.builder().model("gpt-5.4-nano"));
        var evaluator = new RelevancyEvaluator(evaluatorChatClientBuilder);
        var evaluationResponse = evaluator.evaluate(evaluationRequest);

        assertThat(evaluationResponse.isPass())
                .as("RAG response should be relevant to the retrieved context")
                .isTrue();
    }
}