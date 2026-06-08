# Spring AI Support Assistant — Testing Walkthrough

Continues from `INSTRUCTIONS-3.md`. Your starting point is the full Support Assistant: RAG over a Markdown knowledge base, structured output, and tool calls to a ticket repository.

LLM-backed code can't be tested like normal code — the model rewords answers, picks synonyms, and sometimes refuses outright. Two patterns get us most of the way:

1. **Semantic assertions** — for cheap, deterministic-ish checks, assert on meaning (key concepts, not exact text).
2. **LLM-as-judge** — for harder questions like "is this answer actually relevant to the retrieved context?", use a separate model call to grade the output.

Both come out of the box in Spring AI; you just wire them into a regular `@SpringBootTest`.

## 1. Test dependencies

The base project already ships `spring-boot-starter-webmvc-test` (pulls JUnit 5 + AssertJ + `@SpringBootTest`) and `spring-boot-starter-actuator-test`. Nothing extra is needed — `RelevancyEvaluator` lives in `spring-ai-client-chat`, which the chat starter you chose in INSTRUCTIONS-1 already brings in transitively.

So: no new dependencies.

## 2. Basic Response Quality Test

Start with the simplest possible check: did the model respond at all, and does the response mention the things you'd expect for the question? Asserting on *exact strings* is brittle — a paraphrase will flip the test red even when the answer is fine. Assert on **concepts** instead.

Create `src/test/java/com/example/support_assistant/ChatResponseTest.java`:

```java
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
```

Two patterns to notice:

- **`isNotNull().isNotBlank()`** — the smoke test. Catches outright API failures and empty responses.
- **`satisfiesAnyOf(...)`** — the semantic assertion. The test passes as long as *at least one* of the expected concepts appears. Any decent answer to "What is Spring Boot?" mentions at least one of framework / java / application / spring, regardless of exact wording.

Run it:

```bash
./mvnw test -Dtest=ChatResponseTest
```

### Notes per provider

- **OpenAI / Anthropic / Bedrock** — each test hits the real API and burns tokens. Cheap, but it's still cost. Pin the cheapest model in your test profile (`spring.ai.<provider>.chat.model=...`) if you want to keep bills predictable.
- **Ollama** — local, no cost, but slow. Tests are perfectly happy here once `ollama pull` has fetched the model.

### Notes per database

These tests don't touch the DB or the vector store, so the same code passes regardless of whether you're on H2/`SimpleVectorStore` (default) or PostgreSQL/pgvector (postgres profile).

## 3. RAG Relevancy Evaluation Test

The interesting question: when the assistant answers using retrieved context, **is that answer actually backed by the retrieved chunks**? Or did the model hallucinate something only loosely related?

You can't write a regex for that. Spring AI's `RelevancyEvaluator` is an "LLM-as-judge": it takes the question, the retrieved documents, and the response, and asks another model call whether the response is genuinely grounded in those documents. It returns pass/fail.

Create `src/test/java/com/example/support_assistant/RagEvaluationTest.java`:

```java
package com.example.support_assistant;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

        var chatResponse = chatClient.prompt()
                .user(question)
                .advisors(QuestionAnswerAdvisor.builder(vectorStore).build())
                .call()
                .chatResponse();

        var evaluationRequest = new EvaluationRequest(
                question,
                chatResponse.getMetadata().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS),
                chatResponse.getResult().getOutput().getText()
        );

        var evaluator = new RelevancyEvaluator(chatClientBuilder);
        var evaluationResponse = evaluator.evaluate(evaluationRequest);

        assertThat(evaluationResponse.isPass())
                .as("RAG response should be relevant to the retrieved context")
                .isTrue();
    }
}
```

What's happening, step by step:

1. **Run the RAG query** — same `ChatClient` + `QuestionAnswerAdvisor` your real service uses. Ask for `.chatResponse()` (not `.content()`) because we need the metadata.
2. **Pull the retrieved documents** out of the response metadata under `QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS`. These are the chunks the advisor fetched from the vector store before asking the model.
3. **Build an `EvaluationRequest`** — question, retrieved context, generated answer.
4. **Ask the `RelevancyEvaluator`** to judge. It's just another `ChatClient` call under the hood, using the same `ChatClient.Builder` you injected, with a built-in prompt that asks "given this context, is this answer relevant?"
5. **Assert the verdict**.

Run it:

```bash
./mvnw test -Dtest=RagEvaluationTest
```

### Notes per provider

The judge runs on whatever provider your `ChatClient.Builder` is wired to — the same one that generated the answer. That's usually fine: a question asked of `gpt-5.4-mini` is judged by `gpt-5.4-mini`. If you want an independent judge (best practice for production-grade evals), build a second `ChatClient` from a different provider's `ChatModel` and pass *that* builder into `RelevancyEvaluator`.

- **Bedrock attendees**: ensure your IAM role can invoke both the chat model and the judge (same role if using one model, two if you swap).
- **Ollama**: the judge call is local, so cost is zero — but it's two LLM calls per test, so plan for the runtime to ~double.

### Notes per database / vector store

- **In-memory `SimpleVectorStore` (default)** — the `KnowledgeBaseIndexer` from INSTRUCTIONS-2 reindexes on every app start (including the test's `@SpringBootTest` context), so the test has fresh data. No setup.
- **pgvector / PostgreSQL** — same thing, with one wrinkle: `spring.ai.vectorstore.pgvector.remove-existing-vector-store-table=true` (set in INSTRUCTIONS-2) drops and recreates the table on context startup, so each test run starts clean. If you've turned that flag off for production, your test will keep accumulating chunks — use `@DirtiesContext` or a dedicated test profile that re-enables the flag.

## 4. Run the suite

```bash
./mvnw test
```

You'll see both classes execute. The relevancy test is materially slower (it's two LLM calls instead of one), so it's worth tagging if you want to skip it in tight loops — e.g., put `@Tag("eval")` on `RagEvaluationTest` and configure Surefire to exclude that tag by default.

---

## Recap

| Step | What changed | Key API |
|------|--------------|---------|
| 1 | Test deps already present | `spring-boot-starter-webmvc-test` |
| 2 | Semantic smoke test | `satisfiesAnyOf(...)` over concepts |
| 3 | LLM-as-judge evaluation | `RelevancyEvaluator`, `EvaluationRequest`, `QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS` |