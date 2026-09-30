# Observability

The support assistant you have built does a lot. It runs advisors, retrieves documents from a vector store, calls a model over the network, maybe executes a tool or two, and parses the result into a record. When it works, all of that happening out of sight is a feature. When it does *not* work, or when the monthly bill arrives, it becomes a problem.

AI features raise questions that traditional services rarely raise. Why was that answer slow, the model call or the vector search? How many tokens did this request burn, and which endpoint is driving the cost? Did the model actually call the tool you expected? Is latency creeping up because the provider is degraded? You cannot answer any of these by reading code. You need the running system to *tell* you what it is doing, and that is what observability is for.

Here the news is good, in the same way it was for HTTP back in the first section. Observability is something Spring has long done well, and Spring AI does not invent a parallel universe for it. It plugs AI operations into the exact observability stack you already use for the rest of your Spring Boot application.

## Built on Micrometer and Actuator

The observability of Spring AI is built on **Micrometer** and **Spring Boot Actuator**, the same foundation that produces metrics and traces for your web endpoints, your datasource, and your HTTP clients. That is the whole design philosophy. An AI call is just another instrumented operation. The metrics flow to whatever monitoring backend you have configured, such as Prometheus, and the traces flow to your tracing backend, such as Zipkin or an OpenTelemetry collector. If you have set up Actuator before, you already know how to consume what Spring AI emits, with no new tooling and no separate dashboard system.

Concretely, you add the Actuator starter and whatever registry or exporter you use, for example a Prometheus registry for metrics and an OpenTelemetry or Zipkin exporter for traces, and the instrumentation of Spring AI lights up on its own. The autoconfiguration wires its observations into the same Micrometer `ObservationRegistry` that the rest of Boot uses.

The building block behind all of this is the `Observation` API of Micrometer. An observation is a single instrumentation point around a piece of work. Your application records it once, and the handlers registered on the `ObservationRegistry` turn that one recording into several outputs. A meter handler writes timers and counters for the metrics backend, a tracing handler creates a span, and a logging handler writes it to the application log. Spring AI records these observations for you inside the `ChatClient`, the model implementations, the advisors, and the vector stores, which is why the signals show up without a single line of instrumentation code in your own classes.

## Two Kinds of Signals, Metrics and Traces

Observability here comes in two forms that complement each other, and it helps to keep them apart.

**Metrics** are aggregate numbers over time, such as how many model calls happened, how long they took on average, and how many tokens you consumed. They answer how the system behaves in general, and they power dashboards and alerts.

**Traces** follow a *single* request as it moves through the layers, showing the work as a tree of timed **spans**. They answer what happened on *this* request and where the time went. A single user question to your assistant produces a span hierarchy that mirrors the call, with the `ChatClient` operation, the advisors inside it, the vector store query, the model call, and any tool executions, each as a nested span with its own duration.

To keep both useful and affordable, Spring AI follows a Micrometer convention that is worth understanding. Each observation is tagged with attributes, split into **low-cardinality** and **high-cardinality** keys. Low-cardinality keys have few possible values, such as the provider name or the operation type, and they go on *both* metrics and traces, because they are safe to aggregate. High-cardinality keys have many possible values, such as a conversation id, token counts, or tool arguments, and they go on *traces only*, because turning them into metric dimensions would explode your time series database. So you slice your dashboards by stable dimensions and dig into the rich per request detail in a trace.

## What Gets Instrumented

The instrumentation covers the whole pipeline you assembled across the previous sections, so the observability picture matches the mental model you already have.

- **`ChatClient`** covers the top level `call()` and `stream()` operation, tagged with details such as the advisors configured, the tool names passed, and the conversation id.
- **Advisors** each get their own observation (`spring.ai.advisor`), including their order in the chain, so you can see the RAG or memory advisor doing its work.
- **`ChatModel`** covers the actual provider call (`gen_ai.client.operation`), carrying the request and response model names, sampling settings such as temperature and max tokens, finish reasons, and, most importantly, **token usage**.
- **Tool calls** each get an observation (`spring.ai.tool`) with the tool name and type, so you can confirm that the model called what you expected and how long it took.
- **`VectorStore`** covers the `add`, `delete`, and `query` operations (`db.vector.client.operation`) across all implementations, tagged with the database system, the `topK`, the similarity threshold, and more.
- **`EmbeddingModel`** and **`ImageModel`** get their own operations and token usage for the providers that report it.

