## How Do You Test Something That's Never the Same Twice?

Every test you've ever written rests on a quiet assumption: the same input produces the same output. Call the method, assert the result, done. That assumption is exactly what a language model breaks. Ask it the same question twice and you'll get two different answers, same meaning, perhaps, but different words, different ordering, different length. The output is *probabilistic*, not deterministic.

This pulls the rug out from under the usual approach. `assertEquals("...", answer)` is hopeless, you'd be pinning down wording the model was never going to reproduce. But "I can't assert the exact string" doesn't mean "I can't test." It means testing an AI application needs a different mindset, and really two different kinds of tests for two different kinds of code.

The trick is to separate what's deterministic from what isn't. Your application is mostly ordinary code, prompt construction, advisor wiring, tool methods, structured-output mapping, and only the model's text generation is probabilistic. Those two halves call for two different testing strategies.

## Two Things to Test

**The deterministic shell.** Everything *around* the model is normal Java that you can test the normal way. Does your tool method open a ticket with the right summary? Does your service apply the correct `SearchRequest`? Does the structured-output mapping turn the model's JSON into the right `SupportResponse` record? None of this requires a real model at all. Because Spring AI is built from ordinary Spring beans, you can inject a *stubbed or mocked* `ChatModel` that returns a canned response, and then assert your surrounding logic with plain JUnit and Mockito, fast, deterministic, free. This is where the bulk of your tests should live, and it's just good Spring testing applied to AI code.

**The probabilistic output.** At some point, though, you need to know whether the model's *actual answers* are any good, relevant, grounded in your documents, free of hallucinations. You can't mock your way to that; you have to run the real model and judge what comes back. Since you can't assert exact text, you assert *properties* of the answer instead: is it on-topic, is it supported by the retrieved context, does it avoid inventing facts? This is the part that's genuinely new, and Spring AI gives it first-class support through **evaluators**.

## Evaluators: Using a Model to Judge a Model

How do you check, in an automated test, whether a free-form answer is "relevant" or "factually grounded"? Writing code to understand natural language is the very problem language models exist to solve, so Spring AI turns the model on itself. An **evaluator** uses an LLM to assess another LLM's output, the pattern often called *LLM-as-a-judge*. The model under test produces an answer; a second call asks a model to score that answer against the question and context.

The contract is a single interface:

```java
@FunctionalInterface
public interface Evaluator {
    EvaluationResponse evaluate(EvaluationRequest evaluationRequest);
}
```

You give it an **`EvaluationRequest`** bundling the three things any judgment needs, and get back an **`EvaluationResponse`** whose `isPass()` tells you whether the answer met the bar:

```java
public EvaluationRequest(String userText, List<Content> dataList, String responseContent)
```

- **`userText`** — what the user asked.
- **`dataList`** — the context the answer was supposed to be based on, typically the documents your RAG step retrieved.
- **`responseContent`** — the answer the model actually produced.

Under the hood the evaluator renders these into a carefully worded prompt ("here is a query, a response, and some context, is the response in line with the context? Answer YES or NO") and sends it to a judging `ChatClient`. The natural-language verdict is parsed back into a simple pass/fail. Spring AI ships two evaluators for the failure modes you care about most.

## `RelevancyEvaluator`: Is the Answer On Point?

The **`RelevancyEvaluator`** checks whether a response actually addresses the user's question given the provided context. It's the natural way to validate a RAG flow end to end: you ran retrieval, you fed the documents to the model, did the answer use them to address the question, or did it wander off? You construct it with a `ChatClient.Builder` (the model that will act as judge) and hand it the request:

```java
RelevancyEvaluator evaluator = new RelevancyEvaluator(ChatClient.builder(chatModel));

EvaluationRequest request = new EvaluationRequest(question, retrievedDocuments, answer);
EvaluationResponse response = evaluator.evaluate(request);

assertThat(response.isPass()).isTrue();
```

