# Spring AI Support Assistant — MCP Walkthrough

Continues from `INSTRUCTIONS-5.md`. Your Support Assistant now has chat, structured output, RAG, tool calling, tests, and observability — everything it needs to talk to its own users.

In this part you'll connect it to a **second** Spring Boot service via the Model Context Protocol (MCP). MCP is the wire protocol for exposing tools, resources, and prompts from one process so an AI app in another process can consume them. Concretely you'll build a tiny **Spring Releases MCP server** that fetches live release data from `api.spring.io`, then wire the Support Assistant up as an MCP **client** so questions like *"What's the latest release of Spring Boot?"* are answered from live data instead of the model's stale training corpus.

## 1. Build the MCP server

This is a new Spring Boot application. Step out of the `support-assistant` directory.

### 1a. Scaffold via Spring Initializr

```bash
curl https://start.spring.io/starter.zip \
  -d dependencies=spring-ai-mcp-server \
  -d bootVersion=4.0.6 \
  -d type=maven-project \
  -d groupId=com.example \
  -d artifactId=spring-releases-mcp-server \
  -d javaVersion=25 \
  -o spring-releases-mcp-server.zip

unzip spring-releases-mcp-server.zip -d spring-releases-mcp-server
cd spring-releases-mcp-server
```

The Initializr id `spring-ai-mcp-server` resolves to the WebMVC starter, which gives you the HTTP/Streamable transport. The Maven artifact in `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```

### 1b. Configure the server

Replace `src/main/resources/application.properties`:

```properties
spring.application.name=spring-releases

spring.ai.mcp.server.name=${spring.application.name}
spring.ai.mcp.server.protocol=STREAMABLE
spring.ai.mcp.server.version=1.0.0
server.port=8081

logging.level.io.modelcontextprotocol.server=DEBUG
```

The server will be reachable at `http://localhost:8081/mcp`. `STREAMABLE` matches what the Support Assistant client is already configured to call (`spring.ai.mcp.client.streamable-http.connections.spring-releases.url=http://localhost:8081`). The server name `spring-releases` is what clients will see when they introspect the connection.

### 1c. The domain record

Create `src/main/java/com/example/spring_releases/SpringRelease.java`:

```java
package com.example.spring_releases;

record SpringRelease(String version, String status, boolean current) {
}
```

A direct projection of what `api.spring.io` returns for a single release.

### 1d. The MCP tool service

Create `src/main/java/com/example/spring_releases/SpringReleasesInfoService.java`:

```java
package com.example.spring_releases;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
class SpringReleasesInfoService {

    private static final Logger log = LoggerFactory.getLogger(SpringReleasesInfoService.class);

    private final RestClient client = RestClient.create("https://api.spring.io");

    @McpTool(description = "Get all releases for a Spring project, including version and support status.")
    List<SpringRelease> fetchReleasesInfo(
            @McpToolParam(description = "The project slug, e.g. 'spring-boot', 'spring-framework', 'spring-ai'") String projectSlug) {
        log.info("Fetch spring release info for project {} called", projectSlug);

        return client.get()
                .uri("/projects/{slug}/releases", projectSlug)
                .retrieve()
                .body(ReleasesResponse.class)
                .embedded()
                .releases();
    }

    private record ReleasesResponse(@JsonProperty("_embedded") Embedded embedded) {
        record Embedded(List<SpringRelease> releases) {
        }
    }
}
```

Three things worth flagging:

- **`@McpTool` / `@McpToolParam`**, from `org.springframework.ai.mcp.annotation`. These are the **MCP-specific** annotations, not the in-process `@Tool` / `@ToolParam` you used in INSTRUCTIONS-3. The MCP server auto-discovers any bean with `@McpTool` methods and registers them with the protocol — no `ToolCallbackProvider` bean needed.
- **`RestClient`** — pulls live data from Spring's public release API. A real production tool would add error handling, caching, and retry; this version stays minimal so the protocol is what stands out.
- **The inner `ReleasesResponse` / `Embedded` records** model HAL's `_embedded` envelope. Spring AI doesn't need them — they're just Jackson plumbing for the API response.

### 1e. Run it

```bash
./mvnw spring-boot:run
```

You should see the embedded MCP server start on port 8081 and log one registered tool at startup.

## 2. Add the MCP client dependency to the assistant

Back in the `support-assistant` project. Add to `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-client</artifactId>
</dependency>
```

The starter auto-configures one MCP client per named connection it finds in properties and exposes a single `ToolCallbackProvider` bean that aggregates every remote tool.

## 3. Configure the MCP client

Append to `src/main/resources/application.properties`:

```properties
spring.ai.mcp.client.streamable-http.connections.spring-releases.url=http://localhost:8081

# Verbose protocol logging while you're learning — drop these in production
logging.level.io.modelcontextprotocol.client=DEBUG
logging.level.io.modelcontextprotocol.spec=DEBUG
```

