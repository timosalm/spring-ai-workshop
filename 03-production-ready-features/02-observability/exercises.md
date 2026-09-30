# Observability Lab

By now the support assistant covers chat, structured output, RAG, tool calling, and tests. In this lab you add **observability**. You get structured logs of every prompt and completion, and Micrometer metrics that include the token usage. Spring AI hooks into the `Observation` API of Spring, so you get metrics and traces for chat calls, embeddings, vector store queries, and tool calls without extra work. In the second part you push traces and metrics over OpenTelemetry into a local Grafana stack.

Read [Observability](observability.md) first.

## Before You Start

Your starting point is the [`sample-app/`](sample-app/) of this folder. It contains the state after the testing lab. All paths in this lab are relative to `sample-app/`, and all commands run inside it.

As before you work with two terminals. **Terminal 1** runs the application, and **Terminal 2** sends requests with `curl`. After a change to `pom.xml` or to a file in `src/main/resources` you stop the application with `Ctrl+C` and start it again.

The second part of this lab needs **Docker**. Some commands pipe JSON through [`jq`](https://jqlang.org) for readable output. If you do not have `jq`, leave out `| jq`.

## 1. Debug Logging for Spring AI

Requests and responses are not logged out of the box. You get them from the `SimpleLoggerAdvisor` that you registered on the `ChatClient` in the advisors part of the fundamentals lab. Open `src/main/java/com/example/support_assistant/SupportAssistantConfiguration.java` and find this line.

```java
new SimpleLoggerAdvisor(Ordered.LOWEST_PRECEDENCE),
```

The advisor logs at `DEBUG` level, so it stays quiet until you raise the log level. That is already done in `src/main/resources/application.properties` since the tool calling lab, which also turns on the debug output of Spring AI itself, such as the tool call decisions.

```properties
logging.level.org.springframework.ai=debug
```

The advisor runs with the lowest precedence, so it sees the request after every other advisor has changed it. The logged prompt therefore contains the documents retrieved for RAG and the conversation history added by the memory advisor. This helps when an answer is surprising and you want to know why.

## 2. Expose the Actuator Endpoints

Spring Boot Actuator adds endpoints to a running application that show what it is doing. It comes with the `spring-boot-starter-actuator` dependency, which is already part of the project.

Only `health` is reachable over HTTP by default, so you have to list the endpoints you want. This lab needs three of them.

- `health` confirms that the application has started.
- `metrics` lists all metric names and returns the values of a single metric.
- `prometheus` returns the same metrics in the text format that a Prometheus server scrapes.

Append the following lines to `src/main/resources/application.properties`.

```properties

management.endpoints.web.exposure.include=health,metrics,prometheus
```

> ⚠️ **Security note.** This exposes `/actuator/metrics` and `/actuator/prometheus` on the application port. **Do not do this in production.** Bind the actuator to a separate management port with `management.server.port`, restrict it to an internal network, and put authentication in front of it. The endpoints leak request paths, model names, and, with the flags of the next step, prompt content, which are all worth protecting.

The `prometheus` endpoint only shows up when a Micrometer Prometheus registry is on the classpath. Add the following dependency to `pom.xml`, right after the `spring-boot-starter-actuator` dependency.

```xml
		<dependency>
			<groupId>io.micrometer</groupId>
			<artifactId>micrometer-registry-prometheus</artifactId>
			<scope>runtime</scope>
		</dependency>
```

## 3. Spring AI Observations and Their Content

Spring AI records a Micrometer observation for every step of an AI call, so the metrics are there without any instrumentation code in your application.

The prompt and the completion of the `ChatClient` and of the chat model are usually big and can contain sensitive information. For those reasons they are not exported by default. Spring AI can log them instead, which helps with debugging and troubleshooting. When tracing is available, those log lines carry the trace information, so you can match a logged prompt with its trace.

The input arguments and the result of a tool call are not exported by default either, for the same reason. Spring AI can add them to the observation as span attributes.

Turning any of this on brings the risk of exposing sensitive or private information, so be careful. Append the following lines to `src/main/resources/application.properties`.

```properties

# Not recommended for production - high data volume and risk of exposing sensitive content
spring.ai.chat.client.observations.log-prompt=true
spring.ai.chat.client.observations.log-completion=true

spring.ai.chat.observations.log-prompt=true
spring.ai.chat.observations.log-completion=true
spring.ai.chat.observations.include-error-logging=true

spring.ai.tools.observations.include-content=true

spring.ai.vectorstore.observations.log-query-response=true
```

Watch the two different prefixes, because you compare their output in a moment. `spring.ai.chat.client.observations` sits at the level of the `ChatClient`, so it captures the request before the advisors changed it. `spring.ai.chat.observations` sits at the level of the `ChatModel`, which is the request that finally goes to the provider.

Spring AI logs a warning at startup whenever you enable one of these flags. Keep them off in production, where the content is a risk for the data volume and for privacy.

## 4. Generate Traffic and Inspect the Logs and Metrics

In **Terminal 1**, export your key and start the application.

```bash
export OPENAI_API_KEY=sk-...
./mvnw spring-boot:run
```

Wait for `Started SupportAssistantApplication` in the logs. Then try the two flows you built in the earlier labs in **Terminal 2**. First a RAG query.

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Does VMware Tanzu Spring provide commercial support for Micrometer?"
```

Then a tool calling query.

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Open a high priority ticket to request a trial for VMware Tanzu Spring"
```

Run each one a few times, so the histograms have some data to show.

### Watch the Logs

Look at the application logs for the two requests in Terminal 1. They now come from two different sources. The `SimpleLoggerAdvisor` writes the `request:` and `response:` lines at `DEBUG` level, and the observation handlers you just switched on write at `INFO` level.

Compare the prompts in that output. The `request:` line from the advisor holds the retrieved documents and the JSON schema, because the advisor runs last in the chain and sees the finished request. The `Chat Model Prompt Content:` line holds the same information. The `Chat Client Prompt Content:` line, however, holds only the question that your controller passed in, because that observation is opened before the advisors run. This is the difference between the two property prefixes, now visible in the logs.

The tool calling query adds the tool components and a second round trip to the model. The first response carries no text at all. It has the `finishReason` `TOOL_CALLS` and the arguments the model wants to use. Spring AI runs your method, converts the return value to JSON, appends it as a `ToolResponseMessage`, and sends the whole conversation to the model a second time. Only that second response holds the answer for the user. Two model calls also mean two entries in the token metrics that you look at next.

### Query the Metrics Endpoint

The `metrics` endpoint is the quickest way to see what Micrometer collected, with nothing but `curl`. Called without a name it returns the list of all metric names. Called with a name it returns the current values together with the tags that split them up.

List all metric names registered in the application.

```bash
curl -s http://localhost:8080/actuator/metrics | jq
```

Look at the total tokens used per call type, input versus output.

```bash
curl -s http://localhost:8080/actuator/metrics/gen_ai.client.token.usage | jq
```

The response breaks down `input` tokens, which were sent to the model, and `output` tokens, which the model generated. These are the numbers that drive the cost.

Look at the latency of the chat client requests.

```bash
curl -s http://localhost:8080/actuator/metrics/gen_ai.client.operation | jq
```

The metric names follow the OpenTelemetry GenAI semantic conventions. You see useful tags such as `gen_ai.system` (`openai`, `anthropic`, ...), `gen_ai.request.model`, `gen_ai.operation.name` (`chat`, `embedding`, ...), and on the token usage `gen_ai.token.type` (`input` or `output`).

### Scrape the Prometheus Endpoint

The metrics endpoint keeps no history, so for charts and alerts you need a system that collects the values over time. Prometheus does that by calling the `prometheus` actuator endpoint of your application at a fixed interval.

```bash
curl -s http://localhost:8080/actuator/prometheus | grep gen_ai
```

The names look different from the ones on the metrics endpoint. The Prometheus format allows no dots, so `gen_ai.client.token.usage` becomes `gen_ai_client_token_usage`, and the tag names change in the same way.

In the next part you go further and push everything into a Grafana stack over OpenTelemetry, so you get dashboards, traces, and search instead of spot checks.

## 5. Run the Grafana Stack with Docker Compose

The actuator endpoints are good for spot checks. For a real dashboard you push everything over OTLP into a stack that stores and visualizes it. Here that stack runs locally next to your application.

The `grafana/otel-lgtm` image bundles Grafana, Prometheus for metrics, Loki for logs, Tempo for traces, and an OpenTelemetry collector into a single container. You run it with Docker Compose, and you let Spring Boot start and stop it together with the application.

Only traces and metrics are forwarded in this lab. Sending your logs over OTLP as well needs an extra OpenTelemetry logging appender and some Logback configuration in the application, which is out of scope here.

Create `compose.yaml` with the following content. The file goes into the root folder of the project, next to `pom.xml`.

```yaml
services:
  otel-lgtm:
    image: grafana/otel-lgtm:latest
    container_name: support-assistant-otel-lgtm
    ports:
      - "3000:3000" # Grafana UI
      - "4317:4317" # OTLP gRPC
      - "4318:4318" # OTLP HTTP
    volumes:
      - ./custom-grafana-dashboard.json:/otel-lgtm/grafana/conf/provisioning/dashboards/custom/custom-dashboard.json:ro
    environment:
      GF_DASHBOARDS_DEFAULT_HOME_DASHBOARD_PATH: /otel-lgtm/grafana/conf/provisioning/dashboards/custom/custom-dashboard.json
```

The `volumes` and `environment` entries bring in a ready made dashboard. The file `custom-grafana-dashboard.json` is already part of the project, and the volume mount puts it where Grafana looks for provisioned dashboards. The `GF_DASHBOARDS_DEFAULT_HOME_DASHBOARD_PATH` variable makes it the home dashboard.

You do not start the stack by hand. The `spring-boot-docker-compose` module detects the `compose.yaml` at startup, runs `docker compose up`, and stops the containers again when the application shuts down. It also recognizes the `grafana/otel-lgtm` image and **fills in the OTLP endpoints for you**, which you rely on further down.

Add the following dependency to `pom.xml`, right after the `spring-boot-devtools` dependency.

```xml
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-docker-compose</artifactId>
			<scope>runtime</scope>
			<optional>true</optional>
		</dependency>
```

## 6. Add and Configure the OpenTelemetry Starter

Spring Boot **4.0** is the first release with an official `spring-boot-starter-opentelemetry` starter, and Spring Boot **4.1** builds on it, for example with exemplar support that links traces to metrics.

The starter pulls in the OTLP metrics registry, which takes the place of `micrometer-registry-prometheus`, so you swap one for the other. Replace the `micrometer-registry-prometheus` dependency in `pom.xml` with the following.

```xml
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-opentelemetry</artifactId>
		</dependency>
```

You do not have to set an OTLP URL, because `spring-boot-docker-compose` hands the endpoints of the container to the starter. All that is left is some configuration for the export itself. Append the following lines to `src/main/resources/application.properties`.

```properties

management.tracing.sampling.probability=1.0
management.otlp.metrics.export.step=5s
management.metrics.tags.application=${spring.application.name}
```

Every request now ends up in a trace, metrics are pushed every 5 seconds instead of every minute so your numbers show up in Grafana almost right away, and each metric carries an `application` tag that you can filter and group by. In production you lower the sampling rate and export less often to keep the data volume down.

## 7. Start the App and Explore in Grafana

DevTools does not pick up new dependencies or the new `compose.yaml`, so stop the application in Terminal 1 with `Ctrl+C` and start it again.

```bash
./mvnw spring-boot:run
```

Spring Boot now starts the otel-lgtm container before the application, so this run takes longer, especially the first time, while Docker pulls the image. Wait for `Started SupportAssistantApplication`.

Generate some traffic in Terminal 2. First a RAG query.

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Does VMware Tanzu Spring provide commercial support for Micrometer?"
```

Then a tool calling query.

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Open a high priority ticket to request a trial for VMware Tanzu Spring"
```

Open Grafana at [http://localhost:3000](http://localhost:3000). If Grafana asks you to sign in, use `admin` as user name and as password.

You land on the provisioned **Spring AI Observability** dashboard. It visualizes the core metrics that Spring AI records for you, which are the token usage, the latency and errors of the model calls, and the time spent in the advisors and in your tools. The last row lists the matching traces from Tempo. Give it a few seconds after your requests, because the metrics arrive in batches every 5 seconds.

The tool call table shows the arguments and the result. They are only there because you turned on the observation content flags earlier.

Now follow one request from start to end. In the **Chat API traces** table, click one of the requests. Grafana opens the trace view with the full span tree. You see the HTTP request at the top, and below it the `ChatClient` span with the advisors nested inside, the vector store query that the RAG advisor triggers, the first model call, the tool call that the model asked for, and the second model call that produces the final answer. The width of each bar tells you where the time went, which is usually the model calls.

Click a single span to see its attributes on the right. The model spans carry `gen_ai.request.model`, the token counts, and the finish reason, and the tool span carries the tool name with the arguments and the result. This is the same data as in the tables, now in the context of one request.

Compare a trace of the RAG query with a trace of the tool calling query. The tool calling one has the extra tool span and the second model call, which is the round trip you saw in the logs earlier.

Besides the dashboard, you can query the data yourself under **Explore**. The token usage is usually the first thing teams want, so pick the Prometheus datasource and paste in the query behind the token rate panel. Explore opens in the **Builder** view, where you click a query together from dropdowns. Switch to the **Code** view with the toggle on the right of the query row before you paste the query.

```promql
sum by (gen_ai_token_type, gen_ai_request_model) (
  rate(gen_ai_client_token_usage_total[1m])
)
```

In plain words this asks how many tokens per second you used across the last minute, split by whether they were input or output and by the model that served them.

## 8. Make Observability Opt-In with a Profile

You do not want the heavy otel-lgtm container and the content logging on every run. Move all of it behind a profile, so the default run stays lightweight and you switch the full stack on only when you want it.

First, put the otel-lgtm service behind an `otel` Docker Compose profile, so Compose does not start it unless that profile is active. Append the following lines to `compose.yaml`.

```yaml
    profiles:
      - otel
```

Now collect every opt-in setting in a profile specific properties file. Spring Boot loads it only when the `local-observability` profile is active, and its values override `application.properties`. The last line activates the `otel` Compose profile, so turning on the Spring profile also starts the otel-lgtm container.

Create `src/main/resources/application-local-observability.properties` with the following content.

```properties
# Not recommended for production - high data volume and risk of exposing sensitive content
spring.ai.chat.client.observations.log-prompt=true
spring.ai.chat.client.observations.log-completion=true
spring.ai.chat.observations.log-prompt=true
spring.ai.chat.observations.log-completion=true
spring.ai.chat.observations.include-error-logging=true
spring.ai.tools.observations.include-content=true
spring.ai.vectorstore.observations.log-query-response=true

management.otlp.metrics.export.enabled=true
management.otlp.metrics.export.step=5s
management.metrics.tags.application=${spring.application.name}

management.otlp.tracing.export.enabled=true
management.tracing.sampling.probability=1.0

spring.docker.compose.enabled=true
spring.docker.compose.profiles.active=otel
```

Finally, take the same settings out of `application.properties` again and turn the exporters and Docker Compose off by default, so nothing is exported and nothing sensitive is logged unless you ask for it. Replace the content of `src/main/resources/application.properties` with the following.

```properties
spring.application.name=support-assistant
spring.devtools.restart.enabled=true

spring.mvc.apiversion.use.path-segment=1
spring.mvc.apiversion.supported=1.0
spring.mvc.apiversion.default=1.0

spring.ai.openai.api-key=${OPENAI_API_KEY}
spring.ai.openai.chat.model=gpt-5.6-sol
spring.ai.openai.chat.reasoning-effort=none
spring.ai.openai.chat.temperature=0.7
spring.ai.openai.embedding.model=text-embedding-3-small

spring.datasource.url=jdbc:h2:mem:supportdb;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE
spring.datasource.driver-class-name=org.h2.Driver

logging.level.org.springframework.ai=debug

management.endpoints.web.exposure.include=health,metrics,prometheus

# The observability stack is opt-in, enable it with the local-observability profile
management.otlp.metrics.export.enabled=false
management.otlp.tracing.export.enabled=false
management.otlp.logging.export.enabled=false
spring.docker.compose.enabled=false
```

Stop the application in Terminal 1 and start it again. The default run is now lightweight, with no exporters and no otel-lgtm container.

```bash
./mvnw spring-boot:run
```

When you want the full stack again, start the application with the `local-observability` profile. The profile turns the exporters back on and, through `spring.docker.compose.profiles.active=otel`, starts the otel-lgtm container again.

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local-observability
```

## Recap

The assistant now writes structured logs, Micrometer metrics including the token usage for cost, and OpenTelemetry traces that you can explore in Grafana, all through the same observability stack you use for the rest of Spring Boot. The [`sample-app/`](../../04-agentic-ai/02-mcp/sample-app/) of the MCP lab contains the result of this lab. Next you connect the assistant to external tools with MCP.
