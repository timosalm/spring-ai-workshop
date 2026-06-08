# Spring AI Support Assistant — Observability Walkthrough

Continues from `INSTRUCTIONS-4.md`. By now you have the full Support Assistant: chat, structured output, RAG, tool calling, and tests.

This part wires up **observability**: structured logs of every prompt/completion, Micrometer metrics (including token usage), and — optionally — OpenTelemetry export into a local Grafana stack. Spring AI hooks into Spring's `Observation` API, so you get traces and metrics for chat calls, embeddings, vector store queries, and tool invocations out of the box.

## 1. Application configuration

Add (or confirm) the following in `src/main/resources/application.properties`.

### Debug logging for Spring AI

```properties
logging.level.org.springframework.ai=debug
```

You had this already from INSTRUCTIONS-3 to see tool-call decisions. It also surfaces the rendered prompts, retrieved documents, and raw responses at the right log levels — handy when an answer is surprising and you want to know why.

### Expose the actuator endpoints

```properties
management.endpoints.web.exposure.include=health,metrics,prometheus
```

> ⚠️ **Security note.** This exposes `/actuator/metrics` and `/actuator/prometheus` on the application port. **Do not do this in production.** Bind actuator to a separate management port (`management.server.port`), restrict it to an internal network, and put authentication in front of it. The endpoints leak request paths, model names, and (with the flags below) prompt content — all worth protecting.

For Prometheus formatting you also need the Micrometer registry — add to `pom.xml`:

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
    <scope>runtime</scope>
</dependency>
```

### Verbose Spring AI observation content

Spring AI's observations don't include prompt/response content by default (PII risk). Opt in if you want it in logs and traces:

```properties
# Not recommended for production — high data volume and risk of exposing sensitive content
spring.ai.chat.client.observations.log-prompt=true
spring.ai.chat.client.observations.log-completion=true
spring.ai.chat.client.observations.include-error-logging=true
spring.ai.tools.observations.include-content=true
spring.ai.vectorstore.observations.log-query-response=true
```

In a workshop or development setup this is gold — you see every prompt the model sees, every retrieved document, every tool argument. In production it's a compliance liability; default it off.

## 2. Generate traffic and inspect the metrics

Restart the app and exercise the two flows you built in earlier instructions.

### RAG query

```bash
curl -G "http://localhost:8080/api/1.0/chat" \
     --data-urlencode "query=What is Tanzu Spring Runtime?"
```

### Tool-calling query

```bash
curl -G "http://localhost:8080/api/1.0/chat" \
     --data-urlencode "query=Please open a high-priority ticket: SSO login returns 502 on the Tanzu portal."
```

Repeat each a few times so the histograms have something interesting to show.

### Inspect via the metrics endpoint

```bash
# All metric names registered in the app
curl http://localhost:8080/actuator/metrics | jq

# Drill into one — total tokens consumed per call type (input vs output)
curl http://localhost:8080/actuator/metrics/gen_ai.client.token.usage | jq

# Chat client request latency
curl http://localhost:8080/actuator/metrics/gen_ai.client.operation | jq

