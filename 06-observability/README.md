## You Can't Operate What You Can't See

The support assistant you've built does a lot behind a single `.call()`: it runs advisors, retrieves documents from a vector store, calls a model over the network, maybe executes a tool or two, and parses the result into a record. When it works, that opacity is a feature. When it *doesn't*, or when the monthly bill arrives, it becomes a problem.

AI features raise questions traditional services rarely do. Why was that answer slow, the model call or the vector search? How many tokens did this request burn, and which endpoint is driving the cost? Did the model actually call the tool I expected? Is latency creeping up because the provider is degraded? You can't answer any of these by reading code. You need the running system to *tell* you what it's doing, and that's what observability is for.

Here the news is good, in the same way it was for HTTP back in the first section. Observability is something Spring has long done well, and Spring AI doesn't invent a parallel universe for it. It plugs AI operations into the exact same observability stack you already use for the rest of your Spring Boot application.

## Built on Micrometer and Actuator

Spring AI's observability is built on **Micrometer** and **Spring Boot Actuator**, the same foundation that produces metrics and traces for your web endpoints, datasource, and HTTP clients. That's the whole design philosophy: an AI call is just another instrumented operation. The metrics flow to whatever monitoring backend you've configured (Prometheus, and so on), and the traces flow to your tracing backend (Zipkin, an OpenTelemetry collector, and the like). If you've set up Actuator before, you already know how to consume what Spring AI emits, no new tooling, no separate dashboard system.

Concretely, you add the Actuator starter and whatever registry/exporter you use (for example, a Prometheus registry for metrics and an OpenTelemetry or Zipkin exporter for traces), and Spring AI's instrumentation lights up automatically. The auto-configuration wires its observations into the same Micrometer `ObservationRegistry` the rest of Boot uses.

## Two Kinds of Signals: Metrics and Traces

Observability here comes in two complementary forms, and it helps to keep them straight.

**Metrics** are aggregate numbers over time: how many model calls happened, how long they took on average, how many tokens you've consumed. They answer "how is the system behaving in general?" and power dashboards and alerts.

**Traces** follow a *single* request as it moves through the layers, showing the work as a tree of timed **spans**. They answer "what happened on *this* request, and where did the time go?" A single user question to your assistant produces a span hierarchy that mirrors the call: the `ChatClient` operation at the top, the advisors inside it, the vector-store query, the model call, and any tool executions, each as a nested span with its own duration.

To keep both useful and affordable, Spring AI follows a Micrometer convention worth understanding. Each observation is tagged with attributes, split into **low-cardinality** and **high-cardinality** keys. Low-cardinality keys (things with few possible values, like the provider name or operation type) go on *both* metrics and traces, because they're safe to aggregate. High-cardinality keys (things with many possible values, like a response id, token counts, or the tool arguments) go on *traces only*, because turning them into metric dimensions would explode your time-series database. So you slice your dashboards by stable dimensions, and dig into the rich per-request detail in a trace.

## What Gets Instrumented

The instrumentation spans the whole pipeline you've assembled across the previous sections, so the observability picture matches the mental model you already have:

- **`ChatClient`** — the top-level `call()`/`stream()` operation, tagged with details like the advisors configured, the tool names passed, and the conversation id.
- **Advisors** — each advisor execution is its own observation (`spring.ai.advisor`), including its order in the chain, so you can see the RAG or memory advisor doing its work.
- **`ChatModel`** — the actual provider call (`gen_ai.client.operation`), carrying the request and response model names, sampling settings like temperature and max tokens, finish reasons, and, crucially, **token usage**.
- **Tool calls** — each tool invocation (`spring.ai.tool`) with the tool name and type, so you can confirm the model called what you expected and how long it took.
- **`VectorStore`** — `add`, `delete`, and `query` operations (`db.vector.client.operation`) across all implementations, tagged with the database system, the `topK`, the similarity threshold, and more.
- **`EmbeddingModel`** and **`ImageModel`** — their own operations and token usage for the providers that support it.

Because these nest, a trace of one request reads like a story: ChatClient → advisors → (vector-store query) → ChatModel → (tool call) → ChatModel again. When something is slow or wrong, you see exactly which layer to blame.

## Token Usage: Observability Is Also Cost Control

One signal deserves singling out, because it's unique to AI applications. Providers bill per token, so **token usage is cost**, and Spring AI exposes it as a first-class metric, `gen_ai.client.token.usage`, broken down by type:

- `input` — tokens in the prompt you sent (including any retrieved RAG context).
- `output` — tokens in the model's completion.
- `total` — the sum.

This turns an abstract worry ("are we spending too much?") into a number you can chart, alert on, and attribute. You can watch how much your RAG context is inflating prompt size, spot a runaway tool loop by its token spike, or compare the cost of two models. The same `getUsage()` data you met on a single `ChatResponse` back in the chat section is here aggregated across every call your application makes.

## Logging Prompts and Completions: Powerful, and Opt-In

Metrics and span tags tell you *that* a call happened and *how* it behaved, but deliberately leave out the most sensitive thing: the actual content of prompts and responses. When you're debugging "why did the model answer *that*?", though, seeing the real text is invaluable. Spring AI lets you turn it on, but it is **disabled by default, and for good reason**: prompts and completions routinely contain user data, private documents, and other sensitive information you do not want flowing into your logs by accident.

So content logging is strictly opt-in, per surface, via configuration. For example:

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

Treat these as debugging switches, not production defaults. The guiding principle is privacy by default: you must consciously decide to record content, and you should weigh that against where your logs are stored and who can read them. When tracing is active, these logs carry trace-correlation ids, so you can line a logged prompt up with the exact span it came from.

## Tracing Across the Boundary

Because the tracing builds on the standard stack, it also follows requests *out* of your application. Spring AI propagates trace context (the `traceparent` header, per OpenTelemetry's conventions for generative-AI operations) to downstream services, an AI gateway, a proxy, a self-hosted inference server. So a single trace can span your application *and* the infrastructure in front of the model, which is exactly what you want when latency is being introduced somewhere between your code and the provider. Following the OpenTelemetry GenAI semantic conventions also means your AI telemetry speaks the same vocabulary as the broader ecosystem of tools and dashboards.

## What's Next

Observability closes the loop on running a Spring AI application in earnest. Because it's built on **Micrometer and Spring Boot Actuator**, your AI operations produce **metrics** and **distributed traces** through the same stack as the rest of your app, no new tooling required. Every layer you've built is instrumented, ChatClient, advisors, ChatModel, tools, and the vector store, so a trace mirrors the request and a dashboard tracks the system. **Token-usage metrics** make spend visible and chartable, while prompt and completion **content logging stays opt-in** to protect sensitive data. In the next section you'll wire this up: enable Actuator, watch the spans of a real request to your support assistant, and read its token usage and latency for yourself.
