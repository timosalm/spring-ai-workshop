# Observability — Hands-on Exercises

Your starting point is the [`sample-app/`](sample-app/), the assistant with RAG, tools, and tests. In this lab you add **observability**: structured logs of prompts and completions, Micrometer metrics including token usage, and finally traces and dashboards via OpenTelemetry and Grafana.

The `actuator` dependency is already on the project.

## 1. Debug logging for Spring AI

Spring AI's debug logging is already enabled from the tool-calling lab:

```properties
logging.level.org.springframework.ai=debug
```

Besides tool-call decisions, it shows rendered prompts, retrieved documents, and raw responses at the right log levels.

## 2. Expose the actuator endpoints

Expose the metrics and Prometheus endpoints. Append to `application.properties`:

```properties
management.endpoints.web.exposure.include=health,metrics,prometheus
```

> ⚠️ This exposes `/actuator/metrics` and `/actuator/prometheus` on the application port. **Don't do this in production** — bind actuator to a separate management port, restrict it to an internal network, and put authentication in front of it.

For the Prometheus format, add the Micrometer registry to `pom.xml`:

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
    <scope>runtime</scope>
</dependency>
```

## 3. Verbose observation content (opt-in)

Spring AI's observations leave out prompt and response content by default, because of the PII risk. Opt in for a workshop/dev setup. Append to `application.properties`:

```properties
# Not recommended for production - high data volume and risk of exposing sensitive content
spring.ai.chat.client.observations.log-prompt=true
spring.ai.chat.client.observations.log-completion=true
spring.ai.chat.client.observations.include-error-logging=true
spring.ai.tools.observations.include-content=true
spring.ai.vectorstore.observations.log-query-response=true
```

## 4. Generate traffic and inspect the metrics

Start the app, then exercise the two flows from the earlier labs a few times so the histograms have data:

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Does VMware Tanzu Spring provide commercial support for Micrometer?"
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Open a high priority ticket to request a trial for VMware Tanzu Spring"
```

List the metric names, then look at token usage:

```bash
curl -s http://localhost:8080/actuator/metrics | jq
curl -s http://localhost:8080/actuator/metrics/gen_ai.client.token.usage | jq
```

The response breaks down `input` vs `output` tokens — the numbers that drive cost. Also try `gen_ai.client.operation` (chat latency). The names follow the OpenTelemetry GenAI semantic conventions, with tags like `gen_ai.system`, `gen_ai.request.model`, `gen_ai.operation.name`, and `gen_ai.token.type`.

For vector-store timings the metric is `db.vector.client.operation` (following the OpenTelemetry DB semantic conventions, with tags like `db.system`, `db.operation.name`, and `spring.ai.kind`). The in-memory `SimpleVectorStore` only records it when its bean is built with the application's `ObservationRegistry`, so update the `simpleVectorStore` bean in `SupportAssistantConfiguration` to inject and pass it:

```java
@ConditionalOnMissingBean(VectorStore.class)
@Bean
VectorStore simpleVectorStore(EmbeddingModel embeddingModel, ObservationRegistry observationRegistry) {
    return SimpleVectorStore.builder(embeddingModel).observationRegistry(observationRegistry).build();
}
```

Add the import:

```java
import io.micrometer.observation.ObservationRegistry;
```

After a restart and a RAG query, `curl -s http://localhost:8080/actuator/metrics/db.vector.client.operation | jq` shows the timings.

Inspect the Prometheus endpoint too:

```bash
curl -s http://localhost:8080/actuator/prometheus | grep gen_ai
```

That's already enough to scrape with any Prometheus-compatible TSDB.

## 5. Export into Grafana with OpenTelemetry

For real dashboards, push everything over OTLP into a stack that stores and visualizes it. The `grafana/otel-lgtm` image bundles Grafana, Mimir (metrics), Loki (logs), Tempo (traces), and an OTel collector in one container. You run it with Docker Compose and let Spring Boot manage it.

Create a `compose.yaml` in the project root:

