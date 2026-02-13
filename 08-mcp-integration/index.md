---
title: MCP Integration
---

In this section, you'll learn about the Model Context Protocol (MCP) and expose the tools you built in the previous section as an MCP server.

## Learning Objectives

- Understand what MCP is and why it matters
- Configure your Spring AI application as an MCP server
- Expose existing tools via the MCP protocol
- Test the MCP server with a client

## What is MCP?

**Model Context Protocol (MCP)** is an open protocol initially developed by Anthropic that standardizes how AI applications connect to external data sources and tools. It provides:

- A **standardized interface** for tool execution and context retrieval
- **Transport-agnostic communication** (STDIO, SSE, Streamable-HTTP)
- **Interoperability** between different AI applications (hosts) and tool providers (servers)

Think of MCP as a universal adapter: any MCP-compatible host can connect to any MCP server without custom integration code.

## MCP Architecture

MCP follows a client-server architecture where an **MCP Host** (an AI application like ChatGPT, Copilot or Claude Code) establishes connections to one or more MCP servers. The host creates one **MCP Client** for each server, and each client maintains a dedicated connection with its corresponding server.

```
┌─────────────────────────────────────────────┐
│                  MCP Host                   │
│         (Claude Desktop, Cursor)            │
│  ┌─────────────┐         ┌─────────────┐    │
│  │ MCP Client 1│         │ MCP Client 2│    │
│  └──────┬──────┘         └──────┬──────┘    │
└─────────┼───────────────────────┼───────────┘
          │ MCP Protocol          │ MCP Protocol
          ▼                       ▼
┌─────────────────┐     ┌─────────────────┐
│   MCP Server    │     │   MCP Server    │
│ (Your Spring    │     │  (Other tools)  │
│  AI App)        │     │                 │
└─────────────────┘     └─────────────────┘
          │
          ▼
  ┌───────────────┐
  │  Your Tools   │
  │ (DateTimeTool,│
  │  TicketTool)  │
  └───────────────┘
```

- **MCP Host**: The AI application that coordinates and manages one or multiple MCP clients
- **MCP Client**: A component within the host that maintains a connection to a single MCP server
- **MCP Server**: A program that provides context (tools, resources, prompts) to MCP clients