In a real RAG test, the `retrievedDocuments` come straight from the context the advisor surfaced, so you're evaluating the same documents the model saw. If the default judging prompt doesn't fit your domain, you can supply your own via `.promptTemplate(...)`, as long as it keeps the `{query}`, `{response}`, and `{context}` placeholders the evaluator fills in.

## `FactCheckingEvaluator`: Did the Model Make Things Up?

Relevance isn't the same as truth, an answer can be perfectly on-topic and still contain a fabricated detail. The **`FactCheckingEvaluator`** targets hallucination directly: it checks whether the claims in the response are actually *supported by* the supplied context. It treats the context as a document and the answer as a claim, and asks the judging model whether the document backs the claim:

```java
FactCheckingEvaluator evaluator = new FactCheckingEvaluator(ChatClient.builder(chatModel));

EvaluationRequest request = new EvaluationRequest(context, Collections.emptyList(), claim);
EvaluationResponse response = evaluator.evaluate(request);

assertThat(response.isPass()).isFalse(); // a claim the context doesn't support should fail
```

A nice property here: the judge doesn't have to be your biggest, most expensive model. Fact-checking is a narrow task, and small purpose-built models (run locally via Ollama, for example) can do it well, cheaply, and fast, which matters when these checks run as part of your test suite.

Both evaluators are the building blocks of a **basic response-quality test**: ask your assistant a known question, capture the answer and the context it was given, and assert that the answer is both relevant and factually grounded. That's exactly the kind of test you'll write in the exercise.

## Integration Testing With Real Infrastructure

Evaluating real output means actually running the model, and usually the vector store too, and you don't want your tests reaching out to a paid cloud API or depending on services a teammate may not have running. This is where **Testcontainers** comes in: it spins up real services in Docker for the duration of a test, then tears them down. Spring AI integrates with it so those containers wire themselves into your application automatically.

Add the `spring-ai-spring-boot-testcontainers` dependency, declare the containers you need, and mark them with Spring Boot's **`@ServiceConnection`** annotation. Spring AI recognizes the container type and injects the matching connection details, no manual endpoints, ports, or URLs:

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

Spring AI provides these service connections for the model and vector-store services you're likely to use in tests, Ollama for running models locally, and vector stores such as Chroma, Qdrant, Milvus, Weaviate, and others. The result is a self-contained integration test: a real model, a real vector store, your real RAG and tool code, all started fresh and judged by an evaluator, with nothing to install or configure by hand.

## Living With Non-Determinism

A few realities are worth keeping in mind, because they shape how you write and run these tests.

- **The judge is itself a model**, so evaluation is non-deterministic and consumes tokens. Run a judging model at low temperature (ideally `0.0`) to make verdicts as stable as possible, and treat occasional disagreement as a fact of life rather than a flaky bug to chase.
- **Separate the two test tiers.** Mock-based unit tests are fast and deterministic, run them constantly. Evaluator and Testcontainers tests are slower and heavier, run them deliberately (a dedicated integration phase, CI, before a release), not on every save.
- **Pick the right model for each job.** The model you ship isn't necessarily the best judge; a different, even smaller, model may evaluate more reliably and cheaply.
- **Assert on properties, never on exact strings.** Relevance, groundedness, the presence of a required field, the absence of a forbidden claim, these survive the model's natural variation; a hard-coded sentence does not.

## What's Next

Testing AI applications comes down to drawing one line clearly: the deterministic code around the model you test conventionally with a mocked `ChatModel`, and the probabilistic output you test by asserting *qualities* of the answer rather than its exact words. Spring AI gives you **evaluators** to make those quality assertions, `RelevancyEvaluator` for on-topic answers and `FactCheckingEvaluator` for grounded ones, and **Testcontainers** support to run the whole thing against a real model and vector store in throwaway Docker containers. In the next section you'll put this to work with a basic response-quality test that checks your support assistant's answers are both relevant and free of hallucination.