Because these nest, a trace of one request reads like a story. ChatClient, then advisors, then the vector store query, then ChatModel, then a tool call, then ChatModel again. When something is slow or wrong, you see exactly which layer to blame.

## Token Usage Is Also Cost Control

One signal deserves to be singled out, because it is unique to AI applications. Providers bill per token, so **token usage is cost**, and Spring AI exposes it as a first class metric, `gen_ai.client.token.usage`, broken down by type.

- `input` counts the tokens in the prompt you sent, including any retrieved RAG context.
- `output` counts the tokens in the completion of the model.
- `total` is the sum of both.

This turns an abstract worry about spending too much into a number you can chart, alert on, and attribute. You can watch how much your RAG context inflates the prompt size, spot a runaway tool loop by its token spike, or compare the cost of two models. The same `getUsage()` data you met on a single `ChatResponse` back in the chat section is aggregated here across every call your application makes.

## Logging Prompts and Completions, Powerful but Opt-In

Metrics and span tags tell you *that* a call happened and *how* it behaved, but they deliberately leave out the most sensitive thing, the actual content of prompts and responses. When you are debugging why the model answered *that*, seeing the real text is invaluable. Spring AI lets you turn it on, but it is **disabled by default, and for good reason**. Prompts and completions routinely contain user data, private documents, and other sensitive information that you do not want flowing into your logs by accident. There is a second reason as well. The prompt and the answer are different on every single request, so they are exactly the high-cardinality data described earlier, and they have no business on a metric.

So content logging is strictly opt-in and configured per surface. The following properties are examples.

```properties
# Log the ChatClient prompt and completion
spring.ai.chat.client.observations.log-prompt=true
spring.ai.chat.client.observations.log-completion=true

# Log the ChatModel prompt/completion and errors
spring.ai.chat.observations.log-prompt=true
spring.ai.chat.observations.log-completion=true
spring.ai.chat.observations.include-error-logging=true

# Include tool-call arguments and results
spring.ai.tools.observations.include-content=true

# Include the documents returned by a vector-store query
spring.ai.vectorstore.observations.log-query-response=true
```

Look closely at the two different prefixes for chat. The `spring.ai.chat.client.observations` prefix sits at the `ChatClient` level, so it captures the request the way your code handed it over, before any advisor changed it. The `spring.ai.chat.observations` prefix sits at the `ChatModel` level, which is the request that finally goes to the provider, with the retrieved documents, the conversation history, and the output format instructions already in it. Comparing those two log lines is the fastest way to see what your advisor chain actually did. The same split exists for tools and for the vector store, each with its own prefix.

When you switch one of these flags on, Spring AI registers the matching logging handlers and writes a warning at startup as a reminder that the content can be sensitive.

Treat these as debugging switches rather than production defaults. The guiding principle is privacy by default. You have to decide consciously to record content, and you should weigh that against where your logs are stored and who can read them. When tracing is active, these logs carry trace correlation ids, so you can line a logged prompt up with the exact span it came from.

## Tracing Across the Boundary

Because the tracing builds on the standard stack, it also follows requests *out* of your application. Spring AI propagates the trace context, the `traceparent` header from the OpenTelemetry conventions for generative AI operations, to downstream services such as an AI gateway, a proxy, or a self hosted inference server. So a single trace can span your application *and* the infrastructure in front of the model, which is exactly what you want when latency appears somewhere between your code and the provider. Following the OpenTelemetry GenAI semantic conventions also means your AI telemetry speaks the same vocabulary as the wider ecosystem of tools and dashboards.