Local MCP servers using STDIO transport typically serve a single client, while remote servers using Streamable-HTTP (like the one you'll build) can serve many clients simultaneously.

## Why Use MCP?

Without MCP, each AI application needs custom integrations for every tool provider.

| Without MCP | With MCP |
|-------------|----------|
| Custom integration per client | Single MCP server serves all clients |
| Tight coupling | Loose coupling via protocol |
| Duplicate tool implementations | Reusable tool definitions |

## Add MCP Server Dependency

Spring AI provides MCP server support through starter dependencies. Add the MCP server starter for WebMVC-based Streamable-HTTP transport:

```editor:select-matching-text
file: ~/sample-app/pom.xml
text: "</dependencies>"
description: Add MCP server dependencies
before: 0
after: 0
cascade: true
```
```editor:replace-text-selection
file: ~/sample-app/pom.xml
hidden: true
text: |2
          <dependency>
              <groupId>org.springframework.ai</groupId>
              <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
          </dependency>
      </dependencies>
```

The Spring AI MCP Server Boot Starters provide auto-configuration for setting up MCP Servers in Spring Boot applications. Key features include:

- **Automatic component configuration** - Tools, resources, and prompts are auto-discovered
- **Multiple transport options** - STDIO (Standard input/output, client launches MCP server as subprocess), SSE (Server-sent events protocol, deprecated), and Streamable-HTTP
- **Sync and async modes** - Both synchronous and asynchronous operation
- **Flexible tool registration** - Register existing `@Tool` methods via `ToolCallbackProvider` or use MCP-specific annotations

## Configure MCP Server

Add MCP server configuration to your application properties:

```editor:select-matching-text
file: ~/sample-app/src/main/resources/application.yaml
text: "spring:"
description: Add MCP server configuration
before: 0
after: 0
cascade: true
```
```editor:replace-text-selection
file: ~/sample-app/src/main/resources/application.yaml
hidden: true
text: |
  spring:
    ai:
      mcp.server:
        name: ${spring.application.name}
        version: 1.0.0
        protocol: STREAMABLE
```

This configures:
- `name` and `version` - Server metadata that clients see when connecting
- `protocol: STREAMABLE` - Enables the Streamable-HTTP transport

## Expose Tools via MCP

To expose your existing `@Tool` annotated methods via MCP, you need to register them with the MCP server using a `ToolCallbackProvider` bean.

Add the following configuration to register your tools with the MCP server:

```editor:select-matching-text
file: ~/sample-app/src/main/java/com/example/supportassistant/SupportAssistantConfiguration.java
text: "import org.springframework.ai.vectorstore.VectorStore;"
description: Add ToolCallbackProvider import
before: 0
after: 0
cascade: true
```
```editor:replace-text-selection
file: ~/sample-app/src/main/java/com/example/supportassistant/SupportAssistantConfiguration.java
hidden: true
text: |
  import org.springframework.ai.vectorstore.VectorStore;
  import org.springframework.ai.tool.ToolCallbackProvider;
  import org.springframework.ai.tool.method.MethodToolCallbackProvider;
  import com.example.supportassistant.tools.DateTimeTool;
  import com.example.supportassistant.tools.TicketTool;
```

```editor:select-matching-text
file: ~/sample-app/src/main/java/com/example/supportassistant/SupportAssistantConfiguration.java
text: "return SimpleVectorStore.builder(embeddingModel).build();"
description: Add ToolCallbackProvider bean for MCP
before: 0
after: 0
cascade: true
```
```editor:replace-text-selection
file: ~/sample-app/src/main/java/com/example/supportassistant/SupportAssistantConfiguration.java
hidden: true
text: |2
          return SimpleVectorStore.builder(embeddingModel).build();
      }

      @Bean
      public ToolCallbackProvider mcpToolProvider(DateTimeTool dateTimeTool, TicketTool ticketTool) {
          return MethodToolCallbackProvider.builder().toolObjects(dateTimeTool, ticketTool).build();
```

The `ToolCallbackProvider.from()` method scans the provided beans for `@Tool` annotations and registers them with the MCP server. This means your existing tools work for both:
- **Direct ChatClient integration** - Using `.tools(dateTimeTool, ticketTool)` in your controllers
- **MCP server exposure** - Automatically discovered via the `ToolCallbackProvider` bean

**Alternative: MCP-Specific Annotations**

Spring AI also provides dedicated MCP annotations (`@McpTool`, `@McpResource`, `@McpPrompt`) that enable automatic bean scanning without the need for a `ToolCallbackProvider` bean. These annotations are useful when you want to create tools exclusively for MCP exposure.

## Start the MCP Server

Start the application:

```terminal:execute
command: cd ~/sample-app && ./mvnw spring-boot:run
session: 2
```

The MCP server is now running and exposes the Streamable-HTTP endpoint at:
- `http://localhost:8080/mcp` - Main MCP endpoint for all communication

## Test the MCP Server

You can test the MCP server by checking its capabilities. The MCP protocol uses JSON-RPC over HTTP:

Initialize a new session:
```execute
SESSION_ID=$(curl -v -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream, application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test-client","version":"1.0.0"}}}' \
  2>&1 | grep -i "Mcp-Session-Id:" | awk '{print $3}' | tr -d '\r')

echo "Session ID: $SESSION_ID"
```

List available tools:

```execute
curl -X POST http://localhost:8080/mcp \
  -H "mcp-session-id: $SESSION_ID" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream, application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'
```

You should see your `getCurrentDateTime`, `createTicket`, and `listOpenTickets` tools listed.

Call the date/time tool:

```execute
curl -X POST http://localhost:8080/mcp \
  -H "mcp-session-id: $SESSION_ID" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream, application/json" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"getCurrentDateTime","arguments":{"timezone":"Europe/London"}}}'
```

## MCP-Compatible Hosts

Your MCP server can now be used by any MCP-compatible host. Popular hosts include:

- **Claude Desktop** - Anthropic's desktop application
- **Cursor IDE** - AI-powered code editor
- **Open WebUI** - Open-source web interface for AI models
- **Custom applications** - Build your own MCP host using Spring AI's MCP client support

## MCP vs Direct Tool Integration

| Aspect | Direct Tools (`.tools()`) | MCP Server |
|--------|---------------------------|------------|
| **Use Case** | Single application | Multiple hosts |
| **Transport** | In-process | Streamable-HTTP, SSE, or STDIO |
| **Hosts** | Your app only | Any MCP host |
| **Deployment** | Embedded | Standalone service |

Use direct tools when tools are specific to one application. Use MCP when you want to share tools across multiple AI hosts.

## Stop the Application

```terminal:interrupt
session: 2
```

## Summary

You've transformed your Spring AI application into an MCP server:

**Key Concepts:**
- MCP standardizes how AI applications (MCP hosts) connect to tools via MCP clients
- `ToolCallbackProvider` registers existing `@Tool` methods with the MCP server
- The WebMVC starter provides Streamable-HTTP transport
- Any MCP host can now connect to your server and use your tools
- MCP-specific annotations (`@McpTool`, `@McpResource`, `@McpPrompt`) are available as an alternative

**Learning More:**
- [MCP Specification](https://modelcontextprotocol.io/)
- [Spring AI MCP Documentation](https://docs.spring.io/spring-ai/reference/api/mcp.html)