```yaml
services:
  otel-lgtm:
    image: grafana/otel-lgtm:latest
    container_name: support-assistant-otel-lgtm
    ports:
      - "3000:3000" # Grafana UI
      - "4317:4317" # OTLP gRPC
      - "4318:4318" # OTLP HTTP
```

`spring-boot-docker-compose` detects `compose.yaml` at startup, runs `docker compose up`/`stop` with the app, and recognizes the `grafana/otel-lgtm` image to fill in the OTLP endpoints for you. Add it after the `spring-boot-devtools` dependency in `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-docker-compose</artifactId>
    <scope>runtime</scope>
    <optional>true</optional>
</dependency>
```

Spring Boot 4.0 introduced the official `spring-boot-starter-opentelemetry` starter (4.1 builds on it). It wires the tracing, metrics, and logs OTLP exporters in one step and pulls in `io.micrometer:micrometer-registry-otlp`, which plays the same role over OTLP that `micrometer-registry-prometheus` played for scraping. **Replace** the Prometheus registry dependency with the starter:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-opentelemetry</artifactId>
</dependency>
```

You don't set any OTLP URLs — the Docker Compose connection provides them. Set the sampling and metrics tuning in `application.properties`:

```properties
management.tracing.sampling.probability=1.0
management.otlp.metrics.export.step=5s
management.metrics.tags.application=${spring.application.name}
```

`sampling.probability=1.0` captures every request (lower it for production). `export.step=5s` pushes metrics every 5 seconds so numbers show up in Grafana quickly. `management.metrics.tags.application` tags every metric with the application name so you can filter by it in Grafana.

Restart the app (Spring Boot starts the otel-lgtm container first, so the first run is slower while the image pulls), generate some traffic, then open Grafana at [http://localhost:3000](http://localhost:3000) (default credentials `admin` / `admin`). Mimir, Loki, and Tempo are pre-configured as datasources.

- **Explore → Tempo.** Search by service name `support-assistant`, then open a trace to see the chat span tree (HTTP request → ChatClient → vector-store query → tool invocation → second ChatClient call → response).
- **Explore → Loki.** Query `{service_name="support-assistant"}` for the structured Spring AI logs (with prompts/completions if you enabled the content flags).
- **Explore → Mimir.** The same `gen_ai_*` metrics, ready to graph. Token usage per request:

```promql
sum by (gen_ai_token_type, gen_ai_request_model) (
  rate(gen_ai_client_token_usage_total[1m])
)
```

## 6. Make the stack opt-in with a profile

The otel-lgtm container is heavy and the verbose logging is a privacy risk, so make them opt-in. First, gate the container behind an `otel` Docker Compose profile — add to the service in `compose.yaml`:

```yaml
    profiles:
      - otel
```

Collect every opt-in setting in a profile-specific `src/main/resources/application-local-observability.properties`. Spring Boot loads it only when the `local-observability` profile is active, and the last line activates the `otel` Compose profile:

```properties
# Not recommended for production - high data volume and risk of exposing sensitive content
spring.ai.chat.client.observations.log-prompt=true
spring.ai.chat.client.observations.log-completion=true
spring.ai.chat.client.observations.include-error-logging=true
spring.ai.tools.observations.include-content=true
spring.ai.vectorstore.observations.log-query-response=true

management.otlp.metrics.export.enabled=true
management.otlp.metrics.export.step=5s
management.metrics.tags.application=${spring.application.name}

management.otlp.tracing.export.enabled=true
management.tracing.sampling.probability=1.0

management.otlp.logging.export.enabled=true

spring.docker.compose.profiles.active=otel
```

Take those settings back out of `application.properties` and turn the exporters off by default there:

```properties
management.otlp.metrics.export.enabled=false
management.otlp.tracing.export.enabled=false
management.otlp.logging.export.enabled=false
```

Now the default run is lightweight. When you want the full stack again, start with the profile:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local-observability
```

## Recap

The assistant now emits structured logs, Micrometer metrics (including token usage for cost), and OpenTelemetry traces you can explore in Grafana — all through the same observability stack you use for the rest of Spring Boot. Next you connect the assistant to external tools with MCP.
