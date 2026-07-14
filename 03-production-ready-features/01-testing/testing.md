Most of your tests count on one thing. The same input gives the same output. You call the method, you assert the result, and you are done. A language model does not work like that. Call it twice with the same prompt and you get two different answers. The meaning can be the same, but the words, the order, and the length are not. The output is *probabilistic*, which means it changes each time. It is not deterministic.

This means your normal assertions no longer work. `assertEquals("...", answer)` will always fail, because you are trying to fix the exact words that the model will not repeat. But you do not lose the ability to test. You only need to split the work into two kinds of tests for two kinds of code.

The split is simple. Most of your application code, including the prompt construction, advisor wiring, and tool methods, is deterministic and always behaves the same way. The other part is the model, which is probabilistic.

## Two Things to Test

**The deterministic shell** Everything *around* the model is normal Java, so you test it in the normal way. Does your tool method open a ticket with the right summary? Does your service build the correct `SearchRequest`? Does structured output mapping turn the model's JSON into the right `SupportResponse` record? None of this needs a real model. Spring AI is built from ordinary Spring beans, so you inject a *stubbed or mocked* `ChatModel` that returns a fixed response and assert your surrounding logic with plain JUnit and Mockito. These tests are fast, deterministic, and free. Most of your test suite should live here, and it is just standard Spring testing applied to AI code.

**The probabilistic output** At some point you still need to know whether the model's *actual answers* are any good. Are they relevant, grounded in your documents, and free of made up facts? A mock cannot tell you that. You have to run the real model and judge what comes back. Since you cannot assert exact text, you assert *properties* of the answer instead. Is it on topic? Is it supported by the retrieved context? Does it avoid inventing facts? This part is new, and Spring AI supports it directly through **evaluators**.

## Evaluators, Using a Model to Judge a Model

How do you check in an automated test whether a free form answer is "relevant" or "factually grounded"? Understanding natural language is the exact problem language models are built to solve, so Spring AI points the model at itself. An **evaluator** uses an LLM to assess another LLM's output. The pattern is usually called *LLM as a judge*. The model under test produces an answer, and a second call asks a model to score that answer against the question and context.

The contract is a single interface.

```java
@FunctionalInterface
public interface Evaluator {
    EvaluationResponse evaluate(EvaluationRequest evaluationRequest);
}
```

You pass an **`EvaluationRequest`** that bundles the three inputs any judgment needs. You get back an **`EvaluationResponse`** whose `isPass()` tells you whether the answer met the bar.

```java
public EvaluationRequest(String userText, List<Content> dataList, String responseContent)
```

- **`userText`** is what the user asked.
- **`dataList`** is the context the answer was based on, usually the documents your RAG step retrieved.
- **`responseContent`** is the answer the model produced.

Internally the evaluator renders these into a fixed prompt ("here is a query, a response, and some context, is the response in line with the context? Answer YES or NO") and sends it to a judging `ChatClient`. It parses the natural language verdict back into a simple pass or fail. Spring AI ships two evaluators for the failure modes you hit most.

## `RelevancyEvaluator`, Is the Answer On Point?

The **`RelevancyEvaluator`** checks whether a response answers the user's question given the provided context. It is the natural way to validate a RAG flow end to end. You ran retrieval and fed the documents to the model, so now you verify that the answer used them to address the question instead of drifting off. You build it with a `ChatClient.Builder`, which supplies the judging model, and pass it the request.

```java
RelevancyEvaluator evaluator = new RelevancyEvaluator(ChatClient.builder(chatModel));

EvaluationRequest request = new EvaluationRequest(question, retrievedDocuments, answer);
EvaluationResponse response = evaluator.evaluate(request);

assertThat(response.isPass()).isTrue();
```

In a real RAG test, `retrievedDocuments` come straight from the context the advisor surfaced (`chatResponse.getMetadata().get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT)`), so you evaluate the same documents the model saw. If the default judging prompt does not fit your domain, override it with `.promptTemplate(...)`, as long as it keeps the `{query}`, `{response}`, and `{context}` placeholders the evaluator fills in.

## `FactCheckingEvaluator`, Did the Model Make Things Up?

Relevance is not the same as truth. An answer can be on topic and still contain a made up detail. The **`FactCheckingEvaluator`** targets hallucination directly. It checks whether the claims in the response are actually *supported by* the supplied context. It treats the context as a document and the answer as a claim, then asks the judging model whether the document backs the claim.

```java
FactCheckingEvaluator evaluator = new FactCheckingEvaluator(ChatClient.builder(chatModel));

EvaluationRequest request = new EvaluationRequest(context, Collections.emptyList(), claim);
EvaluationResponse response = evaluator.evaluate(request);

assertThat(response.isPass()).isFalse(); // a claim the context does not support should fail
```

One useful property here is that the judge does not have to be your biggest model. Fact checking is a narrow task, so small purpose built models (run locally via Ollama, for example) handle it well, cheaply, and fast. That matters when these checks run inside your test suite.

Both evaluators are the building blocks of a **basic response quality test**. You ask your assistant a known question, capture the answer and the context it was given, and assert that the answer is both relevant and factually grounded. That is the kind of test you will write in the exercise.

## Integration Testing With Real Infrastructure

Evaluating real output means running the model, and usually the vector store too. You do not want tests calling a paid cloud API or depending on services a teammate may not have running. This is where **Testcontainers** comes in. It starts real services in Docker for the lifetime of a test, then tears them down. Spring AI integrates with it so the containers wire themselves into your application automatically.

Add the `spring-ai-spring-boot-testcontainers` dependency, declare the containers you need, and mark them with Spring Boot's **`@ServiceConnection`** annotation. Spring AI recognizes the container type and injects the matching connection details, with no manual endpoints, ports, or URLs.

```java
@SpringBootTest
@Testcontainers
class SupportAssistantIT {

    @Container
    @ServiceConnection
    static OllamaContainer ollama = new OllamaContainer("ollama/ollama");

    @Container
    @ServiceConnection
    static ChromaDBContainer chroma = new ChromaDBContainer("chromadb/chroma");

    // The application under test now talks to these real, throwaway services.
}
```

Spring AI provides these service connections for the model and vector store services you are likely to use in tests. That includes Ollama for running models locally and vector stores such as Chroma, Qdrant, Milvus, Weaviate, and others. The result is a self contained integration test.

## Living With Non-Determinism

A few realities are worth keeping in mind, because they shape how you write and run these tests.

- **The judge is itself a model**, so evaluation is non-deterministic and consumes tokens. Run the judging model at low temperature (ideally `0.0`) to keep verdicts as stable as possible, and treat the occasional disagreement as normal rather than a flaky test to chase.
- **Separate the two test tiers** Mock based unit tests are fast and deterministic, so run them constantly. Evaluator and Testcontainers tests are slower and heavier, so run them deliberately in a dedicated integration phase, in CI, or before a release, not on every save.
- **Pick the right model for each job** The model you ship is not always the best judge. A different and even smaller model may evaluate more reliably and cheaply.
- **Assert on properties, never on exact strings** Relevance, groundedness, the presence of a required field, and the absence of a forbidden claim all survive the model's natural variation. A hard coded sentence does not.
