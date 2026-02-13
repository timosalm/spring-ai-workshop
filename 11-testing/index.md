---
title: Testing (Optional)
---

{{< note >}}
This is an **optional advanced module**. You can skip to the workshop summary if needed.
{{< /note >}}

In this module, you'll learn strategies for testing AI-powered applications and explore Spring AI's built-in evaluation capabilities.

## Learning Objectives

- Understand the unique testing challenges of LLM-based applications
- Know when to use standard tests vs. AI-specific evaluation
- Use Spring AI's `Evaluator` framework for response quality assessment
- Write integration tests for your application

## The Testing Challenge

Traditional testing is **deterministic** -- same input, same output. AI applications are often **non-deterministic** -- same input, different outputs!

```
Test 1: "What is 2+2?" → "4"
Test 2: "What is 2+2?" → "The answer is 4"
Test 3: "What is 2+2?" → "2+2 equals 4"
```

All correct, but different strings! This means `assertEquals("4", response)` would fail on two of three runs.

Additionally, **different models behave differently**. A response that works with GPT-4o may differ significantly with Claude or Gemini. Even the same model family with different parameter sizes (e.g., Llama 8B vs. 70B) can produce varying quality and formatting. Your tests need to account for this variability.

## What to Test and How

Not everything in an AI application requires special testing approaches. Most of your business logic -- controllers, services, repositories -- is tested as usual with standard Spring Boot testing.

The AI-specific parts require different strategies:

| Layer | What to Test | Testing Approach |
|-------|-------------|-----------------|
| Business logic | Controllers, services, entities | Standard unit/integration tests |
| Structured output | Schema compliance, valid values | Validate structure and types |
| RAG retrieval | Document relevance | Vector store search assertions |
| Tool calling | Tool invocation logic | Mock tools, verify calls |
| Free-form responses | Response quality, relevance | Spring AI Evaluators |
| Full integration | End-to-end with real model | Integration tests (CI/CD) |

## Testing Business Logic

Your business code around the AI integration is tested as usual. Let's add a test for our application context and ChatClient configuration.

First, stop the running application if it's still active:

```terminal:interrupt
session: 2
```

Let's verify the existing test works:

```terminal:execute
command: cd ~/sample-app && ./mvnw test -Dtest=AiProviderConfigurationTest 2>&1 | tail -20
session: 2
```

This test runs with the mock profile and validates that the `ChatClient` bean is configured and responding.

## Spring AI Evaluation Framework

For testing free-form AI responses, Spring AI provides the **Evaluator** framework. Instead of comparing exact strings, evaluators assess response **quality** and **relevance** -- often using an LLM as the judge.

### The Evaluator Interface

```java
@FunctionalInterface
public interface Evaluator {
    EvaluationResponse evaluate(EvaluationRequest evaluationRequest);
}
```

An `EvaluationRequest` contains:
- `userText` -- the original user question
- `dataList` -- contextual data (e.g., documents retrieved by RAG)
- `responseContent` -- the AI model's response

The `EvaluationResponse` provides a simple `.isPass()` result.

### Built-in Evaluators

Spring AI ships with two evaluators:

| Evaluator | Purpose | Best For |
|-----------|---------|----------|
| `RelevancyEvaluator` | Checks if the response is relevant to the question given the context | RAG validation |
| `FactCheckingEvaluator` | Checks factual accuracy against provided context | Hallucination detection |

## Create a RAG Evaluation Test

Let's create a test that evaluates whether our RAG-powered knowledge service returns relevant answers. This test uses the `RelevancyEvaluator` to let an LLM judge whether the response is in line with the retrieved context.