# Vector store query timings
curl http://localhost:8080/actuator/metrics/spring.ai.vector.store.client.operation | jq
```

The metric names follow the OpenTelemetry GenAI semantic conventions. Useful tags you'll see: `gen_ai.system` (`openai`, `anthropic`, ...), `gen_ai.request.model`, `gen_ai.operation.name` (`chat`, `embedding`, ...), and on token usage `gen_ai.token.type` (`input` / `output`).

### Inspect via the Prometheus endpoint

```bash
curl http://localhost:8080/actuator/prometheus | grep gen_ai
```

You'll see entries like:

```
gen_ai_client_token_usage_sum{gen_ai_system="openai",gen_ai_request_model="gpt-5.4-mini",gen_ai_token_type="input"} 482.0
gen_ai_client_token_usage_count{gen_ai_system="openai",gen_ai_request_model="gpt-5.4-mini",gen_ai_token_type="input"} 3
gen_ai_client_operation_seconds_bucket{gen_ai_operation_name="chat",le="0.5"} 1.0
```

That's already enough to scrape with any Prometheus-compatible TSDB.

Reference: <https://docs.spring.io/spring-ai/reference/observability/index.html>.

### Notes per provider

The metric *names* and *shapes* are provider-agnostic — they follow the OTel GenAI conventions. What varies is the `gen_ai.system` tag value (`openai`, `anthropic`, `bedrock`, `ollama`) and which model labels carry meaningful data:

- **OpenAI / Anthropic / Bedrock** — every call has accurate input/output token counts because the provider returns them in the response. Cost dashboards are straightforward.
- **Ollama** — token counts are reported but the values come from the local model and aren't billed against anything; useful for throughput, not cost.

### Notes per database / vector store

- **`SimpleVectorStore`** — emits `spring.ai.vector.store.*` observations the same way as pgvector, so dashboards transfer between profiles without changes.
- **pgvector** — same metrics, plus you get the standard JDBC/HikariCP metrics (`hikaricp_*`, `jdbc_*`) for connection-pool health.

## 3. (Optional) OpenTelemetry export into the Grafana LGTM stack

The actuator endpoints are great for spot checks. For a real dashboard, push everything via OTLP into a stack that stores and visualizes it. The project's `compose.yaml` already includes a `grafana/otel-lgtm:latest` service under the `otel` profile — that image bundles Grafana, Mimir (metrics), Loki (logs), Tempo (traces), and an OTel collector on `:4317`/`:4318`.

### Upgrade Spring Boot to 4.1.0-RC1

Spring Boot **4.1.0-RC1** ships a new, simpler observability story for AI apps: the `spring-boot-starter-opentelemetry` starter wires tracing, metrics, and logs OTLP exporters in one shot, and the Spring AI observation set is broader (per-document retrieval spans, structured tool-call attributes). If you're still on `4.0.6`, bump the parent in `pom.xml`:

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.1.0-RC1</version>
    <relativePath/>
</parent>
```

And add the starter:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-opentelemetry</artifactId>
</dependency>
```

### Start the stack

```bash
docker compose --profile otel up -d
```

Grafana is at <http://localhost:3000> (default credentials `admin` / `admin`). The image pre-configures Mimir, Loki, and Tempo as datasources.

### Point Spring Boot at the OTLP endpoints

Append to `application.properties`:

```properties
management.otlp.tracing.endpoint=http://localhost:4318/v1/traces
management.otlp.metrics.export.url=http://localhost:4318/v1/metrics
management.otlp.logging.endpoint=http://localhost:4318/v1/logs

management.tracing.sampling.probability=1.0
```

`sampling.probability=1.0` captures every request — fine for development, dial it down (`0.1`, `0.01`) before anything resembling production traffic.

Restart, run the two curls from step 2 again, and confirm in Grafana:

- **Explore → Tempo** — search by service name `support-assistant`; click a trace to see the chat span tree (HTTP request → ChatClient call → vector store query → tool invocation → second ChatClient call → response).
- **Explore → Loki** — `{service_name="support-assistant"}` shows the structured Spring AI logs (with prompts/completions if you enabled the flags in step 1).
- **Explore → Mimir** — the same `gen_ai_*` metrics, ready to graph.

## 4. Visualize token usage per request in Grafana

Token usage is the metric most teams want first — it's the proxy for cost.

In Grafana, **Explore → Prometheus** and paste:

```promql
sum by (gen_ai_token_type, gen_ai_request_model) (
  rate(gen_ai_client_token_usage_total[1m])
)
```

What this asks: "across the last minute, how many tokens per second are we burning, broken down by whether they were input or output and by which model served them?"

## Recap

| Step | What changed | Key API |
|------|--------------|---------|
| 1 | Actuator exposure + Spring AI debug logging + observation content flags | `management.endpoints.web.exposure.include`, `spring.ai.*.observations.*` |
| 2 | Token / latency / vector-store metrics over HTTP | `/actuator/metrics`, `/actuator/prometheus`, GenAI OTel conventions |
| 3 | OTLP export into Grafana LGTM (Spring Boot 4.1.0-RC1) | `spring-boot-starter-opentelemetry`, `management.otlp.*` |
| 4 | Token-usage dashboard | `gen_ai_client_token_usage_sum` in PromQL |