The `spring-releases` segment is the connection name. It shows up as a prefix on the imported tools (e.g. `spring-releases_fetchReleasesInfo`) so the model can tell remote tools apart from each other and from in-process ones.

### Wire the remote tools into the `ChatClient`

Instead of threading the callbacks into every `chatClient.prompt()` call site, register them once as **default tools** on the `ChatClient` bean. Open `SupportAssistantConfiguration.java`:

```java
package com.example.support_assistant;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class SupportAssistantConfiguration {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder, ToolCallbackProvider tools) {
        return builder
                .defaultSystem("You are a Spring and AI expert.")
                .defaultTools(tools)
                .build();
    }

    @ConditionalOnMissingBean(VectorStore.class)
    @Bean
    VectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
```

Two changes:

- **Inject `ToolCallbackProvider tools`** — Spring AI's MCP client auto-configuration provides this bean automatically; it wraps every connection from your properties.
- **`.defaultTools(tools)`** — every call through this `ChatClient` now sees the MCP tools without further wiring. `SupportAssistantService` doesn't change.

The in-process ticket tools are still attached per-call via `.tools(supportTicketService)` in `generateResponse`. Defaults and per-call tools merge — the model sees both groups.

## 4. Test it

### 4a. Talk to the MCP server directly

The Streamable HTTP transport speaks JSON-RPC over HTTP. The first request must be `initialize`, which returns the session id in the `Mcp-Session-Id` response header; reuse that header on follow-up calls.

```bash
SESSION_ID=$(curl -sS -D - -o /dev/null -X POST http://localhost:8081/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{
        "jsonrpc": "2.0",
        "id": 1,
        "method": "initialize",
        "params": {
          "protocolVersion": "2025-06-18",
          "capabilities": {},
          "clientInfo": { "name": "curl", "version": "1" }
        }
      }' | grep -i '^mcp-session-id:' | awk '{print $2}' | tr -d '\r')

echo "Session: $SESSION_ID"
```

List the tools the server advertises:

```bash
curl -sS -X POST http://localhost:8081/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -H "Mcp-Session-Id: $SESSION_ID" \
  -d '{
        "jsonrpc": "2.0",
        "id": 2,
        "method": "tools/list",
        "params": {}
      }'
```

You should see one entry — `fetchReleasesInfo`, its description, and the JSON schema for the `projectSlug` parameter.

Call the tool directly:

```bash
curl -sS -X POST http://localhost:8081/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -H "Mcp-Session-Id: $SESSION_ID" \
  -d '{
        "jsonrpc": "2.0",
        "id": 3,
        "method": "tools/call",
        "params": {
          "name": "fetchReleasesInfo",
          "arguments": { "projectSlug": "spring-boot" }
        }
      }'
```

You'll get back the current Spring Boot releases array from `api.spring.io`, wrapped in MCP's content envelope. For exploration, the MCP Inspector (`npx @modelcontextprotocol/inspector`) handles session management and pretty-prints — much nicer than raw curl.

### 4b. Talk to it via the assistant

```bash
curl -G "http://localhost:8080/api/1.0/chat" \
     --data-urlencode "query=What is the latest stable release of Spring Boot?"
```

In the assistant's logs you'll see:
- The MCP client connecting to `http://localhost:8081/mcp` on startup.
- The model emitting a `spring-releases_fetchReleasesInfo` tool call with `{"projectSlug": "spring-boot"}`.
- The result fed back into the model for the final answer.

A query that exercises both MCP and in-process tools at once:

```bash
curl -G "http://localhost:8080/api/1.0/chat" \
     --data-urlencode "query=Check the latest Spring AI release, and if we're behind the current GA, please open a high-priority ticket about upgrading."
```

The model calls `fetchReleasesInfo` (MCP, remote) and `createTicket` (in-process tool) in a single conversational turn.
---

## Recap

| Step | What changed | Key API |
|------|--------------|---------|
| 1a | New project scaffolded for the server | `start.spring.io`, `spring-ai-mcp-server` |
| 1b | Server transport + port + name | `spring.ai.mcp.server.protocol=STREAMABLE`, `server.port=8081` |
| 1c | Domain record | `SpringRelease` |
| 1d | Live release lookup as an MCP tool | `@McpTool`, `@McpToolParam`, `RestClient` |
| 2 | MCP client starter on the assistant | `spring-ai-starter-mcp-client` |
| 3 | Connection property + default tools on `ChatClient` | `spring.ai.mcp.client.streamable-http.connections.<name>.url`, `.defaultTools(ToolCallbackProvider)` |
| 4 | Verify directly (JSON-RPC) and via the assistant | `initialize` + `Mcp-Session-Id`, then `tools/list` / `tools/call` |