```editor:append-lines-to-file
file: ~/sample-app/src/test/java/com/example/supportassistant/RagEvaluationTest.java
description: Create RAG evaluation test
text: |
  package com.example.supportassistant;

  import org.junit.jupiter.api.Test;
  import org.springframework.beans.factory.annotation.Autowired;
  import org.springframework.boot.test.context.SpringBootTest;
  import org.springframework.ai.chat.client.ChatClient;
  import org.springframework.ai.chat.model.ChatResponse;
  import org.springframework.ai.evaluation.EvaluationRequest;
  import org.springframework.ai.evaluation.EvaluationResponse;
  import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
  import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
  import org.springframework.ai.vectorstore.VectorStore;

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
          String question = "What are the key features of Tanzu Spring?";

          // Get a RAG-powered response
          ChatResponse chatResponse = chatClient.prompt()
                  .user(question)
                  .advisors(QuestionAnswerAdvisor.builder(vectorStore).build())
                  .call()
                  .chatResponse();

          // Build an evaluation request with the question, context, and response
          EvaluationRequest evaluationRequest = new EvaluationRequest(
                  question,
                  chatResponse.getMetadata().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS),
                  chatResponse.getResult().getOutput().getText()
          );

          // Use the RelevancyEvaluator (LLM-as-judge) to assess relevance
          RelevancyEvaluator evaluator = new RelevancyEvaluator(chatClientBuilder);
          EvaluationResponse evaluationResponse = evaluator.evaluate(evaluationRequest);

          assertThat(evaluationResponse.isPass())
                  .as("RAG response should be relevant to the retrieved context")
                  .isTrue();
      }
  }
```

The `RelevancyEvaluator` sends the question, context, and response to an LLM with a prompt like: *"Is this response in line with the context information? Answer YES or NO."*

## Create a Simple Response Quality Test

Not every test needs the full evaluation framework. For basic checks, you can validate response characteristics directly:

```editor:append-lines-to-file
file: ~/sample-app/src/test/java/com/example/supportassistant/ChatResponseTest.java
description: Create response quality test
text: |
  package com.example.supportassistant;

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

          // Check for key concepts rather than exact wording
          assertThat(response.toLowerCase())
                  .satisfiesAnyOf(
                      r -> assertThat(r).contains("framework"),
                      r -> assertThat(r).contains("java"),
                      r -> assertThat(r).contains("application"),
                      r -> assertThat(r).contains("spring")
                  );
      }
  }
```

## Run the Tests

Run all tests with the mock profile:

```terminal:execute
command: cd ~/sample-app && ./mvnw test 2>&1 | tail -30
session: 2
```

The tests run against the mock AI provider, giving you fast and deterministic results. This is ideal for development and CI pipelines.

## Integration Testing Considerations

### Testing with Local Models (Testcontainers)

For integration tests that need a real model, you can use **Testcontainers** to run local models like Ollama in a container:

```java
@Testcontainers
@SpringBootTest
class OllamaIntegrationTest {

    @Container
    static OllamaContainer ollama = new OllamaContainer("ollama/ollama:latest");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.ollama.base-url", ollama::getEndpoint);
        registry.add("spring.ai.ollama.chat.model", () -> "llama3.2:1b");
    }
}
```

This approach is useful for testing with real model behavior without depending on external API keys. Keep in mind that smaller local models may produce different results than production models.

### Testing with Production Models

Integration tests with the actual production model (e.g., GPT-4o, Claude) are typically run as part of a CI/CD pipeline rather than local development. These tests:

- Require API keys configured as environment variables
- Are slower and more expensive to run
- May produce non-deterministic results
- Are best run on a schedule or before releases, not on every commit

### Model Variability

Always be aware that **different models behave differently**:
- Response formatting varies (markdown, plain text, code blocks)
- Level of detail and verbosity differs
- Tool calling behavior and argument formatting can change
- Smaller models may miss nuances that larger models catch

Design your tests to be resilient to these differences by checking semantics rather than exact output.

## Summary

| What to Test | How to Test | When to Run |
|--------------|-------------|-------------|
| Business logic | Standard unit tests | Every commit |
| Structured output | Validate schema and types | Every commit |
| RAG retrieval | Vector store assertions | Every commit |
| Response quality | Spring AI `RelevancyEvaluator` | Every commit (with mock) |
| Hallucination detection | Spring AI `FactCheckingEvaluator` | CI/CD pipeline |
| Full integration | Real model + Testcontainers | Before release |

**Key Takeaways:**
- Business logic around AI is tested as usual with standard Spring Boot testing
- Spring AI's `Evaluator` framework provides LLM-as-judge evaluation for response quality
- Use mock providers for fast, deterministic tests in development
- Use Testcontainers with local models for integration tests without API key dependencies
- Reserve production model tests for CI/CD pipelines
- Design tests for semantic correctness, not exact string matches
- Account for model variability -- different models and parameter sizes produce different results
