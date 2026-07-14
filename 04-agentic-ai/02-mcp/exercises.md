# MCP — Hands-on Exercises

Your assistant already uses tool calling, but the `SupportTicketService` tools run **in the same process** as the assistant. The **Model Context Protocol (MCP)** is a standard way for one process to expose tools, resources, and prompts so an AI application in another process can use them.

In this lab you build a small, separate **Spring Releases MCP server** that fetches live release data from `api.spring.io`, then connect the support assistant (the [`sample-app/`](sample-app/)) to it as an MCP **client**. A question like *"What's the latest release of Spring Boot?"* is then answered from live data.

## Part 1 — Build the MCP server

The MCP server is a second, separate Spring Boot application. Scaffold it with Spring Initializr:

```bash
curl https://start.spring.io/starter.zip \
  -d dependencies=web,spring-ai-mcp-server \
  -d type=maven-project \
  -d groupId=com.example \
  -d artifactId=spring-releases-mcp-server \
  -d name=spring-releases \
  -d packageName=com.example.spring_releases \
  -d javaVersion=21 \
  -o spring-releases-mcp-server.zip && \
  unzip spring-releases-mcp-server.zip -d spring-releases-mcp-server
```

Because you also selected `web`, the `spring-ai-mcp-server` id resolves to the WebMVC starter `spring-ai-starter-mcp-server-webmvc`, which provides the HTTP Streamable transport. Unlike the assistant, this server needs no model-provider starter — it only *exposes* tools and never calls an LLM.

### Configure the server

Replace `src/main/resources/application.properties`:

```properties
spring.application.name=spring-releases

spring.ai.mcp.server.name=${spring.application.name}
spring.ai.mcp.server.protocol=STREAMABLE
spring.ai.mcp.server.version=1.0.0
server.port=8090

logging.level.io.modelcontextprotocol.server=DEBUG
```

The server will be reachable at `http://localhost:8090/mcp`.

### The domain record

Create `src/main/java/com/example/spring_releases/SpringRelease.java`:

```java
package com.example.spring_releases;

record SpringRelease(String version, String status, boolean current) {
}
```

### The MCP tool service

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

`@McpTool` and `@McpToolParam` (from `org.springframework.ai.mcp.annotation`) are the MCP-specific annotations. The server finds every bean with `@McpTool` methods and registers them with the protocol.

### Run it

```bash
cd spring-releases-mcp-server
./mvnw spring-boot:run
```

You should see the embedded MCP server start on port 8090 and log one registered tool.

### Test the server directly (optional)

The Streamable HTTP transport speaks JSON-RPC over HTTP. The first request must be `initialize`, which returns a session id in the `Mcp-Session-Id` header; reuse it on follow-up calls:

```bash
SESSION_ID=$(curl -sS -D - -o /dev/null -X POST http://localhost:8090/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"curl","version":"1"}}}' \
  | grep -i '^mcp-session-id:' | awk '{print $2}' | tr -d '\r')

curl -sS -X POST http://localhost:8090/mcp \
  -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" \
  -H "Mcp-Session-Id: $SESSION_ID" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"fetchReleasesInfo","arguments":{"projectSlug":"spring-boot"}}}'
```

You get back the current Spring Boot releases from `api.spring.io`, wrapped in MCP's content envelope.

## Part 2 — Connect the assistant as an MCP client

In the support-assistant [`sample-app/`](sample-app/), add the MCP client starter to `pom.xml` (after the `spring-ai-markdown-document-reader` dependency):

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-client</artifactId>
</dependency>
```

For every named connection in your configuration, the starter creates one MCP client and exposes a single `ToolCallbackProvider` bean that gathers every remote tool. Configure the connection in `application.properties`:

```properties
spring.ai.mcp.client.streamable-http.connections.spring-releases.url=http://localhost:8090

# Verbose protocol logging while you learn. Turn these off in production.
logging.level.io.modelcontextprotocol.client=DEBUG
logging.level.io.modelcontextprotocol.spec=DEBUG
```

Register the remote tools as **default tools** on the `ChatClient` bean, so every call sees them. Update the `chatClient` factory method in `SupportAssistantConfiguration` — add a `ToolCallbackProvider tools` parameter and `.defaultTools(tools)`:

```java
@Bean
public ChatClient chatClient(ChatClient.Builder builder,
                             @Value("classpath:/prompts/system-prompt.st") Resource systemPrompt,
                             ChatMemory chatMemory,
                             ToolCallbackProvider tools) {
    return builder
            .defaultSystem(systemPrompt)
            .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
            .defaultAdvisors(
                    new SimpleLoggerAdvisor(Ordered.LOWEST_PRECEDENCE),
                    MessageChatMemoryAdvisor.builder(chatMemory).build())
            .defaultTools(tools)
            .build();
}
```

Add the import:

```java
import org.springframework.ai.tool.ToolCallbackProvider;
```

The in-process ticket tools are still attached per call with `.tools(supportTicketService)` in `generateResponse`. Default tools and per-call tools are combined, so the model sees both.

### Test it

Start the assistant (keep the MCP server running in its own terminal), then:

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=What is the latest stable release of Spring Boot?"
```

The logs show the model making a `fetchReleasesInfo` tool call with `{"projectSlug": "spring-boot"}`, and the result fed back for the final answer.

Try one turn that uses both the remote MCP tool and an in-process tool:

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=What is the latest release of Spring Boot? Please also open a high-priority ticket to request help upgrading our application to that version."
```

The model calls `fetchReleasesInfo` (remote, MCP) and `createTicket` (in-process) in one turn.

## Recap

Your assistant now reaches beyond its own process, consuming tools from a separate MCP server alongside its in-process ones. Next you scale this to many tools with agentic patterns.
