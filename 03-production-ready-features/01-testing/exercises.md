# Testing — Hands-on Exercises

Your starting point is the [`sample-app/`](sample-app/), the full assistant from the advanced-patterns module: RAG plus ticket tools.

Code that calls an LLM cannot be tested like normal code. The model rewords answers, picks different synonyms, and sometimes refuses. Two patterns get you most of the way there, and both are built into Spring AI:

1. **Semantic assertions** — cheap, mostly deterministic checks on the *meaning* of an answer (look for key concepts, not exact text).
2. **LLM as judge** — for questions like "is this answer actually backed by the retrieved context?", use a second model call to grade the output.

> These tests make real model calls, so set `OPENAI_API_KEY` before running them.

## 1. Dependencies

There is nothing to add. The project already ships `spring-boot-starter-webmvc-test` (JUnit 5, AssertJ, `@SpringBootTest`) and `spring-boot-starter-actuator-test`. The `RelevancyEvaluator` you'll use lives in `spring-ai-client-chat`, which the OpenAI chat starter already brings in.

## 2. A basic response-quality test

Start with the simplest check: did the model respond at all, and does the response mention what you'd expect? Asserting on exact strings is brittle — a paraphrase flips the test red even when the answer is fine. Assert on **concepts** instead.

Create `src/test/java/com/example/support_assistant/ChatResponseTest.java`:

```java
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
```

- **`isNotNull().isNotBlank()`** catches API failures and empty responses.
- **`satisfiesAnyOf(...)`** passes as long as *at least one* expected concept appears — any decent answer mentions spring/java/ai/abstraction regardless of wording.

Run it:

```bash
cd sample-app
./mvnw test -Dtest=ChatResponseTest
```

Both tests should pass.

## 3. RAG relevancy evaluation (LLM as judge)

When the assistant answers from retrieved context, is that answer actually backed by the chunks, or did the model hallucinate something loosely related? You can't regex for that. Spring AI's `RelevancyEvaluator` takes the question, the retrieved documents, and the response, and asks another model call whether the response is grounded. It returns pass or fail.

Create `src/test/java/com/example/support_assistant/RagEvaluationTest.java`:

```java
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
```

Step by step:

1. **Run the RAG query** with the same `ChatClient` and `QuestionAnswerAdvisor` your service uses. Ask for `.chatResponse()`, not `.content()`, because you need the metadata.
2. **Pull the retrieved documents** from the metadata under `QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS`.
3. **Build an `EvaluationRequest`** from the question, the retrieved context, and the answer.
4. **Ask the `RelevancyEvaluator`** to judge — it's another `ChatClient` call with a built-in "is this answer grounded in this context?" prompt. Here the judge uses the cheaper `gpt-5.4-nano`, set via `.defaultOptions(...)`. Using a separate (smaller) model, or even a different provider, for the judge is good practice.
5. **Assert the verdict**.

The `KnowledgeBaseIndexer` reindexes on every application start, including the test context, so the test always has fresh data.

Run it:

```bash
./mvnw test -Dtest=RagEvaluationTest
```

## 4. Run the whole suite

```bash
./mvnw test
```

Both test classes execute.

## Recap

You now have automated tests over non-deterministic model output: semantic assertions for basic quality, and an LLM-as-judge evaluator for RAG groundedness. Next you make the running system observable.
