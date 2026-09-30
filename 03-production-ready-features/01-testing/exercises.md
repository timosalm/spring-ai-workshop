# Testing Lab

In this lab you write automated tests for the support assistant.

Code that calls an LLM cannot be tested like normal code. The model rewords answers, picks different synonyms, and sometimes refuses to answer at all. Two patterns get you most of the way there.

1. **Semantic assertions** are cheap and mostly deterministic checks. You assert on the meaning of the answer, so you look for the key concepts and not for the exact text.
2. **LLM as judge** handles the harder questions, such as whether an answer is really backed by the retrieved context. A second model call grades the output.

Both patterns come built into Spring AI, and you only wire them into a normal `@SpringBootTest`.

Read [Testing AI Applications](testing.md) first.

## Before You Start

Your starting point is the [`sample-app/`](sample-app/) of this folder. It is the support assistant from the previous labs with RAG and the ticket tools. All paths in this lab are relative to `sample-app/`, and all commands run inside it.

These tests make real model calls, so export your key in the terminal where you run them.

```bash
export OPENAI_API_KEY=sk-...
```

The tests start the whole application on port 8080. Make sure the application from an earlier lab is **not** running, otherwise the tests fail because the port is already in use.

## 1. Test Dependencies

There is nothing to add. The project already ships `spring-boot-starter-webmvc-test`, which pulls in JUnit 5, AssertJ, and `@SpringBootTest`. It also ships `spring-boot-starter-actuator-test`. Both were part of the project from the start. The `RelevancyEvaluator` that you use later lives in `spring-ai-client-chat`, which the OpenAI chat starter already brings in for you. The other provider starters do the same.

## 2. A Basic Response Quality Test

Start with the simplest possible check. Did the model respond at all, and does the response mention the things you would expect for the question?

Asserting on *exact strings* is brittle, because a paraphrase turns the test red even when the answer is fine. Assert on **concepts** instead.

Create `src/test/java/com/example/support_assistant/ChatResponseTest.java` with the following code. It contains two tests against the `ChatClient` bean of the application.

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

Two patterns are worth noticing.

- The first test uses **`isNotNull().isNotBlank()`**. It only checks that something came back, which catches failures of the API and empty responses.
- The second test uses **`satisfiesAnyOf(...)`** and passes as long as *at least one* of the expected concepts appears. Any good answer to "Tell me about Spring AI" mentions at least one of spring, java, ai, or abstraction, no matter how it is worded.

Run it.

```bash
./mvnw test -Dtest=ChatResponseTest
```

Wait for `BUILD SUCCESS`. Both tests should pass.

## 3. RAG Relevancy Evaluation

Here is the interesting question. When the assistant answers with retrieved context, **is that answer really backed by the retrieved chunks**? Or did the model make up something that is only loosely related?

You cannot write a regular expression for that. The `RelevancyEvaluator` of Spring AI is an LLM as judge. It takes the question, the retrieved documents, and the response, and it asks another model call whether the response is really grounded in those documents. It returns pass or fail.

Create `src/test/java/com/example/support_assistant/RagEvaluationTest.java` with the following code.

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

Here is what happens, step by step.

1. **Run the RAG query.** The query runs with the same `ChatClient` and `QuestionAnswerAdvisor` that your real service uses. It asks for `.chatResponse()` and not `.content()`, because you need the metadata.
2. **Build an `EvaluationRequest`.** The request is built from the question, the retrieved context, and the generated answer. The retrieved documents come out of the response metadata under `QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS`. These are the chunks that the advisor fetched from the vector store before it asked the model.
3. **Ask the `RelevancyEvaluator` to judge.** The evaluator is just another `ChatClient` call under the hood. It uses the `ChatClient.Builder` you injected, with a built-in prompt that asks whether the answer is relevant for the given context.
4. **Assert the verdict.** The test passes when the judge says the answer is grounded in the retrieved chunks.

The `KnowledgeBaseIndexer` indexes the Markdown knowledge base into the in memory vector store on every application start, including the context of the `@SpringBootTest`, so the test always has fresh data. There is no extra setup.

Here the answer and the judgment use two different models. The answer comes from the default model of your injected `ChatClient`, and the judge uses `gpt-5.4-nano`, which you set with `.defaultOptions(...)` on the builder before you pass it to the `RelevancyEvaluator`. Using a separate model for the judge is a good practice, and a smaller and cheaper model is often good enough to grade a response. You can even send the judge call to a completely different AI provider, which Spring AI supports.

Run it.

```bash
./mvnw test -Dtest=RagEvaluationTest
```

## 4. Run the Whole Suite

```bash
./mvnw test
```

You see both test classes run.

## Recap

You now have automated tests for output that is not deterministic. Semantic assertions cover the basic quality, and an LLM as judge evaluator checks that RAG answers are grounded. The [`sample-app/`](../02-observability/sample-app/) of the observability lab contains the result of this lab. Next you make the running system observable.
